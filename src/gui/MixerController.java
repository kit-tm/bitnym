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
			}
		});
		mixerView.getGenGenesisBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.generateGenesisTransaction();
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
	}
	
	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
	}
	
	public MixerView getView() {
		return this.mixerView;
	}
}
