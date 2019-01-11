package bitnymWallet;

public interface MixAbortEventListener {

    public void onMixAborted(Mixer.AbortCode errorCode);

}
