package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JOptionPane;

import bitnymWallet.BitNymWallet;
import bitnymWallet.NoBroadcastAnnouncementsException;

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
				wallet.mixWith(mixerView.getOnionString(), Integer.parseInt(mixerView.getLockTimeField().getText())*60);
				mixerView.getCurrentNymValue().setText(wallet.getPsynymValue());
				mixerView.getCurrentWalletValue().setText(wallet.getWallet().getBalance().toFriendlyString());
			}
		});
		mixerView.getGenGenesisBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.generateGenesisTransaction(Integer.parseInt(mixerView.getLockTimeField().getText())*60);
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
		
		mixerView.getDeleteProofBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wallet.deleteProof();
			}
		});
		
		mixerView.getMixWithRndmBroadcastBtn().addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				//TODO insert wallet.hasBroadcasts function so that caller can check,
				//before calling
				try {
					wallet.mixWithRandomBroadcast(Integer.parseInt(mixerView.getLockTimeField().getText())*60);
				} catch (NoBroadcastAnnouncementsException e) {
					JOptionPane.showMessageDialog(null, "Keine BroadcastsAnnouncements zum " +
							"mixen", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
					e.printStackTrace();
				}
			}
		});
		mixerView.getCurrentBip113Time().setText(wallet.getCurrentBIP113Time().toString());
	}
	
	//use this before loadView
	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
	}
	
	public MixerView getView() {
		return this.mixerView;
	}
}
