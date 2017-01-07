
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
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
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
	
	PeerGroup pg;
	BlockChain bc;
	Block head;
	List<Transaction> broadcasts;
	//TODO add ring buffer data structure
	private Wallet wallet;

	public MixPartnerDiscovery(NetworkParameters params, PeerGroup pg, BlockChain bc, Wallet wallet) {
		this.pg = pg;
		this.bc = bc;
		this.head = null;
		this.wallet = wallet;
		this.broadcasts = new ArrayList<Transaction>();
	}
	
	//method for experimenting
	public void downloadCurrentBlock() {
		Peer p = pg.getDownloadPeer();
		ListenableFuture<Block> headFuture = p.getBlock(bc.getChainHead().getHeader().getHash());
		try {
			this.head = headFuture.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
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
		TransactionOutput fstOutput = outputs.get(0);
		if(!fstOutput.getScriptPubKey().isOpReturn()) {
			return false;
		}
		
		byte[] script = fstOutput.getScriptBytes();
		//check magic numbers that are defined in BroadcastAnnouncement
		return BroadcastAnnouncement.isBroadcastAnnouncementScript(script);
	}
	
	
	//put somewhere else, does not really fit into mixpartnerdiscovery?
	//TODO use only valid pseudonyms (through coin selector for example) to prevent deanonymization
	//as an input
	public static void sendBroadcastAnnouncement(NetworkParameters params, Wallet w, BroadcastAnnouncement ba, File f, ProofMessage pm) throws InsufficientMoneyException {
		//build transaction
		Transaction tx = new Transaction(params);
		
		Script s = ba.buildScript();
		System.out.println("Script size is " + s.SIG_SIZE);
		//System.out.println(s.getScriptType());
		
		tx.addOutput(Coin.ZERO, s);
		
		SendRequest req = SendRequest.forTx(tx);
		req.coinSelector = new DefaultCoinSelector();
		req.signInputs = true;
		req.shuffleOutputs = false;
		Wallet.SendResult result = w.sendCoins(req);
		try {
			w.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			result.broadcastComplete.get();
			//TODO insert the script pair, after we changed the broadcast announcement script type
			pm.addTransaction(result.tx, 1, null);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
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
			System.out.println(tx);
			if(BroadcastAnnouncement.isBroadcastAnnouncementScript(tx.getOutput(0).getScriptBytes())
					&& wallet.findKeyFromPubKey(tx.getInput(0).getScriptSig().getPubKey()) == null) {
				this.broadcasts.add(tx);
			}
		}
	}
	
	public BroadcastAnnouncement getMixpartner() {
		int random;
		Random r = new Random();
		System.out.println(broadcasts.size());	
		random = r.nextInt(broadcasts.size());
		Transaction tx = broadcasts.get(random);
		return BroadcastAnnouncement.deserialize(tx.getOutput(0).getScriptBytes());
	}

	public boolean hasBroadcasts() {
		return this.broadcasts.size() > 0;
	}
	
	
	
	
}