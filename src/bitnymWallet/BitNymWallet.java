package bitnymWallet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import edu.kit.tm.ptp.Identifier;
import edu.kit.tm.ptp.MessageReceivedListener;
import org.bitcoinj.core.*;
import org.bitcoinj.core.TransactionConfidence.Listener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.tm.ptp.PTP;


public class BitNymWallet {

	// TODO(SF): Clean up all this ptp.receive(byte[]) stuff and use seperate classes for it

	//TODO check before mixing that proof passed the bitcoin bip113 time, so that it is free to be spend
	//TODO call timechangedevent listeners on new bestblock or reorganize
	
	private static final Logger log = LoggerFactory.getLogger(BitNymWallet.class);
	public static Coin PSNYMVALUE = Coin.valueOf(90000000);
	public static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	//private static Coin totalOutput = PSNYMVALUE.add(PROOF_OF_BURN.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE));

	private final static String FILENAME_PEERS = "ptp-bitnym/config/bitcoin.peers";

	
	private NetworkParameters params;
	private Context context;
	private PeerGroup pg;
	PTP ptp;
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
	private List<TimeChangedEventListener> timeChangedListeners;
	private List<MixingEventListener> mixListeners;
	private List<WaitForDataListener> waitForDataListeners;
	private ChallengeResponseVerifier crv;

	private BroadcastAnnouncementChangeEventListener broadcastListener;
	private TransactionGeneratorListener tgListener;

	/** time in minutes a broadcast is valid
	 *
	 */
	private final int BROADCAST_TIME = 10;

	// from BitNymWrapper
	/**
	 * true if listener added to mixer, false otherwise. Set to false on re initialising after successful mixing
	 */
	private boolean listenerAdded = false;

	/**
	 * true if mixing finished or aborted, Timer will stop
	 */
	private boolean stopTimeout = false;

