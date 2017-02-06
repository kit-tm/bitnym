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

	private JButton mixBtn;
	
	private JButton generateGenesisBtn;
	
	private JButton listenForMixBtn;
	
	private JButton deleteProofBtn;


	private JButton stopListeningForMixBtn;


	private JButton mixWithRndmBroadcastBtn;


	private JTextField lockTimeField;
	
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
}
