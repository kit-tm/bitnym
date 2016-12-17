import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.VerifyFlag;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.PTP;
import edu.kit.tm.ptp.ReceiveListener;
import edu.kit.tm.ptp.SendListener;
import edu.kit.tm.ptp.SendListener.State;


public class Mixer {
	private PTP ptp;
	private Identifier mixPartnerAdress;
	private ProofMessage ownProof, partnerProof;
	private Wallet w;
	private NetworkParameters params;
	
	public Mixer(PTP ptp, BroadcastAnnouncement bca, ProofMessage pm, Wallet w, NetworkParameters params) {
		this.ptp = ptp;
		this.mixPartnerAdress = new Identifier(bca.getOnionAdress() + ".onion");
		this.ownProof = pm;
		this.w = w;
		this.params = params;
	}
	
	public Mixer(PTP ptp, ProofMessage pm, Wallet w, NetworkParameters params) {
		this.ptp = ptp;
		this.ownProof = pm;
		this.w = w;
		this.params = params;
	}
	
	public void passiveMix(byte[] arg0) {
		//received serialized proof, so deserialize, and check proof
		this.partnerProof = (ProofMessage) deserialize(arg0);
		if(!partnerProof.isValidProof()) {
			System.out.println("proof of mix partner is invalid");
			return;
		}
		//send own proof to partner
		this.ptp.sendMessage(serialize(ownProof), mixPartnerAdress);
		this.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				// TODO Auto-generated method stub
				//deserialize received tx, and add own input and output and
				//sign then and send back
				BitcoinSerializer bs = new BitcoinSerializer(params, false);
				ByteBuffer bb = ByteBuffer.wrap(arg0);
				Transaction rcvdTx = null;
				try {
					rcvdTx = (Transaction) bs.deserialize(bb);
				} catch (ProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				//check that tx input references the psynym in the proof
				Set<Script.VerifyFlag> s = new HashSet<Script.VerifyFlag>();
				rcvdTx.getInput(0).getScriptSig().correctlySpends(rcvdTx, 0, partnerProof.getLastTransactionOutput().getScriptPubKey(),s);
				
				//check output value
				if(rcvdTx.getOutputs().size() == 0) {
					
				} else if(rcvdTx.getOutputs().size() == 1) {
					//partner added already his output					
					ECKey psnymKey = new ECKey();
					Address nymAdrs = new Address(params, psnymKey.getPubKeyHash());
					w.importKey(psnymKey);
					TransactionOutput newPsyNym = new TransactionOutput(params, rcvdTx, Coin.ZERO, nymAdrs);
					rcvdTx.addOutput(newPsyNym);
					//sign input and send back for signing
					rcvdTx.addSignedInput(ownProof.getLastTransactionOutput(), psnymKey);
					ptp.sendMessage(rcvdTx.bitcoinSerialize(), mixPartnerAdress);
				}
				
			}
		});
		
		
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
		
		byte[] pmBytes = null;
		
		pmBytes = serialize(this.ownProof); 
		
		System.out.println("mixpartneradress " + mixPartnerAdress.getTorAddress());
		this.ptp.sendMessage(pmBytes, mixPartnerAdress);
		this.ptp.setReceiveListener(new ReceiveListener() {
			
			//deserialize proof
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				partnerProof = (ProofMessage) deserialize(arg0);
				//check proof
				if(!partnerProof.isValidProof()) {
					System.out.println("proof of mix partner is invalid");
					return;
				}
				challengeResponse();
				
			}
		});
		
	}

	private void challengeResponse() {
		byte[] publicKey = this.w.findKeyFromPubHash(ownProof.getLastTransaction().getOutput(ownProof.getLastOutputIndex()).getAddressFromP2PKHScript(params).getHash160()).getPubKey();
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
	
	//check that partner used the proper transactioninput
	//right output coin value etc.
	private void checkTx(Transaction mixTx, Transaction rcvdTx,
			ProofMessage partnerProof) {
		// TODO Auto-generated method stub
		
	}
	
	private void mixAndConstructNewProof() {
		//mix
				final Transaction mixTx = new Transaction(params);
				mixTx.addInput(this.ownProof.getLastTransactionOutput());
				// draw random value for decision of output order
				// security of randomness? probably not a big thing
				Random r = new Random();
				int outputOrder = r.nextInt(2);
				//set the value later on
				ECKey psnymKey = new ECKey();
				Address nymAdrs = new Address(params, psnymKey.getPubKeyHash());
				w.importKey(psnymKey);
				TransactionOutput newPsyNym = new TransactionOutput(params, mixTx, Coin.ZERO, nymAdrs);
				byte[] serializedTx;
				if(outputOrder == 0) {
					//add the own output and send the unfinished tx to the mix partner
					//the partner adds the input and output and signs the tx, we will then sign it
					//after checking correctness
					mixTx.addOutput(newPsyNym);
					serializedTx = mixTx.bitcoinSerialize();
					this.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
					//check tx, sign it, then send it out to the bitcoin network
					this.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							BitcoinSerializer bs = new BitcoinSerializer(params, false);
							ByteBuffer bb = ByteBuffer.wrap(arg0);
							Transaction rcvdTx = null;
							try {
								rcvdTx = (Transaction) bs.deserialize(bb);
							} catch (ProtocolException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							checkTx(mixTx, rcvdTx, partnerProof);
							//sign transaction and send it to the network
							rcvdTx.verify();
							SendRequest req = SendRequest.forTx(rcvdTx);
							req.shuffleOutputs = false;
							req.signInputs = true;
							Wallet.SendResult result = null;
							try {
								result = w.sendCoins(req);
							} catch (InsufficientMoneyException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}

							try {
								result.broadcastComplete.get();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (ExecutionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}


					});
				} else {
					//let the mixpartner add his output first and partners input
					//we will add then our output and sign the tx, send it to the partner
					//and let the partner sign the tx, probably cumbersome way to handle it
					//it would be simpler to just negotiate who 
					serializedTx = mixTx.bitcoinSerialize();
					this.ptp.sendMessage(serializedTx, this.mixPartnerAdress);
					this.ptp.setReceiveListener(new ReceiveListener() {
						
						@Override
						public void messageReceived(byte[] arg0, Identifier arg1) {
							// TODO Auto-generated method stub
							
						}
					});
				}
				
				//new proofs construction
				if(outputOrder == 0) {
					this.ownProof.addTransaction(mixTx, outputOrder);
				} else {
					this.ownProof = this.partnerProof;
					this.ownProof.addTransaction(mixTx, outputOrder);
				}
	}
	
	
	
	
}
