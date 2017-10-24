package bitnymWallet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.ReceiveListener;
import edu.kit.tm.ptp.SendListener;


//TODO check which broadcastannouncement read and accepted for mixing, and check whether we want to mix this nym or not

//TODO refactor into one branch, mix outputs, send back for signing, and sign ourselves, less code => less bugs

public class Mixer {

	private BitNymWallet wallet;
	private BroadcastAnnouncement bca;
	private Identifier mixPartnerAdress;
	private ProofMessage ownProof, partnerProof;
	private Wallet w;
	private NetworkParameters params;
	private PeerGroup pg;
	private BlockChain bc;
	private List<MixFinishedEventListener> mfListeners;
	private List<MixAbortEventListener> maListeners;
	private List<MixPassiveEventListener> mpListeners;
	private int lockTime;
	private boolean successful;
	
	//get the onion mix adress from a broadcastannouncement
	public Mixer(BitNymWallet wallet, BroadcastAnnouncement bca, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.bca = bca;
		this.mixPartnerAdress = new Identifier(bca.getOnionAdress() + ".onion");
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixPassiveEventListener>();
		this.lockTime = 0; 
	}
	
	
	//get onionAdress directly
	public Mixer(BitNymWallet wallet, String onionAddress, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.mixPartnerAdress = new Identifier(onionAddress);
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixPassiveEventListener>();
		this.lockTime = 0;

	}
	
	//constructor for just listening
	public Mixer(BitNymWallet wallet, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixPassiveEventListener>();
		this.lockTime = 0;
	}
	
