package bitnymWallet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;


public class MixPartnerDiscovery implements NewBestBlockListener, BlocksDownloadedEventListener {
	
	private PeerGroup pg;
	private BlockChain bc;
	private Block head;
	private List<Transaction> broadcasts;
	private Wallet wallet;
	private List<BroadcastAnnouncementChangeEventListener> listeners;

	public MixPartnerDiscovery(NetworkParameters params, PeerGroup pg, BlockChain bc, Wallet wallet) {
		this.pg = pg;
		this.bc = bc;
		this.head = null;
		this.wallet = wallet;
		this.broadcasts = new ArrayList<Transaction>();
		this.listeners = new ArrayList<BroadcastAnnouncementChangeEventListener>();
	}
	
	//method for experimenting
	public void downloadCurrentBlock() {
		Peer p = pg.getDownloadPeer();
		ListenableFuture<Block> headFuture = p.getBlock(bc.getChainHead().getHeader().getHash());
		try {
			this.head = headFuture.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
						
	}
	
	
	public void searchCurrentBlockForPartners() {
		if(this.head == null) {
			return;
		}
		searchBlockForPartner(this.head);
	}
	
	public void searchBlockForPartner(Block block) throws NullPointerException {
		if(this.head == null) {
			throw new NullPointerException("Block must not be null");
		}
		
		List<Transaction> listTxs = block.getTransactions();
		for(Transaction tx : listTxs) {
			System.out.println("test transaction " + tx.getHashAsString());
			if(isTransactionBroadcastAnnouncement(tx)) {
				broadcasts.add(tx);
				System.out.println("found a broadcast announcement!");
				System.out.println(tx);
			}
		}
	}

	public boolean isTransactionBroadcastAnnouncement(Transaction tx) {
		List<TransactionOutput> outputs = tx.getOutputs();
		TransactionOutput scndOutput = outputs.get(1);
		if(!scndOutput.getScriptPubKey().isOpReturn()) {
			return false;
		}
		
		byte[] script = scndOutput.getScriptBytes();
		//check magic numbers that are defined in BroadcastAnnouncement
		return BroadcastAnnouncement.isBroadcastAnnouncementScript(script);
	}
	

	@Override
	public void notifyNewBestBlock(StoredBlock sblock)
			throws VerificationException {
		Peer p = pg.getDownloadPeer();
		//use p.setbloomfilter or is this automatically done?
		
		ListenableFuture<Block> futureBlock = p.getBlock(sblock.getHeader().getHash());
		try {
			this.head = futureBlock.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		System.out.println("searchCurrentBlockForPartners");
		searchCurrentBlockForPartners();
	}
	
	//TODO getPubKey might lead to an exception, use try-catch
	@Override
	public void onBlocksDownloaded(Peer arg0, Block arg1,
			@Nullable FilteredBlock arg2, int arg3) {
		System.out.println("received block");
		boolean receivedBcastAnnouncmnt = false;
		Map<Sha256Hash, Transaction> assocTxs = arg2.getAssociatedTransactions();
		for(Transaction tx : assocTxs.values()) {
			System.out.println("from within mixpartner discovery " + tx);			
			if(tx.getOutputs().size() > 1 &&
					BroadcastAnnouncement.isBroadcastAnnouncementScript(tx.getOutput(1).getScriptBytes()))
					//&& !wallet.isTransactionRelevant(tx)) {
				//tx.getInput(0).getScriptSig().getChunks().get(0)
					{
				if(!this.broadcasts.contains(tx) && wallet.getTransaction(tx.getHash()) == null) {
					this.broadcasts.add(tx);
					receivedBcastAnnouncmnt = true;
				}
			}
		}
		
		if(receivedBcastAnnouncmnt) {
			for(BroadcastAnnouncementChangeEventListener l : listeners) {
				l.onBroadcastAnnouncementChanged();
			}
		}
	}
	
	//get a random mixpartner from several potential mix partner
	public BroadcastAnnouncement getMixpartner() {
		if(broadcasts.size() == 0) {
			return null;
		}
		int random;
		Random r = new Random();
		System.out.println("pick a random mix partner from list, broadcastsize is " + broadcasts.size());	
		random = r.nextInt(broadcasts.size());
		Transaction tx = broadcasts.get(random);
		broadcasts.remove(random);
		return BroadcastAnnouncement.deserialize(tx.getOutput(1).getScriptBytes());
	}

	public boolean hasBroadcasts() {
		return this.broadcasts.size() > 0;
	}
	
	public List<Transaction> getBroadcastAnnouncements() {
		return this.broadcasts;
	}
	
	public void addBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener l) {
		this.listeners.add(l);
	}
	
	public void removeBroadcastAnnouncementChangeEventListener(BroadcastAnnouncementChangeEventListener l) {
		this.listeners.remove(l);
	}

	public BroadcastAnnouncement getNewestBroadcast() throws NoBroadcastAnnouncementsException {
		if(broadcasts.size() == 0) {
			throw new NoBroadcastAnnouncementsException();
		}
		Transaction tx = broadcasts.get(0);
		for (Transaction t : broadcasts) {
			if (tx.getLockTime() < t.getLockTime()) {
				tx = t;
			}
		}
		assert(tx != null);
		//TODO substitute magic number 1 for constant "outputOffsetOfBroadcastAnnouncementOpReturn"
		return BroadcastAnnouncement.deserialize(tx.getOutput(1).getScriptBytes());
	}

	public BroadcastAnnouncement getRandomBroadcast() throws NoBroadcastAnnouncementsException {
		if(broadcasts.size() == 0) {
			throw new NoBroadcastAnnouncementsException();
		}
		Random r = new Random();
		Transaction tx = broadcasts.get(r.nextInt(broadcasts.size()));
		//TODO substitute magic number 1 for constant "outputOffsetOfBroadcastAnnouncementOpReturn" 
		return BroadcastAnnouncement.deserialize(tx.getOutput(1).getScriptBytes());
	}
	
	
}