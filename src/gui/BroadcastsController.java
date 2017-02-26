package gui;

import bitnymWallet.BitNymWallet;

public class BroadcastsController {

	
	private BroadcastsView broadcastsView;
	private BitNymWallet wallet;

	public void loadView() {
		broadcastsView = new BroadcastsView();
		broadcastsView.getDisplay().setText(wallet.getBroadcastAnnouncementsString());
		
	}
	
	public BroadcastsView getView() {
		return this.broadcastsView;
	}
	

	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
	}
}
