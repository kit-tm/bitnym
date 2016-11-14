import java.io.Serializable;

import org.bitcoinj.core.Address;


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

	public static boolean isBroadcastAnnouncementScript(byte[] script) {
		byte[] x = new byte[4];
		x[0] = script[1];
		x[1] = script[2];
		x[2] = script[3];
		x[3] = script[4];
		return x.equals(magicNumber);
	}

}
