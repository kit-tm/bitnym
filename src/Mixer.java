import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionConfidence.Listener.ChangeReason;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.VerifyFlag;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.PTP;
import edu.kit.tm.ptp.ReceiveListener;
import edu.kit.tm.ptp.SendListener;
import edu.kit.tm.ptp.SendListener.State;

//TODO commitTx with complete tx send by mixpartner needs to be called
//TODO implement other branch case for different output order

//TODO check which broadcastannouncement read and accepted for mixing, and check whether we want to mix this nym or not

//TODO refactor into one branch, mix outputs, send back for signing, and sign ourselves, less code => less bugs
//TODO close ptp after mixing
//TODO lock messages to certain .onion adress, after a certain .onion adress is used (when active) or received (when passive)

public class Mixer {
	private PTP ptp;
	private Identifier mixPartnerAdress;
	private ProofMessage ownProof, partnerProof;
	private Wallet w;
	private NetworkParameters params;
	private PeerGroup pg;
	private BlockChain bc;
	
	//get the onion mix adress from a broadcastannouncement
	public Mixer(PTP ptp, BroadcastAnnouncement bca, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.ptp = ptp;
		this.mixPartnerAdress = new Identifier(bca.getOnionAdress() + ".onion");
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
	}
	
	
	//get onionAdress directly
	public Mixer(PTP ptp, String onionAddress, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.ptp = ptp;
		this.mixPartnerAdress = new Identifier(onionAddress);
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
	}
	
	//constructor for just listening
	public Mixer(PTP ptp, ProofMessage pm, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.ptp = ptp;
		this.ownProof = pm;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
	}
	
