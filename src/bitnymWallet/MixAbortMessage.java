package bitnymWallet;

/**
 * Message used to inform mix partner about abort
 */
public class MixAbortMessage {
    /** no-arg constructor required for PTP.
     */
    public MixAbortMessage() {
        data = null;
    }

    public MixAbortMessage(byte[] data) {
        this.data = data;
    }

    public byte[] data;
}
