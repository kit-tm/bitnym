package bitnymWallet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.kit.tm.ptp.MessageReceivedListener;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.ReceiveListener;
import edu.kit.tm.ptp.SendListener;


//TODO check which broadcastannouncement read and accepted for mixing, and check whether we want to mix this nym or not

//TODO refactor into one branch, mix outputs, send back for signing, and sign ourselves, less code => less bugs

public class Mixer {
	public enum AbortCode {
		TIMEOUT,
		NO_PARTNER,
		PROOF_INVALID,
		PARTNER_ABORT,
		MIX_SIMULTAN,
		BROADCAST_OLD,
	}
	private BitNymWallet wallet;
	private BroadcastAnnouncement bca;
	private Identifier mixPartnerAdress;
	private ProofMessage ownProof, partnerProof;
	private Wallet w;
	private Context context;
	private NetworkParameters params;
	private PeerGroup pg;
	private BlockChain bc;
	private List<MixFinishedEventListener> mfListeners;
	private List<MixAbortEventListener> maListeners;
	private List<MixStartedEventListener> mpListeners;
	private int lockTime;
	private boolean successful;
	private long mixRequestTimestamp = 0;

	private List<WaitForDataListener> waitForDataListeners;
	/**
	 * True if currently mixing, false otherwise. Used to avoid mixing more than once at a time
	 */
	private boolean mixing= false;
	
	//get the onion mix adress from a broadcastannouncement
	public Mixer(BitNymWallet wallet, BroadcastAnnouncement bca, ProofMessage pm, Wallet w, Context context, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.bca = bca;
		this.mixPartnerAdress = new Identifier(bca.getOnionAdress() + ".onion");
		this.ownProof = pm;
		this.w = w;
		this.context = context;
		this.params = context.getParams();
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixStartedEventListener>();
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		this.lockTime = 0; 
	}
	
	
	//get onionAdress directly
	public Mixer(BitNymWallet wallet, String onionAddress, ProofMessage pm, Wallet w, Context context, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.mixPartnerAdress = new Identifier(onionAddress);
		this.ownProof = pm;
		this.w = w;
		this.context = context;
		this.params = context.getParams();
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixStartedEventListener>();
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		this.lockTime = 0;

	}
	
	//constructor for just listening
	public Mixer(BitNymWallet wallet, ProofMessage pm, Wallet w, Context context, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.ownProof = pm;
		this.w = w;
		this.context = context;
		this.params = context.getParams();
		this.pg = pg;
		this.bc = bc;
		this.mfListeners = new ArrayList<MixFinishedEventListener>();
		this.maListeners = new ArrayList<MixAbortEventListener>();
		this.mpListeners = new ArrayList<MixStartedEventListener>();
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		this.lockTime = 0;
	}
	