	public void passiveMix(byte[] arg0, Identifier arg1) {
		mixPartnerAdress = arg1;
		wallet.ptp.setSendListener(new SendListener() {

	        @Override
	        public void messageSent(long id, Identifier destination, State state) {
	          switch (state) {
	            case INVALID_DESTINATION:
	              System.out.println("Destination " + destination + " is invalid");
	              break;
	            case TIMEOUT:
	              System.out.println("Sending of message timed out");
	              break;
	            default:
	            	System.out.println("Send successful");
	            	successful = true;
	              break;
	          }
	        }
	      });
		//received serialized proof, so deserialize, and check proof
		System.out.println("check partner proof");
		this.partnerProof = (ProofMessage) deserialize(arg0);
		if(!partnerProof.isProbablyValid(params, bc, pg)) {
			System.out.println("proof of mix partner is invalid");
			mixAbort();
			return;
		}
		//send own proof to partner
		System.out.println("listen for first part of mix transaction");
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {

			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				//deserialize received tx, and add own input and output and
				//sign then and send back
				System.out.println("try to deserialize tx received from mixpartner");
				final Transaction rcvdTx = deserializeTransaction(arg0);
				if(!checkTxInputIsFromProof(rcvdTx, 0)) {
					System.out.println("checktxinput is from proof failed");
					mixAbort();
					return;
				}
				final int outputOrder = rcvdTx.getOutputs().size();
				System.out.println("deserialized tx received from mixpartner");
				
				ECKey newPsnymKey = new ECKey();
				w.importKey(newPsnymKey);
				long unixTime = System.currentTimeMillis() / 1000L;
				//TODO remove 10*60*150
				//TODO use bitcoin bip113 time
				final CLTVScriptPair outSp = new CLTVScriptPair(newPsnymKey, CLTVScriptPair.currentBitcoinBIP113Time(bc)+lockTime);
				Coin newPsyNymValue = computeValueOfNewPsyNyms(ownProof.getLastTransactionOutput().getValue(), partnerProof.getLastTransactionOutput().getValue(), Transaction.DEFAULT_TX_FEE);
				TransactionOutput newPsyNym = new TransactionOutput(params, rcvdTx, newPsyNymValue, outSp.getPubKeyScript().getProgram());
				rcvdTx.addOutput(newPsyNym);
				final CLTVScriptPair inSp = ownProof.getScriptPair();
				assert(inSp != null);
				rcvdTx.addInput(ownProof.getLastTransactionOutput());
				rcvdTx.getInput(1).setSequenceNumber(3);
				
				//check output value
				if(outputOrder == 0) {
					//add our output first
					System.out.println("partner didn't add first output, we'll add the first output and sign later");
					System.out.println("listen for complete mix transaction, we started");
					wallet.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							//deserialize tx, check rcvd Tx, then sign and broadcast
							
							Transaction lastTxVersion = deserializeTransaction(arg0);
							if(!checkTx(rcvdTx, lastTxVersion)) {
								System.out.println("checktx failed");
								//return;
							}
							lastTxVersion.getInput(1).setScriptSig(inSp.calculateSigScript(lastTxVersion, 1, w));
							lastTxVersion.getInput(1).verify(ownProof.getLastTransactionOutput());
							assert(lastTxVersion != null);
							broadcastMixTx(outputOrder, outSp,lastTxVersion, 1);							
						}
					});
					wallet.ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
					System.out.println("done");

				} else if(outputOrder == 1) {
					System.out.println("partner added first output to the tx");
					//partner added already his output			

					//sign input and send back for signing

					rcvdTx.getInput(1).setScriptSig(inSp.calculateSigScript(rcvdTx, 1, w));
					rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					
					//rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					System.out.println("listen for complete mix transaction. other started");
					wallet.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
									// TODO Auto-generated method stub
									if(!checkTx(rcvdTx, deserializeTransaction(arg0))) {
										System.out.println("checktx failed");
										//return;
									}
									commitRcvdFinalTx(outSp, arg0, 1, outputOrder);
									System.out.println("commited tx, create new hidden service");
									wallet.restartPTP();
						}

						
					});
					wallet.ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
					System.out.println("done");

				}

			}


		});
		this.wallet.ptp.sendMessage(serialize(ownProof), mixPartnerAdress);
		System.out.println("done");
	}
	
	private boolean checkTxInputIsFromProof(Transaction rcvdTx, int i) {
		//return rcvdTx.getInput(i).getConnectedOutput().equals(partnerProof.getLastTransactionOutput());
		return rcvdTx.getInput(i).getOutpoint().getHash().equals(partnerProof.getLastTransaction().getHash()) &&
				partnerProof.getLastTransactionOutput().getIndex() == rcvdTx.getInput(i).getOutpoint().getIndex();
	}

	
	public void initiateMix() {
		
		System.out.println("is ptp initialized? " + wallet.ptp.isInitialized());
				
		wallet.ptp.setSendListener(new SendListener() {

	        @Override
	        public void messageSent(long id, Identifier destination, State state) {
	        	System.out.println("call sendlistener of initiatemix");
	          switch (state) {
	            case INVALID_DESTINATION:
	              System.out.println("Destination " + destination + " is invalid");
	              break;
	            case TIMEOUT:
	              System.out.println("Sending of message timed out");
	              break;
	            default:
	            	System.out.println("Send successful");
	            	successful = true;
	              break;
	          }
	        }
	      });
		
		
		//handshake?
		
		//exchange proofs
		System.out.println("initiateMix");
		if(this.mixPartnerAdress == null) {
			System.out.println("No mix partner");
		}
		
		byte[] serializedProof = null;
		
		serializedProof = serialize(this.ownProof);
		
		System.out.println("mixpartneradress " + mixPartnerAdress.getTorAddress());
		//ping();
		System.out.println("listen for proof (first message of mix(?))");
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			//deserialize proof
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				partnerProof = (ProofMessage) deserialize(arg0);
				//check proof
				System.out.println("check partner proof");
				if(!partnerProof.isProbablyValid(params, bc, pg)) {
					System.out.println("proof of mix partner is invalid, abort");
					mixAbort();
					return;
				}
				challengeResponse();
				
			}
		});
		this.wallet.ptp.sendMessage(serializedProof, mixPartnerAdress);
		System.out.println("done");
