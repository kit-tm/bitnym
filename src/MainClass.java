import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
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
import org.bitcoinj.core.TransactionConfidence.Listener.ChangeReason;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

import edu.kit.tm.ptp.PTP;




public class MainClass {
	
	private static final Logger log = LoggerFactory.getLogger(MainClass.class);
	private static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	private static Coin PSNYMVALUE = Coin.valueOf(200000);
	private static Coin totalOutput = PSNYMVALUE.add(PROOF_OF_BURN.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE));
	public static NetworkParameters params;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//initialize neccessary bitcoinj variables
		MainClass.params = TestNet3Params.get();
		
		log.info("testTestTest");
		final ProofMessage pm = new ProofMessage();

		PTP ptp = new PTP(System.getProperty("user.dir"));
		try {
			//TODO move ptp to mixer
			log.info("initiate ptp and create hidden service");
			ptp.init();
			ptp.createHiddenService();
			System.out.println(ptp.getIdentifier().getTorAddress());
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
		wallet.autosaveToFile(f, 30, TimeUnit.SECONDS, null);
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
		
		log.info("this is the current proof chain");
		log.info(pm.toString());
		//don't use orchid, seems not maintained, and last time checked the dirauth keys were outdated ...
		System.setProperty("socksProxyHost", "127.0.0.1");
		System.setProperty("socksProxyPort", "9050");
		final PeerGroup pg = new PeerGroup(params, bc, new BlockingClientManager());
		pg.addWallet(wallet);
		
		//TODO DNS through Tor without Orchid, as it is not maintained
		pg.addPeerDiscovery(new DnsDiscovery(params));
		System.out.println("download chain");
		pg.start();		
		pg.downloadBlockChain();
		
		//insert into new peers the peer identifier into their bloomfilter,
		//unfortunately it isn't possibly to insert it only a single time into the wallet
		//but instead we update it in every peer
		//final BloomFilter filter = new BloomFilter(100, 0.05, 0);
		//filter.insert(BroadcastAnnouncement.magicNumber);
		pg.addConnectedEventListener(new PeerConnectedEventListener() {
			
			@Override
			public void onPeerConnected(Peer arg0, int arg1) {
				arg0.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);			
			}
		});
		for(Peer p : pg.getConnectedPeers()) {
			p.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		}
		//Peer downloadpeer = pg.getDownloadPeer();
		
		
		//downloadpeer.setBloomFilter(filter);
		//downloadpeer.getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		javax.swing.Timer t = new javax.swing.Timer( 1000, new ActionListener() {
			  public void actionPerformed( ActionEvent e ) {
			    pg.getDownloadPeer().getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
			    //pg.getDownloadPeer().setBloomFilter(filter);
			  }
			});
		t.start();
		System.out.println("bloom filter assertion");
	    //pg.getDownloadPeer().setBloomFilter(filter);
		log.info("insert our broadcast transaction identifier string, into bloom filter of current download peer");
	    pg.getDownloadPeer().getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
		
		
		log.info("Current ESTIMATED balance: " + wallet.getBalance(BalanceType.ESTIMATED).toFriendlyString());
		log.info("Current AVAILABLE balance: " + wallet.getBalance().toFriendlyString());
		System.out.println(wallet.currentReceiveAddress().toBase58());
		if(wallet.getBalance(BalanceType.AVAILABLE).isLessThan(totalOutput)) {
			//use faucet to get some coins
			try {
				System.out.println("sleep for 10minutes");
				TimeUnit.MINUTES.sleep(15);
			} catch (InterruptedException e2) {
				//TODO Auto-generated catch block
				e2.printStackTrace();
			}
		}
		log.info("Current AVAILABLE balance: " + wallet.getBalance().toFriendlyString());

		
		
		try {
			wallet.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		MixPartnerDiscovery mpd = new MixPartnerDiscovery(params, pg, bc, wallet);
		//bc.addNewBestBlockListener(mpd);
		
		pg.addBlocksDownloadedEventListener(mpd);
		System.out.println("addblocksdownloadedeventlistener");
		
//		try {
//			System.out.println("sendBroadcastAnnouncement");
//			MixPartnerDiscovery.sendBroadcastAnnouncement(params, wallet, new BroadcastAnnouncement(ptp.getIdentifier().getTorAddress(), 10, 10), f, pm);
//		} catch (InsufficientMoneyException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		try {
//			TimeUnit.MINUTES.sleep(15);
//		} catch (InterruptedException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		Transaction genesisTx;
		try {
			//generate genesis transaction if our proof is empty
			if(pm.getValidationPath().size() == 0 && wallet.getBalance(BalanceType.AVAILABLE).isGreaterThan(PSNYMVALUE)) {
				genesisTx = generateGenesisTransaction(params, pg, wallet, pm, f);
				//TODO register listener before sending tx out, to avoid missing a confidence change
				genesisTx.getConfidence().addEventListener(new Listener() {

					@Override
					public void onConfidenceChanged(TransactionConfidence arg0,
							ChangeReason arg1) {
						if (arg0.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING) {
							return;
						}
						if(arg0.getDepthInBlocks() == 1) {
							//enough confidence, write proof message to the file system
							System.out.println("depth of genesis tx is now 1, consider ready for usage");
						}
					}
				});
			}
		} catch (InsufficientMoneyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));

		//sanity check, that the protocol identifier isn't overwritten by a new bloom filter etc
		try {
			for(int i=0; i<50;i++) {
		//		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
				//if(!mpd.hasBroadcasts() || pm.isEmpty()) {
				if(pm.isEmpty() || pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {	
					TimeUnit.MINUTES.sleep(1);
				} else {
					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					// Ask for the destination hidden service identifier
				    System.out.println("Enter destination identifier: ");
				    String destinationAddress = null;
				    try {
						destinationAddress = br.readLine();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					//Mixer m = new Mixer(ptp, mpd.getMixpartner(), pm, wallet, params);
					Mixer m = new Mixer(ptp, destinationAddress, pm, wallet, params, pg);

					m.initiateMix();
					TimeUnit.MINUTES.sleep(10);
					break;
				}
			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			TimeUnit.MINUTES.sleep(5);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		
		ptp.exit();
		pg.stop();
	}
	
	
	//TODO refactor this out into an seperate class, and split into generating transaction
	// and sending of the transaction
	private static Transaction generateGenesisTransaction(NetworkParameters params, PeerGroup pg, Wallet w, ProofMessage pm, File f) throws InsufficientMoneyException {
		log.info("try generating genesis tx");
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
		w.importKey(changeKey);
//		tx.addOutput(new TransactionOutput(params, tx, suffInptValue.minus(totalOutput), changeAdrs));	
		try {
			log.info("verify the transaction");
			tx.verify();
		} catch (VerificationException e) {
			e.printStackTrace();
		}
		
		
		
		SendRequest req = SendRequest.forTx(tx);
		req.changeAddress = changeAdrs;
		req.shuffleOutputs = false;
		req.signInputs = true;
		w.completeTx(req);
		w.commitTx(req.tx);
		try {
			w.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//Wallet.SendResult result = w.sendCoins(req);
		TransactionBroadcast a = pg.broadcastTransaction(req.tx);
		ListenableFuture<Transaction> future = a.broadcast();
		try {
			Transaction b = future.get();
			pm.addTransaction(b,1);
			pm.writeToFile();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		try {
//			result.broadcastComplete.get();
//			System.out.println("broadcast complete");
//			pm.addTransaction(result.tx, 1);
//			pm.writeToFile();
//			System.out.println("added genesis tx to proof message data structure and file");
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (ExecutionException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		log.info("genereated genesis tx");
		return tx;
	}

}
