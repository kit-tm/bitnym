package bitnymWallet;

/**
 * Listener to be added outside of bitnym for mixing events
 */
public interface MixingEventListener {
    void onMixAborted();

    void onMixStarted();

    void onMixFinished();
}
