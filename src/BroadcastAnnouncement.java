import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.spongycastle.util.Arrays;


public class BroadcastAnnouncement implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//used in the bloom filter for efficient identification of bitnym broadcasts
	public transient static byte[] magicNumber = {(byte) 0xa0,(byte) 0x4f, (byte) 0xff, (byte) 0xac};
//	private Address address;
//	private String onionAddress;
//	private int mixValue;
//	private int acceptableLossValue;
	
	public BroadcastAnnouncement() {
		
	}
//	
//	public BroadcastAnnouncement(Address a, String oa, int mV, int aLV) {
//		this.address = a;
//		this.onionAddress = oa;
//		this.mixValue = mV;
//		this.acceptableLossValue = aLV;
//	}

	//TODO deserialize and check magicNumber
	public static boolean isBroadcastAnnouncementScript(byte[] script) {
		System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(script));
		//TODO expand this to full verification, e.g. parsing the script
		if(script[1] == 0x04) {
			for(int i=0; i<4; i++) {
				if(script[2+i] != magicNumber[i]) {
					return false;
				}
			}
		}
		
		
		return true;
		
	}
	
	public Script buildScript() {
		ScriptBuilder sbuilder = new ScriptBuilder();
		sbuilder = sbuilder.op(ScriptOpCodes.OP_RETURN);
		ScriptChunk protocolIdentifier = new ScriptChunk((byte) magicNumber.length, magicNumber);
		sbuilder = sbuilder.addChunk(protocolIdentifier);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		ScriptChunk dataChunk = new ScriptChunk(baos.toByteArray().length, baos.toByteArray());
		sbuilder = sbuilder.addChunk(dataChunk);
		
		return sbuilder.build();
	}

}
