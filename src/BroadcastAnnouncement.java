import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;

import org.bitcoinj.core.Address;
import org.spongycastle.util.Arrays;


public class BroadcastAnnouncement implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static byte[] magicNumber = {(byte) 0xa0,(byte) 0x4f, (byte) 0xff, (byte) 0xac};
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
		byte[] minusOpReturn = Arrays.copyOfRange(script, 3, script.length);
		System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(minusOpReturn));
		ByteArrayInputStream bais = new ByteArrayInputStream(minusOpReturn);
		try {
			ObjectInputStream ois = new ObjectInputStream(bais);
			Object bca = ois.readObject();
			if (bca instanceof BroadcastAnnouncement) {
				return true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
		
	}

}