//		try {
//			TimeUnit.SECONDS.sleep(30);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	private void ping() {
		byte[] data = {1};
		successful = false;
		for(int i=1; i<2 && successful == false; i++) {
			long timeout = i*180*1000;
			//timeout is in 
			this.wallet.ptp.sendMessage(data, this.mixPartnerAdress, timeout);
			System.out.println("done");
//			try {
//				TimeUnit.MILLISECONDS.sleep(timeout+i*3000);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
	}
	private void mixAbort() {
		for (MixAbortEventListener listener : maListeners) {
			listener.onMixAborted();
		}
	}

	private void mixPassiveStarted(byte[] arg0, Identifier arg1) {
		for (MixPassiveEventListener listener : mpListeners) {
			listener.onMixPassive(arg0,arg1);
		}
	}


	private void challengeResponse() {
		//challenge-response to proof ownership of key,? necessary? mix prooves ownership anyway ...
		//check with adress of transaction
		//this.ptp.sendMessage(publicKey, mixPartnerAdress);
		//this.ptp.setReceiveListener(new ReceiveListener() {
			
			//deserialize public key and check 
		//	@Override
		//	public void messageReceived(byte[] arg0, Identifier arg1) {
				
				
				mixAndConstructNewProof();
		//	}
		//});
	}

	private byte[] serialize(Object o) {
		byte[] serialized = null;
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
		         ObjectOutput out = new ObjectOutputStream(bos)) {
		        out.writeObject(o);
		        serialized = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return serialized;
	}
	
	private Object deserialize(byte[] serialized) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
			         ObjectInput in = new ObjectInputStream(bis)) {
			        return in.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
	public void listenForMix() {
		System.out.println("listenformix");
		System.out.println("listen for mix to start passive mixing with");
		assert(this.wallet != null);
		assert(this.wallet.ptp != null);
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				System.out.println("Mix request received, mixing passive");
				mixPassiveStarted(arg0, arg1);
			}
		});

	}
	
	public void closeListeningForMix() {
		System.out.println("listen for nothing, ignoring messages (when closing listening for mix)");
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				
			}
		});
	}
	
	//check that partner used the proper transactioninput announced in corresponding proof
	//right output coin value etc.
	private boolean checkTx(Transaction mixTx, Transaction rcvdTxToCheck) {
		if(rcvdTxToCheck.getInputs().size() > 2 || rcvdTxToCheck.getOutputs().size() > 2) {
			System.out.println("checktx failed, num of txinputs is " + rcvdTxToCheck.getInputs().size() +
					" num of outputs is " + rcvdTxToCheck.getOutputs().size());
			return false;			
		}
		
		if(rcvdTxToCheck.getOutputs().size() == 2) {
			if(!rcvdTxToCheck.getOutput(0).getValue().equals(rcvdTxToCheck.getOutput(1).getValue())) {
				System.out.println("value of outputs is different, checktx fails ");
				return false;
			}
		}
		
		for(int i=0; i < mixTx.getInputs().size(); i++) {
			if(!mixTx.getInput(i).getOutpoint().equals(rcvdTxToCheck.getInput(i).getOutpoint())) {
				System.out.println("the outpoints do not match, checktx fails");
				return false;
			}
		}
		for(int i=0; i < mixTx.getOutputs().size(); i++) {
			if(!mixTx.getOutput(i).equals(rcvdTxToCheck.getOutput(i))) {
				System.out.println("outputs are sth else, checktx fails");
				System.out.println("mixTx ----------");
				System.out.println(mixTx.toString());
				System.out.println("rcvdtxtocheck--------");
				System.out.println(rcvdTxToCheck.toString());
				return false;
			}
		}		
		
		return true;
	}
	
	private void mixAndConstructNewProof() {
		//mix
		System.out.println("try to mix and construct new proof");
		final Transaction mixTx = new Transaction(params);
		long currentUnixTime = System.currentTimeMillis() / 1000L;
		System.out.println("set locktime of tx to " + String.valueOf(currentUnixTime-(10*60*150)));
		mixTx.setLockTime(CLTVScriptPair.currentBitcoinBIP113Time(bc)-1);
		mixTx.addInput(this.ownProof.getLastTransactionOutput());
		//just needs to be anything else than uint_max, so that nlocktime is really used
		mixTx.getInput(0).setSequenceNumber(3);
		// draw random value for decision of output order
		// TODO use secure random bit
		Random r = new Random();
		final int outputOrder = r.nextInt(2);
		//outputOrder = 1;
		//set the value later on
		//TODO refactor duplicate code within mixAndConstructNewProof and passiveMix
		ECKey psnymKey = new ECKey();
		w.importKey(psnymKey);
		long unixTime = System.currentTimeMillis() / 1000L;
		//add wished lock time
		final CLTVScriptPair inSp = ownProof.getScriptPair();
		assert(inSp != null);
		//TODO add wished locktime
		//TODO remove (10*60*150)
		//TODO use bitcoin bip113 time
		final CLTVScriptPair outSp = new CLTVScriptPair(psnymKey, CLTVScriptPair.currentBitcoinBIP113Time(bc)+this.lockTime);
		Coin newPsyNymValue = computeValueOfNewPsyNyms(ownProof.getLastTransactionOutput().getValue(), partnerProof.getLastTransactionOutput().getValue(), Transaction.DEFAULT_TX_FEE);
		final TransactionOutput newPsyNym = new TransactionOutput(params, mixTx, newPsyNymValue, outSp.getPubKeyScript().getProgram());
		byte[] serializedTx;
		if(outputOrder == 0) {
			//add the own output and send the unfinished tx to the mix partner
			//the partner adds the input and output and signs the tx, we will then sign it
			//after checking correctness
			mixTx.addOutput(newPsyNym);
			serializedTx = mixTx.bitcoinSerialize();

			//check tx, sign it, then send it out to the bitcoin network
			System.out.println("listen for transaction to sign while mixing, we started");
			this.wallet.ptp.setReceiveListener(new ReceiveListener() {

				@Override
				public void messageReceived(byte[] arg0, Identifier arg1) {
					final Transaction rcvdTx = deserializeTransaction(arg0);
					if(!checkTxInputIsFromProof(rcvdTx, 1)) {
						System.out.println("tx input from received tx is not the same as in the proof");
						//return;
					}
					if(!checkTx(mixTx, rcvdTx)) {
						System.out.println("checktx failed");
					}
					//sign transaction and send it to the network

					rcvdTx.getInput(0).setScriptSig(inSp.calculateSigScript(rcvdTx, 0, w));
					rcvdTx.getInput(0).verify(ownProof.getLastTransactionOutput());

					//this method just does rudimentary checks, does not check whether inputs are already spent for example
					rcvdTx.verify();

					System.out.println(ownProof.getLastTransactionOutput());
					//TODO remove transaction if transaction is rejected, maybe just add to proof message only, and commit only when in the blockchain?
					broadcastMixTx(outputOrder, outSp, rcvdTx, 0);

				}


			});
			this.wallet.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
			System.out.println("done");
		} else {
			//let the mixpartner add his output first and partners input
			//we will add then our output and sign the tx, send it to the partner
			//and let the partner sign the tx
			serializedTx = mixTx.bitcoinSerialize();
			System.out.println("listen for transaction of partner to add ours to, he starts");
			this.wallet.ptp.setReceiveListener(new ReceiveListener() {

				@Override
				public void messageReceived(byte[] arg0, Identifier arg1) {
					// add our output, sign and send to partner then
					try {
						final Transaction penFinalTx = deserializeTransaction(arg0);
					if(!checkTxInputIsFromProof(penFinalTx, 1)) {
						System.out.println("checktxinputisfromproof failed");
					}
					if(!checkTx(mixTx, penFinalTx)) {
						System.out.println("checktx failed");
					}
					penFinalTx.addOutput(newPsyNym);
					penFinalTx.getInput(0).setScriptSig(inSp.calculateSigScript(penFinalTx, 0, w));
					penFinalTx.getInput(0).verify(ownProof.getLastTransactionOutput());
					System.out.println("listen for complete mix transaction");
					wallet.ptp.setReceiveListener(new ReceiveListener() {

						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
									// TODO Auto-generated method stub
									if(!checkTx(penFinalTx, deserializeTransaction(arg0))) {
										System.out.println("checktx failed");
									}
									commitRcvdFinalTx(outSp, arg0, 0, outputOrder);
									wallet.restartPTP();
						}
					});
					wallet.ptp.sendMessage(penFinalTx.bitcoinSerialize(), mixPartnerAdress);
					System.out.println("done");
					} catch (Exception e) {
						e.printStackTrace();
						mixAbort();
						return;
					}
				}
			});
			this.wallet.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
			System.out.println("done");
		}
	}
	
	
	//we need to precompute the fee as we only have the two psnyms as inputs (to not deanonymize mix partners, by linking change addresses for example
	//default_tx_fee in bitcoin is static, should be computed (by past txs for example) instead for probable rise in tx cost in the future
	//TODO use other formula for p2sh mixtx
	private Coin computeValueOfNewPsyNyms(Coin ownValue, Coin mixpartnerVal, Coin feePerKb) {
		assert(ownValue != null && mixpartnerVal != null && feePerKb != null);
		//statically computed, is ok as we know the size of the mixTx approximately
		//estimated by the following formula in*180 + out*34 + 10 + in, where in and out are the number of inputs and outputs
		double sizeInKBytes = ((double) (2*180+2*34 + 10 + 2))/1000.0;
		Coin fee = Coin.valueOf((int) (feePerKb.getValue() * sizeInKBytes));
		
		return ((ownValue.add(mixpartnerVal)).minus(fee)).div(2);
	}


	private Transaction deserializeTransaction(byte[] arg0) {
		Transaction rcvdTx = null;
		BitcoinSerializer bs = new BitcoinSerializer(params, false);
		try {
			rcvdTx = bs.makeTransaction(arg0);			
		} catch (ProtocolException e) {
			e.printStackTrace();
		}
		return rcvdTx;
	}
	
	
	//broadcast the mix tx, add to proof the new Transaction
	private void broadcastMixTx(final int outputOrder,
			final CLTVScriptPair outSp,
			final Transaction rcvdTx,
			int inputOrder) {
		System.out.println("call to broacastmixtx");
		rcvdTx.verify();
		w.commitTx(rcvdTx);
		System.out.println(rcvdTx);
		//ping();
		wallet.ptp.setSendListener(new SendListener() {
			
			@Override
			public void messageSent(long arg0, Identifier arg1, State arg2) {
				System.out.println("message sent is called, creating new hidden service");
				wallet.restartPTP();
				for(MixFinishedEventListener l : mfListeners) {
					assert(mfListeners.size() <= 1);
					l.onMixFinished();
				}				
			}
		});
		wallet.ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
		System.out.println("done");
		assert(rcvdTx != null);
		TransactionBroadcast broadcast = pg.broadcastTransaction(rcvdTx);
		//ListenableFuture<Transaction> future = broadcast.broadcast();
		if(inputOrder != outputOrder) {
			partnerProof.setFilePath(ownProof.getFilePath());
			partnerProof.setProofChangeEventListeners(ownProof.getProofChangeEventListeners());
			partnerProof.setProofConfidenceChangeEventListeners(ownProof.getProofConfidenceChangeEventListeners());
			ownProof = partnerProof;
		}
		ownProof.addTransaction(rcvdTx, outputOrder, outSp);
		ownProof.writeToFile();
		//TODO check later on that block is in blockchain, in case that program terminates, maybe a flag or something in proof messages
		//that indicates this?
		//TODO is this a duplicate? already add listener in addtransaction
		rcvdTx.getConfidence().addEventListener(new TransactionConfidence.Listener() {
			
			@Override
			public void onConfidenceChanged(TransactionConfidence arg0,
					ChangeReason arg1) {
				if(arg0.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(arg0.getDepthInBlocks() >= 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is 1");

					rcvdTx.getConfidence().removeEventListener(this);
				}
			}
		});

	}
	
	private void commitRcvdFinalTx(
			final CLTVScriptPair outSp, byte[] arg0, int inputOrder, int outputOrder) {
		System.out.println("call commitrcvdfinaltx");
		System.out.println("deserialize finished transaction");
		final Transaction finishedTx = deserializeTransaction(arg0);
		finishedTx.verify();
		System.out.println("print finishedtx");
		System.out.println(finishedTx);
		w.commitTx(finishedTx);
		if(inputOrder != outputOrder) {
			partnerProof.setFilePath(ownProof.getFilePath());
			partnerProof.setProofChangeEventListeners(ownProof.getProofChangeEventListeners());
			partnerProof.setProofConfidenceChangeEventListeners(ownProof.getProofConfidenceChangeEventListeners());
			ownProof = partnerProof;
		}
		ownProof.addTransaction(finishedTx, outputOrder, outSp);
		ownProof.writeToFile();
		System.out.println("write out new proof");
		finishedTx.getConfidence().addEventListener(new TransactionConfidence.Listener() {

			@Override
			public void onConfidenceChanged(
					TransactionConfidence arg0,
					ChangeReason arg1) {
				if(arg0.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(arg0.getDepthInBlocks() >= 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is " + arg0.getDepthInBlocks());

					finishedTx.getConfidence().removeEventListener(this);
				}
				
			}
		});
		System.out.println("commited mixtx to wallet");
		for(MixFinishedEventListener l : mfListeners) {
			l.onMixFinished();
		}
		//TODO add confidence change event listener, then add to end of proof message
	}
	
	
	public void addMixFinishedEventListener(MixFinishedEventListener listener) {
		mfListeners.add(listener);
	}
	
	public void removeMixFinishedEventListener(MixFinishedEventListener listener) {
		mfListeners.remove(listener);
	}

	public void addMixAbortEventListener(MixAbortEventListener listener) {
		maListeners.add(listener);
	}

	public void removeMixAbortEventListener(MixAbortEventListener listener) {
		maListeners.remove(listener);
	}

	public void addMixPassiveEventListener(MixPassiveEventListener listener) {
		mpListeners.add(listener);
	}

	public void removeMixPassiveEventListener(MixPassiveEventListener listener) {
		mpListeners.remove(listener);
	}
	
	public void setMixPartnerAdress(String onion) {
		this.mixPartnerAdress = new Identifier(onion);
	}
	
	public void setOwnProof(ProofMessage pm) {
		this.ownProof = pm;
	}
	
	public void setBroadcastAnnouncement(BroadcastAnnouncement bca) {
		this.bca = bca;
		this.mixPartnerAdress = new Identifier(bca.getOnionAdress() + ".onion");
	}
	public List<MixAbortEventListener> getMaListeners() {
		return maListeners;
	}


	public void setLockTime(int lockTime) {
		this.lockTime = lockTime;		
	}
	
}
