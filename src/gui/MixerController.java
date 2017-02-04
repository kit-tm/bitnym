package gui;

public class MixerController {

	private MixerView mixerView;

	public void loadView() {
		mixerView = new MixerView();
	}
	
	public MixerView getView() {
		return this.mixerView;
	}
}
