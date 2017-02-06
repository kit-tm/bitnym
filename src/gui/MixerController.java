package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import bitnymWallet.BitNymWallet;

public class MixerController {

	private MixerView mixerView;
	private BitNymWallet wallet;
	
	public MixerController() {
	}

	public void loadView() {
		mixerView = new MixerView();
		mixerView.getMixBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.mixWith(mixerView.getOnionString());
				mixerView.getCurrentNymValue().setText(wallet.getPsynymValue());
				mixerView.getCurrentWalletValue().setText(wallet.getWallet().getBalance().toFriendlyString());
			}
		});
		mixerView.getGenGenesisBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.generateGenesisTransaction();
				mixerView.getCurrentNymValue().setText(wallet.getPsynymValue());
				mixerView.getCurrentWalletValue().setText(wallet.getWallet().getBalance().toFriendlyString());
			}
		});
		
		mixerView.getListenForMixBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.listenForMix();				
			}
		});
		
		mixerView.getStopListeningForMixBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.stopListeningForMix();
			}
		});
		
		mixerView.getCurrentWalletValue().setText(wallet.getWallet().getBalance().toFriendlyString());
		
		mixerView.getCurrentNymValue().setText(wallet.getPsynymValue());
	}
	
	//use this before loadView
	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
	}
	
	public MixerView getView() {
		return this.mixerView;
	}
}
