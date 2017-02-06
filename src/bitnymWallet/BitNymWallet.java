package bitnymWallet;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.Listener;
import org.bitcoinj.core.TransactionConfidence.Listener.ChangeReason;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.tm.ptp.PTP;


public class BitNymWallet {
	
	//TODO check before mixing that proof passed the bitcoin bip113 time, so that it is free to be spend
	
	private static final Logger log = LoggerFactory.getLogger(BitNymWallet.class);
	public static Coin PSNYMVALUE = Coin.valueOf(200000);
	public static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	private static Coin totalOutput = PSNYMVALUE.add(PROOF_OF_BURN.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE));

	
	
	
	private NetworkParameters params;
	private PeerGroup pg;
	private PTP ptp;
	private TransactionGenerator tg;
	private File walletFile;
	private Wallet wallet;
	private ProofMessage pm;
	private SPVBlockStore spvbs;
	private File bs;
	private BlockChain bc;
	private MixPartnerDiscovery mpd;
	private Mixer m;
	//TODO move all listeners back to this class 
	private List<ProofConfidenceChangeEventListener> proofChangeConfidenceListeners;
	private List<ProofChangeEventListener> proofChangeListeners;
	private List<BroadcastAnnouncementChangeEventListener> baListeners;
	private List<MixFinishedEventListener> mfListeners;

	//TODO refactoring: extend bitcoinj wallet and remove wallet field, pass this to functions which need wallet
	
	
	public BitNymWallet() {
		MainClass.params = TestNet3Params.get();
		params = TestNet3Params.get();

		proofChangeConfidenceListeners = new ArrayList<ProofConfidenceChangeEventListener>();
		proofChangeListeners = new ArrayList<ProofChangeEventListener>();


		ptp = new PTP(System.getProperty("user.dir"));
		try {
			//TODO move ptp to mixer
			log.info("initiate ptp and create hidden service");
			ptp.init();
			ptp.createHiddenService();
			System.out.println(ptp.getIdentifier().getTorAddress());
		} catch (IOException e3) {
			e3.printStackTrace();
		}
		wallet = null;
		walletFile = new File("./wallet.wa");
		if(!walletFile.exists()) {
			wallet = new Wallet(params);
			try {
				wallet.saveToFile(walletFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				wallet = Wallet.loadFromFile(walletFile, null);
			} catch (UnreadableWalletException e) {
				e.printStackTrace();
			}
		}
		bs = new File("./blockstore.bc");
		wallet.autosaveToFile(walletFile, 30, TimeUnit.SECONDS, null);
		spvbs = null;
		bc = null;
		try {
			//call spvblockstore with capacity argument, when psynyms are
			//going to be used which are going to be older than 34 days, default value is
			//5000
			spvbs = new SPVBlockStore(params, bs);
		} catch (BlockStoreException e) {
			e.printStackTrace();
		}
		try {
			bc = new BlockChain(params, wallet, spvbs);
		} catch (BlockStoreException e) {
			e.printStackTrace();
		}

		log.info("this is the current proof chain");
		pm = new ProofMessage();
		log.info(pm.toString());
		//don't use orchid, seems not maintained, and last time checked the dirauth keys were outdated ...
		System.setProperty("socksProxyHost", "127.0.0.1");
		System.setProperty("socksProxyPort", "9050");
		pg = new PeerGroup(params, bc, new BlockingClientManager());
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
		//		javax.swing.Timer t = new javax.swing.Timer( 1000, new ActionListener() {
		//			  public void actionPerformed( ActionEvent e ) {
		//			    pg.getDownloadPeer().getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		//			    //pg.getDownloadPeer().setBloomFilter(filter);
		//			    //try setbloomfilter(getbloomfilter.insert(magicnumber), cause setbloomfilter causes resend, insert doesn't causes resend
		//			  }
		//			});
		//		t.start();
		System.out.println("bloom filter assertion");
		//pg.getDownloadPeer().setBloomFilter(filter);
		log.info("insert our broadcast transaction identifier string, into bloom filter of current download peer");
		pg.getDownloadPeer().getBloomFilter().insert(BroadcastAnnouncement.magicNumber);
		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));


		log.info("Current ESTIMATED balance: " + wallet.getBalance(BalanceType.ESTIMATED).toFriendlyString());
		log.info("Current AVAILABLE balance: " + wallet.getBalance().toFriendlyString());
		System.out.println(wallet.currentReceiveAddress().toBase58());


		log.info("Current AVAILABLE balance: " + wallet.getBalance().toFriendlyString());



		try {
			wallet.saveToFile(walletFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		mpd = new MixPartnerDiscovery(params, pg, bc, wallet);
		//bc.addNewBestBlockListener(mpd);

		pg.addBlocksDownloadedEventListener(mpd);
		System.out.println("addblocksdownloadedeventlistener");


		tg = new TransactionGenerator(params, pg, wallet);
		
		m = new Mixer(ptp, pm, wallet, params, pg, bc);


		//assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));

		//sanity check, that the protocol identifier isn't overwritten by a new bloom filter etc

	}
	
	
	
	public void exit() {
		ptp.exit();
		pg.stop();
	}
	
	
	public void generateGenesisTransaction(int lockTime) {
		Transaction genesisTx;
		try {
			//generate genesis transaction if our proof is empty
			if(pm.getValidationPath().size() == 0 && wallet.getBalance(BalanceType.AVAILABLE).isGreaterThan(PSNYMVALUE)) {
				genesisTx = tg.generateGenesisTransaction(pm, walletFile, lockTime);
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
			e.printStackTrace();
		}
	}
	
	public void sendBroadcastAnnouncement(int lockTime) {
		try {
			System.out.println("sendBroadcastAnnouncement");
			for(int i=0; i<50;i++) {
				if(pm.isEmpty() || pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {
					try {
						TimeUnit.MINUTES.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					tg.sendBroadcastAnnouncement(new BroadcastAnnouncement(ptp.getIdentifier().getTorAddress(), 10, 10), walletFile, pm, lockTime);
				}
			}
		} catch (InsufficientMoneyException e) {
			e.printStackTrace();
		}
		try {
			TimeUnit.MINUTES.sleep(15);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	public void mixWith(String onionAdress, int lockTime) {
		//TODO throw exception on not fulfilled preconditions
		try {
			for(int i=0; i<50;i++) {
				//		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
				//if(!mpd.hasBroadcasts() || pm.isEmpty()) {
				if(pm.isEmpty() || pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {	
					TimeUnit.MINUTES.sleep(1);
				} else {
					m.setMixPartnerAdress(onionAdress);
					assert(lockTime >= 0);
					m.setLockTime(lockTime);
					m.initiateMix();
					//TODO remove this
					TimeUnit.MINUTES.sleep(10);
					break;
				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
	}
	
	public void mixWithRandomBroadcast(int lockTime) throws NoBroadcastAnnouncementsException {
		try {
			for(int i=0; i<50;i++) {
				//		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
				//if(!mpd.hasBroadcasts() || pm.isEmpty()) {
				if(pm.isEmpty() || pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {	
					TimeUnit.MINUTES.sleep(1);
				} else {
					m.setBroadcastAnnouncement(mpd.getRandomBroadcast());
					assert(lockTime >= 0);
					m.setLockTime(lockTime);
					m.initiateMix();
					//TODO remove this
					TimeUnit.MINUTES.sleep(10);
					break;
				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
	}
	
	public void mixWith(BroadcastAnnouncement ba) {
		
	}
	
	public String getProofMessageString() {
		return this.pm.toString();
	}
	
	public List<Transaction> getValidationPath() {
		return pm.getValidationPath();
	}
	
	public Address getCurrentReceiveAdress() {
		return wallet.currentReceiveAddress();
	}
	
	public List<Transaction> getBroadcastAnnouncements() {
		return this.mpd.getBroadcastAnnouncements();
	}
	
	public void addProofConfidenceChangeEventListener(ProofConfidenceChangeEventListener listener) {
		assert(proofChangeConfidenceListeners != null);
		proofChangeConfidenceListeners.add(listener);
		pm.addProofChangeEventListener(listener);
	}
	
	public void addProofChangeEventListener(ProofChangeEventListener listener) {
		proofChangeListeners.add(listener);
	}
	
	
	public void addBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener listener) {
		mpd.addBroadcastAnnouncementChangeEventListener(listener);
	}
	
	public void addMixFinishedEventListener(MixFinishedEventListener listener) {
		m.addMixFinishedEventListener(listener);
	}
	
	public void removeProofConfidenceChangeEventListener(ProofConfidenceChangeEventListener listener) {
		pm.removeProofChangeEventListener(listener);
	}
	
	public void removeProofChangeEventListener(ProofChangeEventListener listener) {
		proofChangeListeners.remove(listener);
	}
	
	public void removeBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener listener) {
		mpd.removeBroadcastAnnouncementChangeEventListener(listener);
	}
	
	public void removeMixFinishedEventListener(MixFinishedEventListener listener) {
		m.removeMixFinishedEventListener(listener);
	}



	public Transaction getLastTransaction() {
		return this.pm.getLastTransaction();
	}



	public void listenForMix() {
		m.listenForMix();
	}
	
	public void stopListeningForMix() {
		m.closeListeningForMix();
	}
	
	public Wallet getWallet() {
		return this.wallet;
	}



	public String getPsynymValue() {
		if(pm.getLastTransaction() != null) {
			return pm.getLastTransactionOutput().getValue().toFriendlyString();
		} else {
			return "";
		}
	}
	
	public void deleteProof() {
		try {
			Files.delete(Paths.get(pm.getFilePath()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		pm = new ProofMessage();
		m.setOwnProof(pm);
		for(ProofChangeEventListener l : proofChangeListeners) {
			l.onProofChanged();
		}
	}

}
