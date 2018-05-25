package bitnymWallet;

/**
 *
 */
public class SendProofMessage {
    /** no-arg constructor required for PTP.
     */
    public SendProofMessage() {
        data = null;
    }

    public SendProofMessage(byte[] data) {
        this.data = data;
    }

    public byte[] data;
}
