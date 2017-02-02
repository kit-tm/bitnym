
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;


public class MixPartnerDiscovery implements NewBestBlockListener, BlocksDownloadedEventListener {
	
	private PeerGroup pg;
	private BlockChain bc;
	private Block head;
	private List<Transaction> broadcasts;
	private Wallet wallet;
	private ProofMessage pm;

	public MixPartnerDiscovery(NetworkParameters params, PeerGroup pg, BlockChain bc, Wallet wallet, ProofMessage pm) {
		this.pg = pg;
		this.bc = bc;
		this.head = null;
		this.wallet = wallet;
		this.broadcasts = new ArrayList<Transaction>();
		this.pm = pm;
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
	
	@Override
	public void onBlocksDownloaded(Peer arg0, Block arg1,
			@Nullable FilteredBlock arg2, int arg3) {
		System.out.println("received block");
		Map<Sha256Hash, Transaction> assocTxs = arg2.getAssociatedTransactions();
		for(Transaction tx : assocTxs.values()) {
			System.out.println("from within mixpartner discovery " + tx);
			//TODO getPubKey might lead to an exception, use try-catch
			if(BroadcastAnnouncement.isBroadcastAnnouncementScript(tx.getOutput(1).getScriptBytes())
					&& wallet.findKeyFromPubKey(tx.getInput(1).getScriptSig().getPubKey()) == null) {
				this.broadcasts.add(tx);
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
	
	
	
	
}