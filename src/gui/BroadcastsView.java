package gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

public class BroadcastsView extends JPanel {
	
	JScrollPane scroll;
	
	JTextArea display;


	private static final long serialVersionUID = 4319566368986221508L;
	
	public BroadcastsView() {
		super();
		GridBagConstraints gbc = new GridBagConstraints();
		GridBagLayout layout = new GridBagLayout();
		this.setLayout(layout);
		
		display = new JTextArea(16, 58);
		display.setEditable(false);
		
		scroll = new JScrollPane(display);
	    scroll.setVerticalScrollBarPolicy ( ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS );
		this.add(scroll);
		
	}

}
