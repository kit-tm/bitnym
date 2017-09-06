package bitnymWallet;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionBroadcast.ProgressCallback;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;


public class TransactionGenerator {
	
	private static final Logger log = LoggerFactory.getLogger(TransactionGenerator.class);

	private NetworkParameters params;

	private PeerGroup pg;

	private Wallet w;

	private BlockChain bc;


	public TransactionGenerator(NetworkParameters params, PeerGroup pg, Wallet w, BlockChain bc) {
		this.params = params;
		this.pg = pg;
		this.w = w;
		this.bc = bc;
		
	}
	
	//TODO refactor this out into an seperate class, and split into generating transaction
	// and sending of the transaction
	//@param lockTime how long to lock Psynym in seconds
	public Transaction generateGenesisTransaction(ProofMessage pm, File f, long lockTime) throws InsufficientMoneyException {
		log.info("try generating genesis tx");
		Transaction tx = new Transaction(params);
		//TODO refactoring: use smaller string for less tx fees
		byte[] opretData = "xxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
		//wallet Balance is not sufficient
		if (w.getBalance().isLessThan(BitNymWallet.PROOF_OF_BURN)) {
			throw new InsufficientMoneyException(BitNymWallet.PROOF_OF_BURN.minus(w.getBalance()));
		}



		//add marker output
		tx.addOutput(BitNymWallet.PROOF_OF_BURN, ScriptBuilder.createOpReturnScript(opretData));

		//add pseudonym output
		ECKey psnymKey = new ECKey();
		long unixTime = System.currentTimeMillis() / 1000L;
		//TODO use bitcoin bip113 time
		tx.setLockTime(CLTVScriptPair.currentBitcoinBIP113Time(bc)-1);
		CLTVScriptPair sp = new CLTVScriptPair(psnymKey, CLTVScriptPair.currentBitcoinBIP113Time(bc)+lockTime);
		System.out.println(sp.toString());
		assert(sp != null);
		w.importKey(psnymKey);

		Coin suffInptValue = Coin.ZERO;


		List<TransactionOutput> unspents = w.getUnspents();

		Iterator<TransactionOutput> iterator = unspents.iterator();
//		while(suffInptValue.isLessThan(totalOutput) && iterator.hasNext()) {
//			TransactionOutput next = iterator.next();
//			suffInptValue = suffInptValue.add(next.getValue());
//			tx.addInput(next);
//		}
		//create p2sh output, for possibility of freezing funds to prove it is utxo
		tx.addOutput(new TransactionOutput(params, tx, BitNymWallet.PSNYMVALUE, sp.getPubKeyScript().getProgram()));

		ECKey changeKey = new ECKey();
		Address changeAdrs = new Address(params, changeKey.getPubKeyHash());
		w.importKey(changeKey);




		SendRequest req = SendRequest.forTx(tx);
		req.changeAddress = changeAdrs;
		req.shuffleOutputs = false;
		req.signInputs = true;
		w.completeTx(req);
		try {
			log.info("verify the transaction");
			tx.verify();
		} catch (VerificationException e) {
			e.printStackTrace();
		}
		w.commitTx(req.tx);
		try {
			w.saveToFile(f);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		//Wallet.SendResult result = w.sendCoins(req);
		TransactionBroadcast a = pg.broadcastTransaction(req.tx);
		ListenableFuture<Transaction> future = a.broadcast();
		try {
			Transaction b = future.get();
			pm.addTransaction(b,1, sp);
			pm.writeToFile();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		//			try {
		//				result.broadcastComplete.get();
		//				System.out.println("broadcast complete");
		//				pm.addTransaction(result.tx, 1);
		//				pm.writeToFile();
		//				System.out.println("added genesis tx to proof message data structure and file");
		//			} catch (InterruptedException e) {
		//				e.printStackTrace();
		//			} catch (ExecutionException e) {
		//				e.printStackTrace();
		//			}
		log.info("genereated genesis tx");
		return tx;
	}
	
	
	
	public void sendBroadcastAnnouncement(BroadcastAnnouncement ba, File f, final ProofMessage pm, int lockTime) throws InsufficientMoneyException {
		//build transaction
		final Transaction tx = new Transaction(params);
		
		Script s = ba.buildScript();
		System.out.println("Script size is " + s.SIG_SIZE);
		//System.out.println(s.getScriptType());
		ECKey psnymKey = new ECKey();
		long unixTime = System.currentTimeMillis() / 1000L;
		//TODO use bitcoin nets median time
		tx.setLockTime(CLTVScriptPair.currentBitcoinBIP113Time(bc)-1);
		final CLTVScriptPair sp = new CLTVScriptPair(psnymKey, CLTVScriptPair.currentBitcoinBIP113Time(bc)+lockTime);
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
			e1.printStackTrace();
		}
		
		TransactionBroadcast broadcast = pg.broadcastTransaction(tx);
		broadcast.setProgressCallback(new ProgressCallback() {

			@Override
			public void onBroadcastProgress(double arg0) {
				if (arg0 == 1) {
					pm.addTransaction(tx, 0, sp);
					pm.writeToFile();
					System.out.println("save broadcast announcement to file");
				}
			}
		});
		
		

	}
	
	private Coin estimateBroadcastFee() {
		//TODO implement
		return Coin.valueOf(50000);
	}
}
