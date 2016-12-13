import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
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
import org.bitcoinj.core.Address;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import edu.kit.tm.ptp.PTP;




public class MainClass {
	
	
	private static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	private static Coin PSNYMVALUE = Coin.valueOf(200000);
	private static Coin totalOutput = PSNYMVALUE.add(PROOF_OF_BURN.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE));

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//initialize neccessary bitcoinj variables
		final NetworkParameters params = TestNet3Params.get();
		ProofMessage pm = new ProofMessage();
		
		
		PTP ptp = new PTP(System.getProperty("user.dir"));
		try {
			ptp.init();
			ptp.createHiddenService();
		} catch (IOException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
		Wallet wallet = null;
		File f = new File("./wallet.wa");
		if(!f.exists()) {
			wallet = new Wallet(params);
			try {
				wallet.saveToFile(f);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				wallet = Wallet.loadFromFile(f, null);
			} catch (UnreadableWalletException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		File bs = new File("./blockstore.bc");
		wallet.autosaveToFile(f, 2, TimeUnit.MINUTES, null);
		SPVBlockStore spvbs = null;
		BlockChain bc = null;
		try {
			spvbs = new SPVBlockStore(params, bs);
		} catch (BlockStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			bc = new BlockChain(params, wallet, spvbs);
		} catch (BlockStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		//don't use orchid, seems not maintained, and last time checked the dirauth keys were outdated ...
		System.setProperty("socksProxyHost", "127.0.0.1");
		System.setProperty("socksProxyPort", "9050");
		PeerGroup pg = new PeerGroup(params, bc, new BlockingClientManager());
		pg.addWallet(wallet);
		
		//TODO DNS through Tor without Orchid, as it is not maintained
		pg.addPeerDiscovery(new DnsDiscovery(params));
		System.out.println("download chain");
		pg.start();		
		pg.downloadBlockChain();
		
		//insert into new peers the peer identifier into their bloomfilter,
		//unfortunately it isn't possibly to insert it only a single time into the wallet
		//but instead we update it in every peer
		pg.addConnectedEventListener(new PeerConnectedEventListener() {
			
			@Override
			public void onPeerConnected(Peer arg0, int arg1) {
				arg0.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);				
			}
		});
		for(Peer p : pg.getConnectedPeers()) {
			p.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		}
		Peer downloadpeer = pg.getDownloadPeer();
		downloadpeer.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		assert(downloadpeer.getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
		
		
		
		System.out.println(wallet.currentReceiveAddress().toBase58());
		if(wallet.getBalance().isLessThan(totalOutput)) {
			//use faucet to get some coins
			try {
				System.out.println("sleep for 10minutes");
				TimeUnit.MINUTES.sleep(10);
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		}
		
		System.out.println("Current balance: " + wallet.getBalance().toFriendlyString());
		try {
			wallet.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		MixPartnerDiscovery mpd = new MixPartnerDiscovery(params, pg, bc);
		//bc.addNewBestBlockListener(mpd);
		
		pg.addBlocksDownloadedEventListener(mpd);
		
		try {
			System.out.println("sendBroadcastAnnouncement");
			MixPartnerDiscovery.sendBroadcastAnnouncement(params, wallet, new BroadcastAnnouncement(ptp.getIdentifier().getTorAddress(), 10, 10));
		} catch (InsufficientMoneyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		try {
//			generateGenesisTransaction(params, pg, wallet);
//		} catch (InsufficientMoneyException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		assert(downloadpeer.getBloomFilter().contains(BroadcastAnnouncement.magicNumber));

		//sanity check, that the protocol identifier isn't overwritten by a new bloom filter etc
		try {
			for(int i=0; i<15;i++) {
				assert(downloadpeer.getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
				TimeUnit.MINUTES.sleep(1);
			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Mixer m = new Mixer(ptp, mpd.getMixpartner(), pm, wallet, params);
		m .initiateMix();
		
		
		pg.stop();
	}
	
	
	//TODO refactor this out into an seperate class, and split into generating transaction
	// and sending of the transaction
	private static void generateGenesisTransaction(NetworkParameters params, PeerGroup pg, Wallet w, ProofMessage pm) throws InsufficientMoneyException {
		Transaction tx = new Transaction(params);
		byte[] opretData = "xxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
		//wallet Balance is not sufficient
		if (w.getBalance().isLessThan(PROOF_OF_BURN)) {
			throw new InsufficientMoneyException(PROOF_OF_BURN.minus(w.getBalance()));
		}

		
		
		//add marker output
		tx.addOutput(PROOF_OF_BURN, ScriptBuilder.createOpReturnScript(opretData));
		
		//add pseudonym output
		ECKey psnymKey = new ECKey();
		Address nymAdrs = new Address(params, psnymKey.getPubKeyHash());
		w.importKey(psnymKey);
		
		Coin suffInptValue = Coin.ZERO;
		
		
		List<TransactionOutput> unspents = w.getUnspents();
				
		Iterator<TransactionOutput> iterator = unspents.iterator();
		//TODO use only certain inputs, if so why use certain inputs?
		//TODO use CoinSelector instead
		while(suffInptValue.isLessThan(totalOutput) && iterator.hasNext()) {
			TransactionOutput next = iterator.next();
			suffInptValue = suffInptValue.add(next.getValue());
			tx.addInput(next);
		}
				
		tx.addOutput(new TransactionOutput(params, tx, PSNYMVALUE, nymAdrs));
		
		//TODO add change, for know we add everything except PoB and fees to the psnym
		ECKey changeKey = new ECKey();
		Address changeAdrs = new Address(params, changeKey.getPubKeyHash());
//		tx.addOutput(new TransactionOutput(params, tx, suffInptValue.minus(totalOutput), changeAdrs));	
		try {
			System.out.println("verify the transaction");
			tx.verify();
		} catch (VerificationException e) {
			e.printStackTrace();
		}
		
		SendRequest req = SendRequest.forTx(tx);
		pm.addTransaction(tx, 1);
		req.changeAddress = changeAdrs;
		req.shuffleOutputs = false;
		req.signInputs = true;
		Wallet.SendResult result = w.sendCoins(req);
		try {
			result.broadcastComplete.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
