package bitnymWallet;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStoreException;
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


//maybe use serializable hashtable to store some CLTVScriptPair needed for when creating inputs
//spending those p2sh outputs
public class CLTVScriptPair implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final int LOCKTIME_TRESHOLD = 500000000;
	private Script pubKeyScript;
	private Script redeemScript;
	private byte[] pubkeyHash;
	private byte[] pubkey;
	
	CLTVScriptPair(Script a, Script b) {
		this.pubKeyScript = a;
		this.redeemScript = b;
		this.pubkeyHash = b.getPubKeyHash();
	}
	
	CLTVScriptPair() {
		
	}
	
	
	//TODO compute expire date maybe in this class, instead of letting the caller doing the work?
	//TODO change the format to match p2pk as p2pkh doesn't seem useful here
	CLTVScriptPair(ECKey newPsynymKey, long expireDate) {
		ScriptBuilder sb = new ScriptBuilder();
		
		//the part that freezes the tx output, special 5 byte number
		sb.data(encodeExpireDate(expireDate));
		//sb.smallNum(num)
		sb.op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY);
		sb.op(ScriptOpCodes.OP_DROP);
		
		//standard p2pkh part
		sb.op(ScriptOpCodes.OP_DUP);
		sb.op(ScriptOpCodes.OP_HASH160);
		sb.data(newPsynymKey.getPubKeyHash());
		sb.op(ScriptOpCodes.OP_EQUALVERIFY);
		sb.op(ScriptOpCodes.OP_CHECKSIG);
		
		this.redeemScript = sb.build();
		System.out.println("cltvscripiar yielded a locktime of " + new Date(getLockTime()*1000));
		assert(expireDate == getLockTime());

		
		this.pubKeyScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
		this.pubkeyHash = newPsynymKey.getPubKeyHash();
		this.pubkey = newPsynymKey.getPubKey();
	}
	
	//TODO encode sign in most-significant bitencodeExpireDate
	//checklocktimeverify has a special format, encode this into a bytearray to add to the script
	private static byte[] encodeExpireDate(long expireDate) {
		int numOfBits = CLTVScriptPair.log(expireDate, 2);
		System.out.println("numofbits" + numOfBits);
		int numOfBytes = (int) Math.ceil((double) numOfBits / 8.0);
		assert(numOfBytes >= 1 && numOfBytes <= 5); //see implementation of time formati in op_checklocktimeverify
		System.out.println("numOfBytes " + numOfBytes);
		ByteBuffer b = ByteBuffer.allocate(numOfBytes);
		b.order(ByteOrder.LITTLE_ENDIAN);
		//b.put((byte) numOfBytes);
		for(int i=0; i < numOfBytes; i++) {
			byte expireB = (byte) ((expireDate & (0x000000FFL << i*8)) >> i*8);
			b.put(expireB);
		}
		System.out.println(Arrays.toString(b.array()));
		return b.array();
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
	
	//TODO use other function
	private static int log(long expireDate, int base) {
		return (int) ((Math.log(expireDate) / Math.log(base)) + 1);
	}
	
	
	private void writeObject(ObjectOutputStream oos)
			throws IOException {
		oos.writeObject(redeemScript.getProgram());
		oos.writeObject(pubKeyScript.getProgram());
		oos.writeObject(pubkeyHash);
		oos.writeObject(pubkey);
	}
	
	private void readObject(ObjectInputStream ois)
			throws ClassNotFoundException, IOException {
		this.redeemScript = new Script((byte[]) ois.readObject());
		this.pubKeyScript = new Script((byte[]) ois.readObject());
		this.pubkeyHash = (byte[]) ois.readObject();	
		this.pubkey = (byte[]) ois.readObject();
	}
	
	//our sigScript of an p2sh-Output needs to append the signature first, than the redeem script
	public Script calculateSigScript(Transaction tx, int inOffset, Wallet w) {
		assert(inOffset >= 0);
		//get key by pubkeyhash from wallet
		ECKey key = w.findKeyFromPubHash(this.pubkeyHash);
		assert(key != null);
		TransactionSignature ts = tx.calculateSignature(inOffset, key, redeemScript, SigHash.ALL, false);
		ScriptBuilder sb = new ScriptBuilder();
		byte[] sigEncoded = ts.encodeToBitcoin();
		sb.data(sigEncoded);
		assert(TransactionSignature.isEncodingCanonical(sigEncoded));
		sb.data(key.getPubKey());
		sb.data(redeemScript.getProgram());
		
		
		return sb.build();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		if((pubKeyScript != null) && (redeemScript != null)) {
			sb.append(pubKeyScript.toString());
			sb.append(redeemScript.toString());
		} else {
			return "";
		}
		return sb.toString();
	}

	//check that the redeem script is the one we defined, not some other
	//TODO implement
	public boolean isRedeemScriptRightFormat() {
		return true;
	}

	//check and compare whether the lock time is still bigger than bip113 bitcoin time
	public boolean isLocked(BlockChain bc) {
		long challengeLockTime = getLockTime();
		System.out.println("challenge lock time is " + new Date(challengeLockTime*1000));
		return challengeLockTime > currentBitcoinBIP113Time(bc);
	}

	//returns current times as unix time 1.1.1970
	public static long currentBitcoinBIP113Time(BlockChain bc) {
		StoredBlock headBlock = bc.getChainHead();
		StoredBlock iteratingBlock = headBlock;
		long[] blockTimeStamps = new long[11];
		for(int i=0; i < 11; i++) {
			blockTimeStamps[i] = iteratingBlock.getHeader().getTimeSeconds();
			try {
				iteratingBlock = iteratingBlock.getPrev(bc.getBlockStore());
			} catch (BlockStoreException e) {
				e.printStackTrace();
			}
		}
		Arrays.sort(blockTimeStamps);
		System.out.println("current bitcoinbip113time yielded " + new Date(blockTimeStamps[5]*1000));
		return blockTimeStamps[5];
	}

	public long getLockTime() {
		byte[] program = redeemScript.getProgram();
		System.out.println(redeemScript.toString());
		long[] time = new long[4];
		time[0] = program[1] & 0xFF;
		time[1] = program[2] & 0xFF;
		time[2] = program[3] & 0xFf;
		time[3] = program[4] & 0xFF;
		//System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(time));		
		return time[0] | (time[1] << 8L) | (time[2] << 16L) | (time[3] << 24L);
	}

	public byte[] getPubKey() {
		return this.pubkey;
	}
	
}
