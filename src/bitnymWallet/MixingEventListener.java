package bitnymWallet;

/**
 * Listener to be added outside of bitnym for mixing events
 */
public interface MixingEventListener {
    void onMixAborted(int errorCode);

    void onMixStarted();

    void onMixFinished();
}
