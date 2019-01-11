package bitnymWallet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.wallet.Wallet;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.PTP;
import edu.kit.tm.ptp.ReceiveListener;

public class ChallengeResponseVerifier {
	
	private BitNymWallet wallet;
	private Identifier mixPartnerAdress;
	private ProofMessage partnerProof;
	private Wallet w;
	private NetworkParameters params;
	private PeerGroup pg;
	private BlockChain bc;

	public ChallengeResponseVerifier(BitNymWallet wallet, Wallet w, NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.wallet = wallet;
		this.w = w;
		this.params = params;
		this.pg = pg;
		this.bc = bc;
	}
	
	
	public void listenForProofToVerify() {
		System.out.println("listen for proof to verify");
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				mixPartnerAdress = arg1;
				challengeResponse(arg0);
			}
		});
	}
	
	private void challengeResponse(byte[] arg0) {
		this.partnerProof = (ProofMessage) deserialize(arg0);
		if(!this.partnerProof.isValidProof(bc, pg, params)) {
			System.out.println("proof is not valid, verification failed");
			return;
		}
		if(!partnerProof.isPubKeyCorrect(params)) {
			System.out.println("alleged pseudonym holder didn't send right pubkey corresponding to pseudonym, abort");
			return;
		}
		System.out.println("proof is valid, start challenge response procedure");
		System.out.println("draw a challenge string at random");
		final byte[] challengeString = drawChallengeNumber(20);
		//System.out.println("drew random string " + javax.xml.bind.DatatypeConverter.printHexBinary(challengeString));
		this.wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				//verify signature of challengeString
				ECDSASignature signature = ECDSASignature.decodeFromDER(arg0);
				byte[] notification = new byte[1];
				if(!isSignatureCorrect(signature, challengeString)) {
					System.out.println("signature is not correct");
					notification[0] = 0;
					wallet.ptp.sendMessage(notification, mixPartnerAdress);
					return;
				}
				System.out.println("signature is correct");
				notification[0] = 1;
				wallet.ptp.sendMessage(notification, mixPartnerAdress);
			}
		});
		wallet.ptp.sendMessage(challengeString, mixPartnerAdress);

	}
	
	private boolean isSignatureCorrect(ECDSASignature signature, byte[] challengeString) {
		Sha256Hash hash = Sha256Hash.wrap(Sha256Hash.hash(challengeString));
		ECKey verificationKey = ECKey.fromPublicOnly(partnerProof.getScriptPair().getPubKey());
		return verificationKey.verify(hash, signature);
	}


	private byte[] drawChallengeNumber(int numOfBytes) {
		byte[] result = new byte[numOfBytes];
		SecureRandom sr = new SecureRandom();
		sr.nextBytes(result);
		return result;
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


	public void proveToVerifier(ProofMessage message, final ECKey signingKey) {
		System.out.println("Sending proof, listening for challenge");
		wallet.ptp.setReceiveListener(new ReceiveListener() {
			
			@Override
			public void messageReceived(byte[] arg0, Identifier arg1) {
				byte[] challengeString = arg0;
				Sha256Hash hash = Sha256Hash.wrap(Sha256Hash.hash(challengeString));
				ECDSASignature signature = sign(hash, signingKey);			
				byte[] serializedSignature = signature.encodeToDER();
				System.out.println("received and solved challenge, listening for confirmation");
				wallet.ptp.setReceiveListener(new ReceiveListener() {
					
					@Override
					public void messageReceived(byte[] arg0, Identifier arg1) {
						if(arg0[0] == 1) {
							System.out.println("pseudonym was accepted");
						} else {
							System.out.println("pseudonym was not accepted");
						}
					}
				});
				wallet.ptp.sendMessage(serializedSignature , mixPartnerAdress);
				
			}
		});
		wallet.ptp.sendMessage(serialize(message), mixPartnerAdress);
	}


	private ECDSASignature sign(Sha256Hash hash, ECKey signingKey) {		
		return signingKey.sign(hash);
	}
	
	public void setMixPartnerAdress(String onionAddress) {
		this.mixPartnerAdress = new Identifier(onionAddress);
	}
}