	public void passiveMix(byte[] proofData) {
		Context.propagate(context);
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

		// add listener for MixAbortMessage, which is sent by mixpartner on abort
		this.wallet.ptp.setReceiveListener(MixAbortMessage.class, new MessageReceivedListener<MixAbortMessage>() {
			@Override
			public void messageReceived(MixAbortMessage msg, Identifier identifier) {
				System.out.println("Passive: Abort received from " + identifier);
				mixAbort(AbortCode.PARTNER_ABORT);
			}
		});
		//received serialized proof, so deserialize, and check proof
		System.out.println("check partner proof");
		this.partnerProof = (ProofMessage) deserialize(proofData);
		partnerProof.addWaitForDataListener(new WaitForDataListener() {
			@Override
			public void waitForData(boolean waiting) {
				for(WaitForDataListener listener : waitForDataListeners) {
					listener.waitForData(waiting);
				}
			}
		});
		if(!partnerProof.isProbablyValid(params, bc, pg)) { //TODO is it possible that invalid is returned although it should be valid?
			System.out.println("proof of mix partner is invalid");
			mixAbort(AbortCode.PROOF_INVALID);
			return;
		}
		//send own proof to partner
		System.out.println("listen for first part of mix transaction");
		this.wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {

			@Override
			public void messageReceived(SendProofMessage msg, Identifier ident) {
				//deserialize received tx, and add own input and output and
				//sign then and send back
				System.out.println("try to deserialize tx received from mixpartner");
				final Transaction rcvdTx = deserializeTransaction(msg.data);
				if(!checkTxInputIsFromProof(rcvdTx, 0)) {
					System.out.println("checktxinput is from proof failed");
					mixAbort(AbortCode.PROOF_INVALID);
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
					wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {
						
						@Override
						public void messageReceived(SendProofMessage msg, Identifier ident) {
							//deserialize tx, check rcvd Tx, then sign and broadcast
							
							Transaction lastTxVersion = deserializeTransaction(msg.data);
							if(!checkTx(rcvdTx, lastTxVersion)) {
								System.out.println("checktx failed");
								//mixAbort(5);
								//return;
							}
							try {
								lastTxVersion.getInput(1).setScriptSig(inSp.calculateSigScript(lastTxVersion, 1, w));
								lastTxVersion.getInput(1).verify(ownProof.getLastTransactionOutput());
								assert (lastTxVersion != null);
								broadcastMixTx(outputOrder, outSp, lastTxVersion, 1);
							} catch (ScriptException e) {
								e.printStackTrace();
								mixAbort(AbortCode.PROOF_INVALID);
								return;
							}
						}
					});
					wallet.sendMessage(new SendProofMessage(rcvdTx.bitcoinSerialize()), mixPartnerAdress);
					System.out.println("done");

				} else if(outputOrder == 1) {
					System.out.println("partner added first output to the tx");
					//partner added already his output			

					//sign input and send back for signing

					rcvdTx.getInput(1).setScriptSig(inSp.calculateSigScript(rcvdTx, 1, w));
					try {
						rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					} catch(ScriptException e) {
						e.printStackTrace();
						mixAbort(AbortCode.PROOF_INVALID);
						return;
					}

					
					//rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					System.out.println("listen for complete mix transaction. other started");
					wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {
						
						@Override
						public void messageReceived(SendProofMessage msg, Identifier ident) {
							// TODO Auto-generated method stub
							if(!checkTx(rcvdTx, deserializeTransaction(msg.data))) {
								System.out.println("checktx failed");
								//mixAbort(5);
								//return;
							}
							commitRcvdFinalTx(outSp, msg.data, 1, outputOrder);
							System.out.println("commited tx, create new hidden service");
							wallet.restartPTP();
						}

						
					});
					wallet.sendMessage(new SendProofMessage(rcvdTx.bitcoinSerialize()), mixPartnerAdress);
					System.out.println("done");

				}

			}


		});
		System.out.println("New Tx:");
		System.out.println(ownProof.getLastTransaction());
		this.wallet.sendMessage(new SendProofMessage(serialize(ownProof)), mixPartnerAdress);
		System.out.println("done");
	}
	
	private boolean checkTxInputIsFromProof(Transaction rcvdTx, int i) {
		if (rcvdTx == null) {
			return false;
		}
		//return rcvdTx.getInput(i).getConnectedOutput().equals(partnerProof.getLastTransactionOutput());
		// TODO sometimes index i is too high (1, but array size of rcvdTx only 1)
		if (rcvdTx.getInputs().size() > i) {
			System.out.println("DEBUG: transaction for comparison " + rcvdTx.getInput(i).getOutpoint().toString() + "(" + rcvdTx.getInput(i).getOutpoint().getHash() + ")");
			System.out.println("DEBUG: last transaction of partner " + partnerProof.getLastTransaction().toString() + "(" + partnerProof.getLastTransaction().getHash() + ")");
			System.out.println("DEBUG: Index 1(Partner): " + partnerProof.getLastTransactionOutput().getIndex() + ", Index 2(TX): " + rcvdTx.getInput(i).getOutpoint().getIndex());
			return rcvdTx.getInput(i).getOutpoint().getHash().equals(partnerProof.getLastTransaction().getHash()) &&
					partnerProof.getLastTransactionOutput().getIndex() == rcvdTx.getInput(i).getOutpoint().getIndex();
		} else {
			System.out.println("Transaction index too high, abort mixing");
			return false;
		}
	}

	
	public void initiateMix() {
		if (mixing) {
			// already mixing , do not initiate mix
			System.out.println("already mixing! Can't mix active");
			return;
		}
		mixing = true;
		mixStarted();
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
			mixAbort(AbortCode.NO_PARTNER);
			return; //? why no return
		}
		
		byte[] serializedProof = null;
		
		serializedProof = serialize(this.ownProof);
		System.out.println("New Tx");
		System.out.println(ownProof.getLastTransaction());
		
		System.out.println("mixpartner address " + mixPartnerAdress.getTorAddress());
		//ping();
		System.out.println("listen for proof (first message of mix(?))");
		this.wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {
			
			//deserialize proof
			@Override
			public void messageReceived(SendProofMessage msg, Identifier ident) {
				partnerProof = (ProofMessage) deserialize(msg.data);
				//check proof
				System.out.println("check partner proof");
				if(!partnerProof.isProbablyValid(params, bc, pg)) {
					System.out.println("proof of mix partner is invalid, abort");
					mixAbort(AbortCode.PROOF_INVALID);
					return;
				}
				challengeResponse();
				
			}
		});
		MixRequestMessage mixRequestMessage = new MixRequestMessage(serializedProof);
		this.wallet.sendMessage(mixRequestMessage, mixPartnerAdress);
		mixRequestTimestamp = mixRequestMessage.timeStamp;

		// add listener for MixAbortMessage, which is sent by mixpartner on abort
		this.wallet.ptp.setReceiveListener(MixAbortMessage.class, new MessageReceivedListener<MixAbortMessage>() {
			@Override
			public void messageReceived(MixAbortMessage msg, Identifier identifier) {
				System.out.println("Active: Abort received from " + identifier);
				mixAbort(AbortCode.PARTNER_ABORT);
			}
		});

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
	private void mixAbort(AbortCode errorCode) {
		mixing = false;
		// inform mixpartner about abort
		if(errorCode == AbortCode.NO_PARTNER) {
			sendAbort(mixPartnerAdress);
		}
		for (MixAbortEventListener listener : maListeners) {
			listener.onMixAborted(errorCode);
		}
	}

	private void sendAbort(Identifier ident) {
		wallet.ptp.sendMessage(new MixAbortMessage(), ident);
	}

	private void mixStarted() {
		for (MixStartedEventListener listener : mpListeners) {
			listener.onMixStarted();
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
		this.wallet.ptp.setReceiveListener(MixRequestMessage.class, new MessageReceivedListener<MixRequestMessage>() {
			@Override
			public void messageReceived(MixRequestMessage mixRequestMessage, Identifier source) {
				System.out.println("Mix request received, mixing passive");
				if (mixing) {
					// mixing active, do not mix passive
					System.out.println("Already mixing, can't mix passive. Checking if simultaneously mixing active");
					// check if mix request is from same source, if not ignore mix request
					if (mixPartnerAdress.equals(source)) {
						if (mixRequestTimestamp != 0) {
							if(mixRequestTimestamp == mixRequestMessage.timeStamp) {
								// abort
								System.out.println("Can't determine active/passive, abort");
								mixAbort(AbortCode.MIX_SIMULTAN);
								return;
							}
							if (mixRequestMessage.timeStamp > mixRequestTimestamp) {
								//own Request was faster mix active, other should have same result and mix passive
								System.out.println("stay active");
								return;
							}
						}
						System.out.println("mix passive");
						passiveMix(mixRequestMessage.data);
					}
					System.out.println("ignore mix request");
					return;
				}
				mixPartnerAdress = source;
				mixing = true;
				mixStarted();
				System.out.println("Mixing passive with " + mixPartnerAdress.getTorAddress());
				passiveMix(mixRequestMessage.data);
			}
		});

	}
	
	public void closeListeningForMix() {
		System.out.println("listen for nothing, ignoring messages (when closing listening for mix)");
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] msg, Identifier identifier) {
				
			}
		});

		// override all message types
		this.wallet.ptp.setReceiveListener(MixRequestMessage.class, new MessageReceivedListener<MixRequestMessage>() {
			@Override
			public void messageReceived(MixRequestMessage msg, Identifier identifier) {
				// ignore
			}
		});
		this.wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {
			@Override
			public void messageReceived(SendProofMessage msg, Identifier identifier) {
				// ignore
			}
		});
		this.wallet.ptp.setReceiveListener(MixAbortMessage.class, new MessageReceivedListener<MixAbortMessage>() {
			@Override
			public void messageReceived(MixAbortMessage msg, Identifier identifier) {
				// ignore
			}
		});
	}
	
	//check that partner used the proper transactioninput announced in corresponding proof
	//right output coin value etc.
	private boolean checkTx(Transaction mixTx, Transaction rcvdTxToCheck) {
		if(rcvdTxToCheck == null) {
			System.out.println("checktx failed, transaction null");
			return false;
		}
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
		Context.propagate(context);
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
			this.wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {

				@Override
				public void messageReceived(SendProofMessage msg, Identifier source) {
					final Transaction rcvdTx = deserializeTransaction(msg.data);
					if(!checkTxInputIsFromProof(rcvdTx, 1)) {
						System.out.println("tx input from received tx is not the same as in the proof");
						//return;
					}
					if(!checkTx(mixTx, rcvdTx)) {
						System.out.println("checktx failed");
					}
					//sign transaction and send it to the network
					try {
						rcvdTx.getInput(0).setScriptSig(inSp.calculateSigScript(rcvdTx, 0, w));
						rcvdTx.getInput(0).verify(ownProof.getLastTransactionOutput());
					} catch (Exception e) {
						System.out.println("DEBUG: WHICH EXCEPTIONS HAPPEN HERE?");
						e.printStackTrace();
						mixAbort(AbortCode.PROOF_INVALID);
						return;
					}
					//this method just does rudimentary checks, does not check whether inputs are already spent for example
					rcvdTx.verify();

					System.out.println(ownProof.getLastTransactionOutput());
					//TODO remove transaction if transaction is rejected, maybe just add to proof message only, and commit only when in the blockchain?
					broadcastMixTx(outputOrder, outSp, rcvdTx, 0);

				}


			});
			this.wallet.sendMessage(new SendProofMessage(serializedTx), this.mixPartnerAdress);
			System.out.println("done");
		} else {
			//let the mixpartner add his output first and partners input
			//we will add then our output and sign the tx, send it to the partner
			//and let the partner sign the tx
			serializedTx = mixTx.bitcoinSerialize();
			System.out.println("listen for transaction of partner to add ours to, he starts");
			this.wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {

				@Override
				public void messageReceived(SendProofMessage msg, Identifier source) {
					// add our output, sign and send to partner then
					try {
						final Transaction penFinalTx = deserializeTransaction(msg.data);
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
					wallet.ptp.setReceiveListener(SendProofMessage.class, new MessageReceivedListener<SendProofMessage>() {

						@Override
						public void messageReceived(SendProofMessage msg, Identifier source) {
							System.out.println("Active last Message received");
							// TODO Auto-generated method stub
							if(!checkTx(penFinalTx, deserializeTransaction(msg.data))) {
								System.out.println("checktx failed");
							}
							commitRcvdFinalTx(outSp, msg.data, 0, outputOrder);
							wallet.restartPTP();
						}
					});
					wallet.sendMessage(new SendProofMessage(penFinalTx.bitcoinSerialize()), mixPartnerAdress);
					System.out.println("done");
					} catch (Exception e) {
						e.printStackTrace();
						mixAbort(AbortCode.PROOF_INVALID);
						return;
					}
				}
			});
			this.wallet.sendMessage(new SendProofMessage(serializedTx), this.mixPartnerAdress);
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


	private Transaction deserializeTransaction(byte[] txData) {
		Transaction rcvdTx = null;
		BitcoinSerializer bs = new BitcoinSerializer(params, false);
		try {
			rcvdTx = bs.makeTransaction(txData);
		} catch (ProtocolException | NegativeArraySizeException e) {
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
			public void messageSent(long id, Identifier destination, State state) {
				System.out.println("message sent is called, creating new hidden service");
				wallet.restartPTP();
				for(MixFinishedEventListener l : mfListeners) {
					assert(mfListeners.size() <= 1);
					mixing = false;
					l.onMixFinished();
				}				
			}
		});
		wallet.sendMessage(new SendProofMessage(rcvdTx.bitcoinSerialize()), mixPartnerAdress);
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
			public void onConfidenceChanged(TransactionConfidence confidence,
					ChangeReason reason) {
				if(confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(confidence.getDepthInBlocks() >= 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is 1");

					rcvdTx.getConfidence().removeEventListener(this);
				}
			}
		});

	}
	
	private void commitRcvdFinalTx(
			final CLTVScriptPair outSp, byte[] txData, int inputOrder, int outputOrder) {
		System.out.println("call commitrcvdfinaltx");
		System.out.println("deserialize finished transaction");
		final Transaction finishedTx = deserializeTransaction(txData);
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
					TransactionConfidence confidence,
					ChangeReason reason) {
				if(confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(confidence.getDepthInBlocks() >= 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is " + confidence.getDepthInBlocks());

					finishedTx.getConfidence().removeEventListener(this);
				}
				
			}
		});
		System.out.println("commited mixtx to wallet");
		for(MixFinishedEventListener l : mfListeners) {
			mixing = false;
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

	public void addMixPassiveEventListener(MixStartedEventListener listener) {
		mpListeners.add(listener);
	}

	public void removeMixPassiveEventListener(MixStartedEventListener listener) {
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

	public void addWaitForDataListener(WaitForDataListener listener) {waitForDataListeners.add(listener);}

	public void setLockTime(int lockTime) {
		this.lockTime = lockTime;		
	}
	
}
