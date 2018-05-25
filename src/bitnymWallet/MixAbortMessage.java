package bitnymWallet;

/**
 * Message used for mix request, sent after initiate mix, receiver of this message should try to mix passive
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