	//TODO refactoring: extend bitcoinj wallet and remove wallet field, pass this to functions which need wallet
	
	
	public BitNymWallet() {
		//MainClass.params = TestNet3Params.get();
		MainClass.params = RegTestParams.get();
		params = MainClass.params;
		// TODO(PM) use context as parameter at certain methods to avoid bitcoinj warnings
		context = new Context(params);
		proofChangeConfidenceListeners = new ArrayList<ProofConfidenceChangeEventListener>();
		proofChangeListeners = new ArrayList<ProofChangeEventListener>();
		timeChangedListeners = new ArrayList<TimeChangedEventListener>();
		mixListeners = new ArrayList<MixingEventListener>();
		waitForDataListeners = new ArrayList<WaitForDataListener>();

		//ptp = new PTP(System.getProperty("user.dir"), 9051, 9050);
		restartPTP();

		// register classes for ptp used in Mixer
		ptp.registerClass(MixRequestMessage.class);
		ptp.registerClass(SendProofMessage.class);
		ptp.registerClass(MixAbortMessage.class);
		ptp.registerClass(TestMessage.class);
		ptp.setReceiveListener(TestMessage.class, new MessageReceivedListener<TestMessage>() {

			@Override
			public void messageReceived(TestMessage message, Identifier source) {
				System.out.println("TestMessage received from " + source.getTorAddress());
			}
		});

		wallet = null;
		walletFile = new File("./wallet.wa");
		if(!walletFile.exists()) {
			wallet = new Wallet(context);
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
			bc = new BlockChain(context, wallet, spvbs);
		} catch (BlockStoreException e) {
			e.printStackTrace();
		}

		log.info("this is the current proof chain");
		pm = new ProofMessage();
		log.info(pm.toString());
//		try {
//			PrintWriter out = new PrintWriter("prooftextfile2.txt");
//			out.println(pm.toString());
//			out.close();
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		//don't use orchid, seems not maintained, and last time checked the dirauth keys were outdated ...
		//System.setProperty("socksProxyHost", "127.0.0.1");
		//System.setProperty("socksProxyPort", "9050");
		pg = new PeerGroup(context, bc, new BlockingClientManager());
		pg.addWallet(wallet);

		//TODO DNS through Tor without Orchid, as it is not maintained
		//pg.addPeerDiscovery(new DnsDiscovery(params));

		// Load peers from file
		if (!Files.exists(Paths.get(FILENAME_PEERS))) {
			System.err.println("File with bitcoin peers ('" + FILENAME_PEERS + "') does not exist."
					+ "Create it with one peer IP address per line. You need at least 2 peers.");
			System.exit(50);
		}
		List<String> peers;
		try {
			peers = Files.readAllLines(Paths.get(FILENAME_PEERS), StandardCharsets.UTF_8);
			if (peers.size() < 2) {
				System.err.println("Error reading \"" + FILENAME_PEERS + "\": Not enough peers");
				System.exit(51);
			}
			for (String peer : peers) {
				try {
					pg.addAddress(InetAddress.getByName(peer));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e2) {
			e2.printStackTrace();
			System.exit(52);
		}
		// 2 peers are enough
		pg.setMaxConnections(5);

		/*
		// TODO(SF): Remove failing peers
		pg.addDisconnectedEventListener(new PeerDisconnectedEventListener() {

			@Override
			public void onPeerDisconnected(Peer peer, int peerCount) {
				pg.remove
			}
		});*/

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
			public void onPeerConnected(Peer peer, int peerCount) {
				BloomFilter filter = peer.getBloomFilter();
				filter.insert(BroadcastAnnouncement.magicNumber);			
				peer.setBloomFilter(filter);
			}
		});
		insertIdentifierIntoFilter();
//		javax.swing.Timer t = new javax.swing.Timer( 1000, new ActionListener() {
//			@Override
//			  public void actionPerformed( ActionEvent e ) {
//				assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
//			  }
//			});
//		t.start();
		System.out.println("bloom filter assertion");
		//pg.getDownloadPeer().setBloomFilter(filter);
		log.info("insert our broadcast transaction identifier string, into bloom filter of current download peer");
		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));

		System.out.println("Current receive address: " + wallet.currentReceiveAddress().toBase58());

		log.info("Current ESTIMATED balance: " + wallet.getBalance(BalanceType.ESTIMATED).toFriendlyString());
		log.info("Current AVAILABLE balance: " + wallet.getBalance().toFriendlyString());
		
		/*
		// Send all coins back to faucet
		Address targetAddress = new Address(params, "mwCwTceJvYV27KXBc3NJZys6CjsgsoeHmf");
		try {
			wallet.sendCoins(SendRequest.emptyWallet(targetAddress));
		} catch (InsufficientMoneyException e) {
			e.printStackTrace();
		}
		*/


		try {
			wallet.saveToFile(walletFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		mpd = new MixPartnerDiscovery(params, pg, bc, wallet);
		//bc.addNewBestBlockListener(mpd);

		pg.addBlocksDownloadedEventListener(mpd);
		System.out.println("addblocksdownloadedeventlistener");


		tg = new TransactionGenerator(context, pg, wallet, bc);
		
		m = new Mixer(this, pm, wallet, context, pg, bc);

		pm.addWaitForDataListener(new WaitForDataListener() {
			@Override
			public void waitForData(boolean waiting) {
				for(WaitForDataListener listener : waitForDataListeners)
					listener.waitForData(waiting);
			}
		});
		
		crv = new ChallengeResponseVerifier(this, wallet, params, pg, bc);


		//assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));

		//sanity check, that the protocol identifier isn't overwritten by a new bloom filter etc

		wallet.addReorganizeEventListener(new WalletReorganizeEventListener() {
			
			@Override
			public void onReorganize(Wallet wallet) {
				for(TimeChangedEventListener l : timeChangedListeners) {
					l.onTimeChangedEvent();
				}
			}
		});
		bc.addNewBestBlockListener(new NewBestBlockListener() {
			
			@Override
			public void notifyNewBestBlock(StoredBlock block)
					throws VerificationException {
				for(TimeChangedEventListener l : timeChangedListeners) {
					l.onTimeChangedEvent();
				}
			}
		});

		wallet.addChangeEventListener(new WalletChangeEventListener() {
			
			@Override
			public void onWalletChanged(Wallet wallet) {
				insertIdentifierIntoFilter();
			}
		});
	}



	private void insertIdentifierIntoFilter() {
		for(Peer p : pg.getConnectedPeers()) {
			BloomFilter filter = p.getBloomFilter();
			filter.insert(BroadcastAnnouncement.magicNumber);
			p.setBloomFilter(filter);
		}
		Peer downloadpeer = pg.getDownloadPeer();


		BloomFilter filter = downloadpeer.getBloomFilter();
		filter.insert(BroadcastAnnouncement.magicNumber);
		downloadpeer.setBloomFilter(filter);
	}
	
	
	public void exit() {
		System.out.println("Exiting Bitnym PTP");
		ptp.exit();
		ptp.deleteHiddenService();
		System.out.println("Exiting Bitnym bitcoin peer group");
		pg.stop();
		// close spvbs to release file lock for reinitialising bitnym
		try {
			spvbs.close();
		} catch (BlockStoreException e) {
			System.out.println("Debug: spvbs close failed!");
			e.printStackTrace();
		}
		System.out.println("Done exiting Bitnym");
	}
	
	
	/**
	 * Generates and broadcasts a genesis transaction.
	 * @param lockTime
	 * @return \c True when the transaction has been send, \c false if there already is a proof
	 *         or not enough money exists
	 */
	public boolean generateGenesisTransaction(int lockTime) {
		Context.propagate(context);
		//generate genesis transaction if our proof is empty
		if(pm.getValidationPath().size() != 0 || !wallet.getBalance(BalanceType.AVAILABLE).isGreaterThan(PSNYMVALUE)) {
			return false;
		}
		try {
			Transaction genesisTx = tg.generateGenesisTransaction(pm, walletFile, lockTime);
			//TODO register listener before sending tx out, to avoid missing a confidence change
			genesisTx.getConfidence().addEventListener(new Listener() {

				@Override
				public void onConfidenceChanged(TransactionConfidence confidence,
						ChangeReason reason) {
					if (confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING) {
						return;
					}
					if(confidence.getDepthInBlocks() == 1) {
						//enough confidence, write proof message to the file system
						System.out.println("depth of genesis tx is now 1, consider ready for usage");
					}
				}
			});
			return true;
		} catch (InsufficientMoneyException e) {
			// Should not happen since we check it before
			e.printStackTrace();
			return false;
		}
	}
	
	public void sendBroadcastAnnouncement(int lockTime) {
		Context.propagate(context);
		try {
			System.out.println("sendBroadcastAnnouncement");
			//if(pm.isEmpty() || pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {
			if(pm.isEmpty()) { //|| !pm.getLastTransaction().getConfidence().getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
				return;
			} else {
				tg.sendBroadcastAnnouncement(new BroadcastAnnouncement(ptp.getIdentifier().getTorAddress(), pm.getLastTransactionOutput().getValue().getValue(), 10), walletFile, pm, lockTime);
			}
		} catch (InsufficientMoneyException e) {
			e.printStackTrace();
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
					//TimeUnit.MINUTES.sleep(10);
					break;
				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
	}
	
	public void mixWithNewestBroadcast(int lockTime) throws NoBroadcastAnnouncementsException {
		if(pm.isEmpty()) {
			System.out.println("mixWithNewestBroadcast aborted: No own proof message");
			return;
		} else if(pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {
			System.out.println("mixWithNewestBroadcast aborted: Last transaction is not yet verified " + pm.getLastTransaction().toString());
			return;
		} else {
			m.setBroadcastAnnouncement(mpd.getNewestBroadcast());
			assert(lockTime >= 0);
			m.setLockTime(lockTime);
			m.initiateMix();
		}
	}

	public void mixWithRandomBroadcast(int lockTime) throws NoBroadcastAnnouncementsException {
		Context.propagate(context);
		//		assert(pg.getDownloadPeer().getBloomFilter().contains(BroadcastAnnouncement.magicNumber));
		//if(!mpd.hasBroadcasts() || pm.isEmpty()) {
		if(pm.isEmpty()) {	
			System.out.println("mixWithRandomBroadcast aborted: No own proof message");
			return;
		} else if(pm.getLastTransaction().getConfidence().getDepthInBlocks() == 0) {	
			System.out.println("mixWithRandomBroadcast aborted: Last transaction is not yet verified " + pm.getLastTransaction().toString());
			return;
		} else {
			m.setBroadcastAnnouncement(mpd.getRandomBroadcast());
			assert(lockTime >= 0);
			m.setLockTime(lockTime);
			m.initiateMix();
		}		
	}

	private void reinitMixer() {
		stopListeningForMix();
		m = new Mixer(this, pm, wallet, context, pg, bc);
		m.addWaitForDataListener(new WaitForDataListener() {
			@Override
			public void waitForData(boolean waiting) {
				for (WaitForDataListener listener : waitForDataListeners) {
					listener.waitForData(waiting);
				}
			}
		});
	}

	// Timeout to abort mixing after a certain time
	private void startTimeout() {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				stopTimeout = false;
				int timeout = 4 * 60;
				while (timeout > 0 && !stopTimeout) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					timeout--;
				}
				if (timeout <= 0 && !stopTimeout) {
					// abort mixing, create new mixer, listener inactive until doMix() is called
					System.out.println("Mixing aborted: Mixing timed out");
					mixAborted(Mixer.AbortCode.TIMEOUT);
				}
			}
		});
		thread.start();
	}

	/**
	 *
	 * @param errorCode
	 * case 0: abortMessage = "Timeout";
	 * case 1: abortMessage = "Partner not found";
	 * case 5: abortMessage = "Partner proof Invalid/could not check TX";
	 * case 10: abortMessage = "Partner aborted";
	 * case 11: abortMessage = "Mixing active simultaneously";
	 */
	private void mixAborted(Mixer.AbortCode errorCode) {
		// remove old mixer and create new one after abort event (removes listeners, state etc)
		removeBroadcastAnnouncementChangeEventListener(broadcastListener);
		broadcastListener = null;
		removeTransactionGeneratorListener(tgListener);
		// avoid using old bc on timeout
		if(errorCode == Mixer.AbortCode.TIMEOUT) {
			getBroadcastAnnouncements().clear();
		}
		reinitMixer();
		listenerAdded = false;
		stopTimeout = true;
		for (MixingEventListener listener : mixListeners) {
			listener.onMixAborted(errorCode);
		}
	}

	private void mixFinished() {
		// stop mixing again
		stopListeningForMix();
		removeBroadcastAnnouncementChangeEventListener(broadcastListener);
		removeTransactionGeneratorListener(tgListener);
		stopTimeout = true;
		for (MixingEventListener listener : mixListeners) {
			listener.onMixFinished();
		}
	}

	private void mixStarted() {
		startTimeout();
		for (MixingEventListener listener : mixListeners) {
			listener.onMixStarted();
		}
	}

	/**
	 * Starts mixing if broadcast is available, otherwise send broadcast und listen for mix
	 * @param lockTime
	 */
	public void doMix(int lockTime) {
		Context.propagate(context);
		listenForMix();
		if(!listenerAdded) {
			this.addMixFinishedEventListener(new MixFinishedEventListener() {

				@Override
				public void onMixFinished() {
					mixFinished();
				}
			});

			this.addMixPassiveEventListener(new MixStartedEventListener() {
				@Override
				public void onMixStarted() {
					mixStarted();
				}
			});

			this.addMixAbortEventListener(new MixAbortEventListener() {

				@Override
				public void onMixAborted(Mixer.AbortCode errorCode) {
					mixAborted(errorCode);
				}
			});
		}
		listenerAdded = true;
		// Remove old broadcasts (older than four minutes)
		System.out.println("Currently having " + getBroadcastAnnouncements().size() + " broadcasts.");
		System.out.println("Wait random time to receive broadcasts before sending own.");
		int randomTime = (int) (Math.random() * (5000));
		try {
			Thread.sleep(randomTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Set<Transaction> to_remove = new HashSet<Transaction>();
		for (Transaction t : getBroadcastAnnouncements()) {
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			//get current date time with Date()
			Date date = new Date();
			System.out.println("Test current time: " + dateFormat.format(date) + "( " + System.currentTimeMillis() + ")");
			System.out.println("Test blip: " + getCurrentBIP113Time() + "( " + getCurrentBIP113Time().getTime() + ")");
			System.out.println("Test locktime: " + t.getLockTime());
			// Locktime is interpreted as the time when the broadcast has been created
			if (t.getLockTime() < (getCurrentBIP113Time().getTime() / 1000) - (BROADCAST_TIME * 60)) {
				to_remove.add(t);
			}
		}
		getBroadcastAnnouncements().removeAll(to_remove);
		System.out.println("After removing old ones having " + getBroadcastAnnouncements().size() + " broadcasts.");

		if (getBroadcastAnnouncements().isEmpty()) {
			// check what time broadcast would have
			long bcTime = (CLTVScriptPair.currentBitcoinBIP113Time(bc)-1);


			// add listener for tg, so mix can be initiated only after own broadcast was successfully wrote to file
			tgListener = new TransactionGeneratorListener() {
				@Override
				public void onTransactionWroteToFile() {
					// check if mixing is possible every time a new broadcast is received
					listenToBroadcasts(lockTime);
					//check if broadcast
					if (!getBroadcastAnnouncements().isEmpty()) {
						System.out.println("Broadcast found, try mixing");
						try {
							mixWithNewestBroadcast(lockTime);
							// Remove all stored broadcasts since one of them has been used
							getBroadcastAnnouncements().clear();
						} catch (NoBroadcastAnnouncementsException e1) {
							// Should not happen
							e1.printStackTrace();
						}
					}
				}
			};
			addTransactionGeneratorListener(tgListener);

			// Send a broadcast ourself
			sendBroadcastAnnouncement(0);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			//get current date time with Date()
			Date date = new Date();
			Date bcDate = new Date();
			bcDate.setTime(bcTime * 1000);
			System.out.println("Test bcDate: " + bcDate + ". Broadcast is " + bcTime + " old");
			System.out.println("Test current time: " + dateFormat.format(date) + "( " + System.currentTimeMillis() + ")");
			System.out.println("Test blip: " + getCurrentBIP113Time() + "( " + getCurrentBIP113Time().getTime() + ")");
			if (bcTime < (getCurrentBIP113Time().getTime() / 1000) - (BROADCAST_TIME * 60)) {
				System.out.println("Broadcast would be too old. Send new one");
				mixAborted(Mixer.AbortCode.BROADCAST_OLD);
			}
		} else {
			// There are broadcasts, mix with one of them
			try {
				mixWithNewestBroadcast(lockTime);
				// Remove all stored broadcasts since one of them has been used
				getBroadcastAnnouncements().clear();
			} catch (NoBroadcastAnnouncementsException e1) {
				// Should not happen
				e1.printStackTrace();
			}
		}
	}
	/*
	public void mixWith(BroadcastAnnouncement ba) {
		
	}*/

	private void listenToBroadcasts(int lockTime) {
		// check if mixing is possible every time a new broadcast is received
		broadcastListener = new BroadcastAnnouncementChangeEventListener() {
			@Override
			public void onBroadcastAnnouncementChanged() {
				System.out.println("DEBUG: BroadcastAnnouncementChange Received");
				// Remove old broadcasts (older than four minutes)
				System.out.println("Currently having " + getBroadcastAnnouncements().size() + " broadcasts.");
				Set<Transaction> to_remove = new HashSet<Transaction>();
				for (Transaction t : getBroadcastAnnouncements()) {
					DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
					//get current date time with Date()
					Date date = new Date();
					System.out.println("Test current time: " + dateFormat.format(date) + "( " + System.currentTimeMillis() + ")");
					System.out.println("Test blip: " + getCurrentBIP113Time() + "( " + getCurrentBIP113Time().getTime() + ")");
					System.out.println("Test locktime: " + t.getLockTime());
					// Locktime is interpreted as the time when the broadcast has been created
					if (t.getLockTime() < (getCurrentBIP113Time().getTime() / 1000) - (BROADCAST_TIME * 60)) {
						to_remove.add(t);
					}
				}
				getBroadcastAnnouncements().removeAll(to_remove);
				System.out.println("After removing old ones having " + getBroadcastAnnouncements().size() + " broadcasts.");

				if (getBroadcastAnnouncements().isEmpty()) {
					// do nothing
				} else {
					// There are broadcasts, mix with one of them
					try {
						System.out.println("Trying to mix with broadcast");
						mixWithNewestBroadcast(lockTime);
						// Remove all stored broadcasts since one of them has been used
						getBroadcastAnnouncements().clear();
					} catch (NoBroadcastAnnouncementsException e1) {
						// Should not happen
						e1.printStackTrace();
					}
				}

			}
		};
		addBroadcastAnnouncementChangeEventListener(broadcastListener);
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
		pm.addProofConfidenceChangeEventListener(listener);
	}
	
	public void addProofChangeEventListener(ProofChangeEventListener listener) {
		pm.addProofChangeEventListener(listener);
		proofChangeListeners.add(listener);
	}
	
	
	public void addBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener listener) {
		mpd.addBroadcastAnnouncementChangeEventListener(listener);
	}

	public void addTransactionGeneratorListener(TransactionGeneratorListener listener) {
		tg.addTransactionGeneratorListener(listener);
	}
	
	public void addMixFinishedEventListener(MixFinishedEventListener listener) {
		m.addMixFinishedEventListener(listener);
	}

	public void addMixAbortEventListener(MixAbortEventListener listener) {
	    m.addMixAbortEventListener(listener);
	}

	public void addMixPassiveEventListener(MixStartedEventListener listener) {
	    m.addMixPassiveEventListener(listener);
	}

	public void addMixingEventListener(MixingEventListener listener) {
	    mixListeners.add(listener);
	}

	public void addWaitForDataListener(WaitForDataListener listener) {
	    waitForDataListeners.add(listener);
	}
	
	public void removeProofConfidenceChangeEventListener(ProofConfidenceChangeEventListener listener) {
		pm.removeProofConfidenceChangeEventListener(listener);
		proofChangeConfidenceListeners.remove(listener);
	}
	
	public void removeProofChangeEventListener(ProofChangeEventListener listener) {
		pm.removeProofChangeEventListener(listener);
		proofChangeListeners.remove(listener);
	}
	
	public void removeBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener listener) {
		if(listener != null) {
			mpd.removeBroadcastAnnouncementChangeEventListener(listener);
		}
	}

	public void removeTransactionGeneratorListener(TransactionGeneratorListener listener) {
		if(listener != null) {
			tg.removeTransactionGeneratorListener(listener);
		}
	}

	public void removeWaitForDataListener(WaitForDataListener listener) {
		if(listener != null) {
			waitForDataListeners.remove(listener);
		}
	}

	public void removeMixFinishedEventListener(MixFinishedEventListener listener) {
		m.removeMixFinishedEventListener(listener);
	}

	public void removeMixAbortEventListener(MixAbortEventListener listener) {
		m.removeMixAbortEventListener(listener);
	}

	public void removeMixPassiveEventListener(MixStartedEventListener listener) {
		m.removeMixPassiveEventListener(listener);
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

	public void abortMixing() {
		m.closeListeningForMix();
		removeBroadcastAnnouncementChangeEventListener(broadcastListener);
		broadcastListener = null;
		removeTransactionGeneratorListener(tgListener);
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
		for(ProofConfidenceChangeEventListener listener : proofChangeConfidenceListeners) {
			pm.addProofConfidenceChangeEventListener(listener);
		}
		for(ProofChangeEventListener listener : proofChangeListeners) {
			pm.addProofChangeEventListener(listener);
		}
		m.setOwnProof(pm);
		for(ProofChangeEventListener l : proofChangeListeners) {
			l.onProofChanged();
		}
	}



	public Date getCurrentBIP113Time() {
		return new Date(((long) CLTVScriptPair.currentBitcoinBIP113Time(bc))*1000);
	}



	public void addTimeChangedEventListener(
			TimeChangedEventListener listener) {
		this.timeChangedListeners.add(listener);
	}
	
	public void removeTimeChangedEventListener(TimeChangedEventListener listener) {
		this.timeChangedListeners.remove(listener);
	}



	public String getBroadcastAnnouncementsString() {
		return this.mpd.getBroadcastAnnouncements().toString();
	}



	public void listenForVerification() {
		crv.listenForProofToVerify();
	}



	public void sendForVerification(String onionAddress) {
		crv.setMixPartnerAdress(onionAddress);
		crv.proveToVerifier(pm, wallet.findKeyFromPubHash(pm.getScriptPair().getPubKeyHash()));
	}

	/**
	 * Checks given proof
	 * @param proof proof to check
	 * @return null on failure, contained pubkey on success
	 */
	public byte[] checkProof(byte[] proof) {
		System.out.println("DEBUG BitWallet: Check proof");
		Context.propagate(context);
		try (ByteArrayInputStream bis = new ByteArrayInputStream(proof); ObjectInput in = new ObjectInputStream(bis)) {
			ProofMessage other_pm = (ProofMessage) in.readObject();
			other_pm.addWaitForDataListener(new WaitForDataListener() {
				@Override
				public void waitForData(boolean waiting) {
					for(WaitForDataListener listener : waitForDataListeners) {
						listener.waitForData(waiting);
					}
				}
			});
			if (!other_pm.isProbablyValid(MainClass.params, bc, pg)) {
				System.out.println("DEBUG BitWallet: other_pm is probably invalid");
				return null;
			}
			return other_pm.getScriptPair().getPubKey();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns a proof to verify with checkProof().
	 * @return A proof of pseudonym ownership
	 */
	public byte[] getProof() {
		byte[] serialized = null;
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
		         ObjectOutput out = new ObjectOutputStream(bos)) {
		        out.writeObject(pm);
		        serialized = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return serialized;
	}



	public String getCurrentOnionAddress() {
		return ptp.getIdentifier().getTorAddress();
	}

	void restartPTP() {
		if (ptp != null) {
			return;
		}
		ptp = new PTP("ptp-bitnym", null);
		try {
			//TODO move ptp to mixer
			log.info("initiate ptp and create hidden service");
			ptp.init();
			System.out.println("executed ptp init");
			ptp.createHiddenService();
			System.out.println(ptp.getIdentifier().getTorAddress());
		} catch (IOException e3) {
			e3.printStackTrace();
		}
	}

	public ECKey getPseudonym() {
		return wallet.findKeyFromPubHash(pm.getScriptPair().getPubKeyHash());
	}

	/**
	 * Only for testing
	 * @return ptp identifier
	 */
	public Identifier getIdentifier() {
		if (ptp.isInitialized()) {
			return ptp.getIdentifier();
		} else {
			return null;
		}
	}

	public void sendMessage(Object message, Identifier ident) {
		try {
			ptp.sendMessage(message, ident);
		} catch (IllegalStateException e) {
			System.err.println("BitNym Unable to send message over PTP! Continuing anyway.");
			e.printStackTrace();
		}
	}

}
