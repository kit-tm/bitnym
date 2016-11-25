
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;


public class MixPartnerDiscovery implements NewBestBlockListener {
	
	PeerGroup pg;
	BlockChain bc;
	Block head;
	List<Transaction> broadcasts;
	//TODO add ring buffer data structure

	public MixPartnerDiscovery(NetworkParameters params, PeerGroup pg, BlockChain bc) {
		this.pg = pg;
		this.bc = bc;
		this.head = null;
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
	
	
	//put somewhere else, does not really fit into mixpartnerdiscovery
	public static void sendBroadcastAnnouncement(NetworkParameters params, Wallet w, BroadcastAnnouncement ba) throws InsufficientMoneyException {
		//build transaction
		Transaction tx = new Transaction(params);
		
		Script s = ba.buildScript();
		//System.out.println(s.getScriptType());
		
		tx.addOutput(Coin.ZERO, s);
		
		SendRequest req = SendRequest.forTx(tx);
		req.coinSelector = new DefaultCoinSelector();
		req.signInputs = true;
		req.shuffleOutputs = false;
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
	
}