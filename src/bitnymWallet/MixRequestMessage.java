package bitnymWallet;

import edu.kit.tm.ptp.Identifier;

/**
 * Message used for mix request, sent after initiate mix, receiver of this message should try to mix passive
 */
public class MixRequestMessage {
    /** no-arg constructor required for PTP.
     */
    public MixRequestMessage() {
        data = null;
    }

    public MixRequestMessage(byte[] data) {
        this.data = data;
    }

    public byte[] data;
}
