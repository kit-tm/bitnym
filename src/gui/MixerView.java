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

	private JButton mixBtn;
	
	private JButton generateGenesisBtn;
	
	private JButton listenForMixBtn;


	private JButton stopListeningForMixBtn;
	
	public MixerView() {
		super();
		
		GridBagConstraints gbc = new GridBagConstraints();
		GridBagLayout layout = new GridBagLayout();
		this.setLayout(layout);
		
		onion = new JTextField();
		onion.setPreferredSize(new Dimension(150,30));
		this.add(onion);
		
		mixBtn = new JButton("mix");
		mixBtn.setPreferredSize(new Dimension(80,30));
		this.add(mixBtn);
		
		generateGenesisBtn = new JButton("generate Genesis Transaction");
		generateGenesisBtn.setPreferredSize(new Dimension(80,30));
		this.add(generateGenesisBtn);
		
		listenForMixBtn = new JButton("listen for Mix");
		listenForMixBtn.setPreferredSize(new Dimension(80,30));
		this.add(listenForMixBtn);
		
		stopListeningForMixBtn = new JButton("Stop Listening For Mix");
		stopListeningForMixBtn.setPreferredSize(new Dimension(130, 30));
		this.add(stopListeningForMixBtn);
		
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
}
