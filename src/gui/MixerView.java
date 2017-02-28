package gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class MixerView extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6595715731798690160L;
	
	
	private JTextField onion;
	
	private JTextField currentWalletValue;
	
	private JTextField currentNymValue;
	
	private JTextField currentBip113Time;

	private JButton mixBtn;
	
	private JButton generateGenesisBtn;
	
	private JButton listenForMixBtn;
	
	private JButton deleteProofBtn;
	
	private JButton generateBroadcastBtn;

	private JButton stopListeningForMixBtn;

	private JButton mixWithRndmBroadcastBtn;


	private JTextField lockTimeField;


	private JButton listenForVerificationBtn;


	private JButton sendForVerificationBtn;


	private JTextField ourOnionAddress;
	
	public MixerView() {
		super();
		
		GridBagConstraints gbc = new GridBagConstraints();
		GridBagLayout layout = new GridBagLayout();
		this.setLayout(layout);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridwidth = 3;
		
		onion = new JTextField();
		onion.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 0;
		gbc.gridy = 0;
		this.add(onion, gbc);
		
		mixBtn = new JButton("mix");
		mixBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 0;
		this.add(mixBtn, gbc);
		
		generateGenesisBtn = new JButton("generate GenesisTX");
		generateGenesisBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 1;
		this.add(generateGenesisBtn, gbc);
		
		listenForMixBtn = new JButton("listen for Mix");
		listenForMixBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 2;
		this.add(listenForMixBtn, gbc);
		
		stopListeningForMixBtn = new JButton("Stop Listening For Mix");
		stopListeningForMixBtn.setPreferredSize(new Dimension(220, 30));
		gbc.gridx = 3;
		gbc.gridy = 3;
		this.add(stopListeningForMixBtn, gbc);
		
		currentWalletValue = new JTextField();
		currentWalletValue.setEditable(false);
		gbc.gridx = 0;
		gbc.gridy = 2;
		this.add(currentWalletValue, gbc);
		
		currentNymValue = new JTextField();
		currentNymValue.setEditable(false);
		gbc.gridx = 0;
		gbc.gridy = 3;
		this.add(currentNymValue, gbc);
		
		currentBip113Time = new JTextField();
		currentBip113Time.setEditable(false);
		gbc.gridx = 0;
		gbc.gridy = 4;
		this.add(currentBip113Time, gbc);
		
		ourOnionAddress = new JTextField();
		ourOnionAddress.setEditable(false);
		gbc.gridx = 0;
		gbc.gridy = 5;
		this.add(ourOnionAddress, gbc);
		
		deleteProofBtn = new JButton("Delete Proof Message");
		deleteProofBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 4;
		this.add(deleteProofBtn, gbc);
		
		mixWithRndmBroadcastBtn = new JButton("Mix with Random Broadcast");
		mixWithRndmBroadcastBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 5;
		this.add(mixWithRndmBroadcastBtn, gbc);
		
		generateBroadcastBtn = new JButton("Generate BroadcastTX");
		generateBroadcastBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 6;
		this.add(generateBroadcastBtn, gbc);
		
		listenForVerificationBtn = new JButton("Listen for Verification");
		listenForVerificationBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 7;
		this.add(listenForVerificationBtn, gbc);
		
		sendForVerificationBtn = new JButton("Send for Verification");
		sendForVerificationBtn.setPreferredSize(new Dimension(220,30));
		gbc.gridx = 3;
		gbc.gridy = 8;
		this.add(sendForVerificationBtn, gbc);
		
		lockTimeField = new JTextField();
		lockTimeField.setText("0");
		lockTimeField.setPreferredSize(new Dimension(220, 30));
		gbc.gridx = 0;
		gbc.gridy = 1;
		this.add(lockTimeField, gbc);
		
		
	}
	
	public JButton getMixBtn() {
		return this.mixBtn;
	}
	
	public String getOnionString() {
		return onion.getText().toString();
	}
	
	public JButton getGenGenesisBtn() {
		return this.generateGenesisBtn;
	}
	
	public JButton getListenForMixBtn() {
		return this.listenForMixBtn;
	}
	
	public JButton getStopListeningForMixBtn() {
		return this.stopListeningForMixBtn;
	}
	
	public JTextField getCurrentNymValue() {
		return this.currentNymValue;
	}
	
	public JTextField getCurrentWalletValue() {
		return this.currentWalletValue;
	}
	
	public JButton getDeleteProofBtn() {
		return this.deleteProofBtn;
	}
	
	public JButton getMixWithRndmBroadcastBtn() {
		return this.mixWithRndmBroadcastBtn;
	}
	
	public JTextField getLockTimeField() {
		return this.lockTimeField;
	}

	public JTextField getCurrentBip113Time() {
		return this.currentBip113Time;
	}
	
	public JButton getGenerateBroadcastBtn() {
		return this.generateBroadcastBtn;
	}
	
	public JButton getListenForVerificationBtn() {
		return this.listenForVerificationBtn;
	}
	
	public JButton getSendForVerificationBtn() {
		return this.sendForVerificationBtn;
	}
	
	public JTextField getOurOnionAddress() {
		return this.ourOnionAddress;
	}
}
