package bitnymWallet;
import gui.BroadcastsController;
import gui.ConsoleController;
import gui.MixerController;
import gui.ProofController;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextField;

import org.bitcoinj.core.Transaction;


public class BitNymGui extends JFrame {
	
	private BroadcastsController bcontroller;
	private ConsoleController ccontroller;
	private MixerController mcontroller;
	private ProofController pcontroller;
	private BitNymWallet wallet;
	
	
	
	public BitNymGui(BitNymWallet wallet) {
		this.wallet = wallet;
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		
		bcontroller = new BroadcastsController();
		bcontroller.loadView();
		
		ccontroller = new ConsoleController();
		ccontroller.loadView();
		
		mcontroller = new MixerController();
		mcontroller.loadWallet(wallet);
		mcontroller.loadView();
		
		pcontroller = new ProofController();
		pcontroller.loadWallet(wallet);
		pcontroller.loadView();
		
		tabbedPane.addTab("Mixer", mcontroller.getView());
		
		tabbedPane.addTab("Proof", pcontroller.getView());
		
		tabbedPane.addTab("Broadcasts", bcontroller.getView());
		
		tabbedPane.addTab("Console", ccontroller.getView());
		
		
		getContentPane().add(tabbedPane, BorderLayout.CENTER);
		this.pack();
		
	}
	
	public void loadWalletListener() {
		//register listeners
		//move to the controllers
		wallet.addBroadcastAnnouncementChangeEventListener(new BroadcastAnnouncementChangeEventListener() {
			
			@Override
			public void onBroadcastAnnouncementChanged() {
				List<Transaction> bas = wallet.getBroadcastAnnouncements();
				bcontroller.getView().getDisplay().append(bas.get(bas.size()-1).toString());
			}
		});
		wallet.addMixFinishedEventListener(new MixFinishedEventListener() {
			
			@Override
			public void onMixFinished() {
				// TODO Auto-generated method stub
				JOptionPane.showMessageDialog(null, "Mixing erfolgreich, Aufnahme in die Blockchain" +
						"steht aus", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
				
			}
		});
		wallet.addProofConfidenceChangeEventListener(new ProofConfidenceChangeEventListener() {
			
			@Override
			public void onProofConfidenceChanged() {
				JOptionPane.showMessageDialog(null, "Mixtransaktion wurde in die Blockchain" +
						" aufgenommen", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
				//might happen that we receive to times 
//				if() {
					pcontroller.getView().getDisplay().append(wallet.getLastTransaction().toString());
//				}
			}
		});
		
		wallet.addProofChangeEventListener(new ProofChangeEventListener() {
			
			@Override
			public void onProofChanged() {
				pcontroller.getView().getDisplay().setText(wallet.getProofMessageString());
			}
		});
		wallet.addTimeChangedEventListener(new TimeChangedEventListener() {

			@Override
			public void onTimeChangedEvent() {
				mcontroller.getView().getCurrentBip113Time().setText(wallet.getCurrentBIP113Time().toString());
			}
			
		});
	}
}
