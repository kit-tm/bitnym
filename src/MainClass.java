import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.Listener;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

import edu.kit.tm.ptp.PTP;




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
		BitNymGui gui = new BitNymGui();
		gui.loadWallet(bitwallet);
		
		gui.setMinimumSize(new Dimension(800, 450));
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		gui.setVisible(true);
		gui.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				bitwallet.exit();
			}
		});

	}
	
	
	

}
