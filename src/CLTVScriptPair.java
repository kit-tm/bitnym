import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.Wallet;

/**
 * We need special p2sh script, which locks the txoutputs,
 * so that spv clients can verify that they are UTXOs,
 * after the expiration time, we can create new mix txs with new
 * expiration times so that those psynyms are again verifiably utxo by spv clients
 * 
 * @author kai
 *
 */


//TODO: bitcoinj doesn't seem to support management of redeemscripts for custom p2sh,
//maybe use serializable hashtable to store some CLTVScriptPair needed for when creating inputs
//spending those p2sh outputs
public class CLTVScriptPair implements Serializable {
	
	private static final int LOCKTIME_TRESHOLD = 500000000;
	private Script pubKeyScript;
	private Script redeemScript;
	private byte[] pubkeyHash; //to get the key by pubkeyhash
	
	CLTVScriptPair(Script a, Script b) {
		this.pubKeyScript = a;
		this.redeemScript = b;
		this.pubkeyHash = b.getPubKeyHash();
	}
	
	
	//TODO compute expire date maybe in this class, instead of letting the caller doing the work?
	//TODO compute unix time 
	CLTVScriptPair(ECKey newPsynymKey, long expireDate) {
		ScriptBuilder sb = new ScriptBuilder();
		
		//the part that freezes the tx output, special 5 byte number
		sb.data(encodeExpireDate(expireDate));
		sb.op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY);
		sb.op(ScriptOpCodes.OP_DROP);
		
		//standard p2pkh part
		sb.op(ScriptOpCodes.OP_DUP);
		sb.op(ScriptOpCodes.OP_HASH160);
		sb.data(newPsynymKey.getPubKeyHash());
		sb.op(ScriptOpCodes.OP_EQUALVERIFY);
		sb.op(ScriptOpCodes.OP_CHECKSIG);
		
		this.redeemScript = sb.build();
		
		this.pubKeyScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
		this.pubkeyHash = redeemScript.getPubKeyHash();
	}
	
	//TODO encode sign in most-significant bit
	//checklocktimeverify has a special format, encode this into a bytearray to add to the script
	private byte[] encodeExpireDate(long expireDate) {
		int numOfBits = CLTVScriptPair.log(expireDate, 2);
		int numOfBytes = CLTVScriptPair.log(numOfBits, 8);
		ByteBuffer b = ByteBuffer.allocate(numOfBytes);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putLong(expireDate);
		return null;
	}


	public Script getPubKeyScript() {
		assert(pubKeyScript.isPayToScriptHash());
		return this.pubKeyScript;
	}
	
	public Script getRedeemScript() {
		return this.redeemScript;
	}
	
	public byte[] getPubKeyHash() {
		return this.pubkeyHash;
	}
	
	private static int log(long expireDate, int base) {
		return (int) (Math.log(expireDate) / Math.log(base));
	}
	
	
	private void writeObject(ObjectOutputStream oos)
			throws IOException {
		oos.writeObject(redeemScript.getProgram());
		oos.writeObject(pubKeyScript.getProgram());
		oos.writeObject(pubkeyHash);
	}
	
	private void readObject(ObjectInputStream ois)
			throws ClassNotFoundException, IOException {
		this.redeemScript = new Script((byte[]) ois.readObject());
		this.pubKeyScript = new Script((byte[]) ois.readObject());
		this.pubkeyHash = (byte[]) ois.readObject();
		
	}
	
	//our sigScript of an p2sh-Output needs to append the signature first, than the redeem script
	public Script calculateSigScript(Transaction tx, int inOffset, Wallet w) {
		assert(inOffset >= 0);
		//get key by pubkeyhash from wallet
		ECKey key = w.findKeyFromPubHash(this.pubkeyHash);
		TransactionSignature ts = tx.calculateSignature(inOffset, key, redeemScript, SigHash.ALL, false);
		ScriptBuilder sb = new ScriptBuilder();
		byte[] sigEncoded = ts.encodeToDER();
		sb.data(sigEncoded);
		sb.data(redeemScript.getProgram());
		
		
		return sb.build();
	}
	
}
