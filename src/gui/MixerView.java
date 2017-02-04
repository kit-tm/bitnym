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
	
	
	JTextField onion;

	JButton mixBtn;
	
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
	}
}
