package gui;

public class BroadcastsController {

	
	private BroadcastsView broadcastsView;

	public void loadView() {
		broadcastsView = new BroadcastsView();
		
		
	}
	
	public BroadcastsView getView() {
		return this.broadcastsView;
	}
}
