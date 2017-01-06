import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

/**
 * We need special p2sh script, which locks the txoutputs,
 * so that spv clients can verify that they are UTXOs,
 * after the expiration time, we can create new mix txs with new
 * expiration times so that those psynyms are again verifiably utxo by spv clients
 * 
 * @author kai
 *
 */
public class CLTVScriptPair {
	
	private static final int LOCKTIME_TRESHOLD = 500000000;
	private Script pubKeyScript;
	private Script redeemScript;
	
	CLTVScriptPair(Script a, Script b) {
		this.pubKeyScript = a;
		this.redeemScript = b;
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
		
		pubKeyScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
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
		return this.pubKeyScript;
	}
	
	public Script getRedeemScript() {
		return this.redeemScript;
	}
	
	private static int log(long expireDate, int base) {
		return (int) (Math.log(expireDate) / Math.log(base));
	}
	
	
}
