package bitnymWallet;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class MainClass {
	
	
	
	private static final Logger log = LoggerFactory.getLogger(MainClass.class);
	//TODO move to utility/config class?
	public static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	public static Coin PSNYMVALUE = Coin.valueOf(200000);
	private static Coin totalOutput = PSNYMVALUE.add(PROOF_OF_BURN.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE));
	public static NetworkParameters params;
	//TODO remove these constansts, are alreay in bitnymwallet

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		final BitNymWallet bitwallet = new BitNymWallet();
		BitNymGui gui = new BitNymGui(bitwallet);
		gui.loadWalletListener();
		
		gui.setMinimumSize(new Dimension(800, 450));
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		gui.setVisible(true);
		gui.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.out.println("Starting to place eow sign.");
				bitwallet.exit();
				System.out.println("Placed eow sign.");
			}
		});
		System.out.println("Reached end of world, continuing.");

	}
	
	
	

}
