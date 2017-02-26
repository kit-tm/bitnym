package bitnymWallet;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.spongycastle.util.Arrays;


public class BroadcastAnnouncement {
	/**
	 * 
	 */
	//used in the bloom filter for efficient identification of bitnym broadcasts
	public static byte[] magicNumber = {(byte) 0xa0,(byte) 0x4f, (byte) 0xff, (byte) 0xac};
	private transient String onionAddress;
	private transient int mixValue;
	private int acceptableLossValue;
	
	public BroadcastAnnouncement(String oa, long l, int aLV) {
		this.onionAddress = oa;
		this.mixValue =  (int) l;
		this.acceptableLossValue = aLV;
	}

	public static boolean isBroadcastAnnouncementScript(byte[] script) {
		System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(script));
		//TODO expand this to full verification, e.g. parsing the script
		if(script[1] != 0x04) {
			return false;
		}
		for(int i=0; i<4; i++) {
			if(script[2+i] != magicNumber[i]) {
				return false;
			}
		}
		
		return true;
		
	}
	
	//constructs the identifier for the bloom filter together with the announcement data
	public Script buildScript() {
		ScriptBuilder sbuilder = new ScriptBuilder();
		sbuilder = sbuilder.op(ScriptOpCodes.OP_RETURN);
		ScriptChunk protocolIdentifier = new ScriptChunk((byte) magicNumber.length, magicNumber);
		sbuilder = sbuilder.addChunk(protocolIdentifier);
		
		byte[] data;
		ByteBuffer result = ByteBuffer.allocate(8 + this.onionAddress.getBytes().length);
		result.put(this.onionAddress.getBytes());
		result.putInt(this.mixValue);
		result.putInt(this.acceptableLossValue);
		data = result.array();
		System.out.println("length of data arary " + data.length);
		
		ScriptChunk dataChunk = new ScriptChunk(data.length, data);
		sbuilder = sbuilder.addChunk(dataChunk);
		
		return sbuilder.build();
	}
	
	//builds a broadcastannouncement from the opreturn output
	public static BroadcastAnnouncement deserialize(byte[] data) {
		byte[] onionAdress = Arrays.copyOfRange(data, 7, 23);
		byte[] mixValue = Arrays.copyOfRange(data, 23, 27);
		byte[] acceptableLoss = Arrays.copyOfRange(data, 27, 31);
		
		return new BroadcastAnnouncement(new String(onionAdress), (new BigInteger(mixValue)).intValue(), (new BigInteger(acceptableLoss)).intValue());
	}
	
	public String getOnionAdress() {
		return this.onionAddress;
	}
	

}