	public void passiveMix(byte[] arg0) {
		ptp.setSendListener(new SendListener() {

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
	              break;
	          }
	        }
	      });
		//received serialized proof, so deserialize, and check proof
		System.out.println("check partner proof");
		this.partnerProof = (ProofMessage) deserialize(arg0);
		if(!partnerProof.isProbablyValid(params, bc, pg)) {
			System.out.println("proof of mix partner is invalid");
			return;
		}
		//send own proof to partner
		this.ptp.setReceiveListener(new ReceiveListener() {

			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				// TODO Auto-generated method stub
				//deserialize received tx, and add own input and output and
				//sign then and send back
				System.out.println("try to deserialize tx received from mixpartner");
				final Transaction rcvdTx = deserializeTransaction(arg0);
				if(!checkTxInputIsFromProof(rcvdTx, 0)) {
					//partner put an input which is not from the proof
					return;
				}
				final int outputOrder = rcvdTx.getOutputs().size();
				System.out.println("deserialized tx received from mixpartner");
				
				ECKey newPsnymKey = new ECKey();
				Address nymAdrs = new Address(params, newPsnymKey.getPubKeyHash());
				w.importKey(newPsnymKey);
				long unixTime = System.currentTimeMillis() / 1000L;
				final CLTVScriptPair outSp = new CLTVScriptPair(newPsnymKey, unixTime-(10*60*150));
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
					ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							//deserialize tx, check rcvd Tx, then sign and broadcast
							
							Transaction lastTxVersion = deserializeTransaction(arg0);
							if(!checkTx(rcvdTx, lastTxVersion)) {
								return;
							}
							lastTxVersion.getInput(1).setScriptSig(inSp.calculateSigScript(lastTxVersion, 1, w));
							lastTxVersion.getInput(1).verify(ownProof.getLastTransactionOutput());
							assert(lastTxVersion != null);
							broadcastMixTx(outputOrder, outSp,lastTxVersion, 1);
							
						}
					});
					ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
					try {
						TimeUnit.MINUTES.sleep(2);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					

				} else if(outputOrder == 1) {
					System.out.println("partner added first output to the tx");
					//partner added already his output			

					//sign input and send back for signing

					rcvdTx.getInput(1).setScriptSig(inSp.calculateSigScript(rcvdTx, 1, w));
					rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					
					//rcvdTx.getInput(1).verify(ownProof.getLastTransactionOutput());
					ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							if(!checkTx(rcvdTx, deserializeTransaction(arg0))) {
								return;
							}
							commitRcvdFinalTx(outSp, arg0, 1, outputOrder);
						}

						
					});
					ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
					try {
						TimeUnit.MINUTES.sleep(2);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}


		});
		this.ptp.sendMessage(serialize(ownProof), mixPartnerAdress);
		try {
			TimeUnit.MINUTES.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
		
	}
	
	private boolean checkTxInputIsFromProof(Transaction rcvdTx, int i) {
		return rcvdTx.getInput(i).getConnectedOutput().equals(partnerProof.getLastTransactionOutput());
	}

	
	public void initiateMix() {
		
		ptp.setSendListener(new SendListener() {

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
		this.ptp.setReceiveListener(new ReceiveListener() {
			
			//deserialize proof
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				partnerProof = (ProofMessage) deserialize(arg0);
				//check proof
				System.out.println("check partner proof");
				if(!partnerProof.isProbablyValid(params, bc, pg)) {
					System.out.println("proof of mix partner is invalid");
					return;
				}
				challengeResponse();
				
			}
		});
		this.ptp.sendMessage(serializedProof, mixPartnerAdress);
		try {
			TimeUnit.MINUTES.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return serialized;
	}
	
	private Object deserialize(byte[] serialized) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
			         ObjectInput in = new ObjectInputStream(bis)) {
			        return in.readObject();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	
	
	public void listenForMix() {
		System.out.println("listenformix");
		this.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				mixPartnerAdress = arg1;
				passiveMix(arg0);
			}
		});
	}
	
	public void closeListeningForMix() {
		this.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	
	//check that partner used the proper transactioninput announced in corresponding proof
	//right output coin value etc.
	private boolean checkTx(Transaction mixTx, Transaction rcvdTxToCheck) {
		if(rcvdTxToCheck.getInputs().size() > 2 || rcvdTxToCheck.getOutputs().size() > 2) {
			return false;
		}
		
		if(rcvdTxToCheck.getOutputs().size() == 2) {
			if(!rcvdTxToCheck.getOutput(0).getValue().equals(rcvdTxToCheck.getOutput(1).getValue())) {
				return false;
			}
		}
		
		for(int i=0; i < mixTx.getInputs().size(); i++) {
			if(!mixTx.getInput(i).getConnectedOutput().equals(rcvdTxToCheck.getInput(i).getConnectedOutput())) {
				return false;
			}
		}
		for(int i=0; i < mixTx.getOutputs().size(); i++) {
			if(!mixTx.getOutput(i).equals(rcvdTxToCheck.getOutput(i))) {
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
				mixTx.setLockTime(currentUnixTime-(10*60*150));
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
				Address nymAdrs = new Address(params, psnymKey.getPubKeyHash());
				w.importKey(psnymKey);
				long unixTime = System.currentTimeMillis() / 1000L;
				//add wished lock time
				final CLTVScriptPair inSp = ownProof.getScriptPair();
				assert(inSp != null);
				//TODO add wished locktime
				final CLTVScriptPair outSp = new CLTVScriptPair(psnymKey, unixTime-(10*60*150));
				//TODO compute the right value as (input1 + input2 - fee)/2
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
					this.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							final Transaction rcvdTx = deserializeTransaction(arg0);
							checkTxInputIsFromProof(rcvdTx, 1);
							checkTx(mixTx, rcvdTx);
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
					this.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
					try {
						TimeUnit.MINUTES.sleep(2);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}


					
				} else {
					//let the mixpartner add his output first and partners input
					//we will add then our output and sign the tx, send it to the partner
					//and let the partner sign the tx
					serializedTx = mixTx.bitcoinSerialize();
					this.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							// add our output, sign and send to partner then
							Transaction penFinalTx = deserializeTransaction(arg0);
							checkTxInputIsFromProof(penFinalTx, 1);
							checkTx(mixTx, penFinalTx);
							penFinalTx.addOutput(newPsyNym);
							penFinalTx.getInput(0).setScriptSig(inSp.calculateSigScript(penFinalTx, 0, w));
							penFinalTx.getInput(0).verify(ownProof.getLastTransactionOutput());
							ptp.setReceiveListener(new ReceiveListener() {
								
								@Override
								public void messageReceived(byte[] arg0, Identifier arg1) {
									commitRcvdFinalTx(outSp, arg0, 0, outputOrder);	
								}
							});
							ptp.sendMessage(penFinalTx.bitcoinSerialize(), mixPartnerAdress);
							try {
								TimeUnit.MINUTES.sleep(2);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						}
					});
					this.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
					try {
						TimeUnit.MINUTES.sleep(2);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rcvdTx;
	}
	
	
	//broadcast the mix tx, add to proof the new Transaction
	private void broadcastMixTx(final int outputOrder,
			final CLTVScriptPair outSp,
			final Transaction rcvdTx,
			int inputOrder) {
		rcvdTx.verify();
		w.commitTx(rcvdTx);
		System.out.println(rcvdTx);
		ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
		assert(rcvdTx != null);
		TransactionBroadcast broadcast = pg.broadcastTransaction(rcvdTx);
		//ListenableFuture<Transaction> future = broadcast.broadcast();
		if(inputOrder != outputOrder) {
			partnerProof.setFilePath(ownProof.getFilePath());
			ownProof = partnerProof;
		}
		ownProof.addTransaction(rcvdTx, outputOrder, outSp);
		ownProof.writeToFile();
		//TODO check later on that block is in blockchain, in case that program terminates, maybe a flag or something in proof messages
		//that indicates this?
		rcvdTx.getConfidence().addEventListener(new TransactionConfidence.Listener() {
			
			@Override
			public void onConfidenceChanged(TransactionConfidence arg0,
					ChangeReason arg1) {
				// TODO Auto-generated method stub
				if(arg0.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(arg0.getDepthInBlocks() == 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is 1");

					//TODO remove eventlistener
				}
			}
		});
	}
	
	private void commitRcvdFinalTx(
			final CLTVScriptPair outSp, byte[] arg0, int inputOrder, int outputOrder) {
		System.out.println("deserialize finished transaction");
		Transaction finishedTx = deserializeTransaction(arg0);
		finishedTx.verify();
		System.out.println(finishedTx);
		w.commitTx(finishedTx);
		if(inputOrder != outputOrder) {
			partnerProof.setFilePath(ownProof.getFilePath());
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
				// TODO Auto-generated method stub
				if(arg0.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					return;
				}
				if(arg0.getDepthInBlocks() == 1) {
					//add to proof message and write to file
					System.out.println("confidence of mix tx is 1");

					//TODO remove eventlistener
				}
				
			}
		});
		System.out.println("commited mixtx to wallet");
		//TODO add confidence change event listener, then add to end of proof message
		//TODO check consistency with signed tx
	}
	
	
	
	
}
