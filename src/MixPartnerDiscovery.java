
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
	//TODO add ring buffer data structure
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
			// TODO Auto-generated catch blockTransaction
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
		TransactionOutput scndOutput = outputs.get(1);
		if(!scndOutput.getScriptPubKey().isOpReturn()) {
			return false;
		}
		
		byte[] script = scndOutput.getScriptBytes();
		//check magic numbers that are defined in BroadcastAnnouncement
		return BroadcastAnnouncement.isBroadcastAnnouncementScript(script);
	}
	
	
	//put somewhere else, does not really fit into mixpartnerdiscovery?
	//TODO use only valid pseudonyms (through coin selector for example) to prevent deanonymization
	//as an input
	public static void sendBroadcastAnnouncement(NetworkParameters params, Wallet w, BroadcastAnnouncement ba, File f, ProofMessage pm, PeerGroup pg) throws InsufficientMoneyException {
		//build transaction
		Transaction tx = new Transaction(params);
		
		Script s = ba.buildScript();
		System.out.println("Script size is " + s.SIG_SIZE);
		//System.out.println(s.getScriptType());
		ECKey psnymKey = new ECKey();
		long unixTime = System.currentTimeMillis() / 1000L;
		//TODO use bitcoin nets median time
		tx.setLockTime(unixTime-(10*60*150));
		CLTVScriptPair sp = new CLTVScriptPair(psnymKey, unixTime-(10*60*150));
		w.importKey(psnymKey);
		tx.addOutput(new TransactionOutput(params, tx, pm.getLastTransactionOutput().getValue().subtract(estimateBroadcastFee()), sp.getPubKeyScript().getProgram()));
		tx.addOutput(Coin.ZERO, s);
		tx.addInput(pm.getLastTransactionOutput());
		tx.getInput(0).setSequenceNumber(3); //the concrete value doesn't matter, this is just for cltv
		tx.getInput(0).setScriptSig(pm.getScriptPair().calculateSigScript(tx, 0, w));
		
		try {
			w.commitTx(tx);
			w.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		TransactionBroadcast broadcast = pg.broadcastTransaction(tx);
		pm.addTransaction(tx, 0, sp);
		pm.writeToFile();
		System.out.println("save broadcast announcement to file");
		
		

	}


	private static Coin estimateBroadcastFee() {
		//TODO implement
		return Coin.valueOf(50000);
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
			//TODO getPubKey might lead to an exception, use try-catch
			if(BroadcastAnnouncement.isBroadcastAnnouncementScript(tx.getOutput(0).getScriptBytes())
					&& wallet.findKeyFromPubKey(tx.getInput(0).getScriptSig().getPubKey()) == null) {
				this.broadcasts.add(tx);
			}
		}
	}
	
	//get a random mixpartner from several potential mix partner
	//TODO remove chosen tx, to not try mix later on
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