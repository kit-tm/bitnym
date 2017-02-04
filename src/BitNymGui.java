import gui.BroadcastsController;
import gui.ConsoleController;
import gui.MixerController;
import gui.ProofController;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;


public class BitNymGui extends JFrame {
	
	BroadcastsController bcontroller;
	ConsoleController ccontroller;
	MixerController mcontroller;
	ProofController pcontroller;
	private BitNymWallet wallet;
	
	
	
	public BitNymGui() {
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		
		bcontroller = new BroadcastsController();
		bcontroller.loadView();
		
		ccontroller = new ConsoleController();
		ccontroller.loadView();
		
		mcontroller = new MixerController();
		mcontroller.loadView();
		
		pcontroller = new ProofController();
		pcontroller.loadView();
		
		tabbedPane.addTab("Mixer", mcontroller.getView());
		
		tabbedPane.addTab("Proof", pcontroller.getView());
		
		tabbedPane.addTab("Broadcasts", bcontroller.getView());
		
		tabbedPane.addTab("Console", ccontroller.getView());
		
		
		getContentPane().add(tabbedPane, BorderLayout.CENTER);
		this.pack();
		
	}
	
	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
		
		//register listeners
	}
}
