import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.DeterministicSeed;

import org.bitcoinj.core.Address;

import com.mysql.jdbc.TimeUtil;




public class MainClass {
	
	
	private static Coin PROOF_OF_BURN = Coin.valueOf(50000);
	private static Coin FEE = Coin.valueOf(500000);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//initialize neccessary bitcoinj variables
		final NetworkParameters params = TestNet3Params.get();
		
		Wallet wallet = new Wallet(params);
		File f = new File("./wallet.wa");
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
		//use newWithTor instead? 
		PeerGroup pg = new PeerGroup(params, bc);
		try {
			System.out.println("add addresses");
			//pg.addAddress(InetAddress.getByName("46.166.165.18"));
			//pg.addAddress(InetAddress.getByName("71.199.135.215"));
			pg.addAddress(InetAddress.getByName("bitcoins.sk"));
			pg.addAddress(InetAddress.getByName("btc.smsys.me"));
			pg.addAddress(InetAddress.getByName("btc.vpirate.org"));
			pg.addAddress(InetAddress.getByName("cluelessperson.com"));
			pg.addAddress(InetAddress.getByName("condor1003.server4you.de"));
			pg.addAddress(InetAddress.getByName("electrum.rofl.cat"));
			pg.addAddress(InetAddress.getByName("electrum.snipanet.com"));
			pg.addAddress(InetAddress.getByName("btc.smsys.me"));
			pg.addAddress(InetAddress.getByName("electrum3.hachre.de"));
			pg.addAddress(InetAddress.getByName("uselectrum.be"));
			pg.addAddress(InetAddress.getByName("vps1.au.f4e.pw"));
			pg.addAddress(InetAddress.getByName("ulrichard.ch"));
			pg.addAddress(InetAddress.getByName("ilikehuskies.no-ip.org"));
			System.out.println("added addresses");
		} catch (UnknownHostException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
		System.out.println("download chain");
		pg.start();
		pg.downloadBlockChain();
		pg.stop();
		
		System.out.println(wallet.currentReceiveAddress().toBase58());
		try {
			System.out.println("sleep for 15minutes");
			TimeUnit.MINUTES.sleep(15);
		} catch (InterruptedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		System.out.println(wallet.getBalance().toString());
		try {
			wallet.saveToFile(f);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			generateGenesisTransaction(params, pg, wallet);
		} catch (InsufficientMoneyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}
	
	private static void generateGenesisTransaction(NetworkParameters params, PeerGroup pg, Wallet w) throws InsufficientMoneyException {
		Transaction tx = new Transaction(params);
		//wallet Balance is not sufficient
		//TODO add transaction fee in the comparison?
		if (w.getBalance().isLessThan(PROOF_OF_BURN)) {
			throw new InsufficientMoneyException(PROOF_OF_BURN.minus(w.getBalance()));
		}
		List<TransactionOutput> unspents = w.getUnspents();
		
		Coin suffInptValue = Coin.ZERO;
		
		Iterator<TransactionOutput> iterator = unspents.iterator();
		//TODO use only certain inputs, if so why use certain inputs?
		while(suffInptValue.isLessThan(PROOF_OF_BURN) && iterator.hasNext()) {
			TransactionOutput next = iterator.next();
			suffInptValue = suffInptValue.add(next.getValue());
			tx.addSignedInput(next.getOutPointFor(), next.getScriptPubKey() ,next.getOutPointFor().getConnectedKey(w));
			;
		}
		
		//add marker output
		tx.addOutput(PROOF_OF_BURN, ScriptBuilder.createOpReturnScript(null));
		//add pseudonym output
		ECKey psnymKey = new ECKey();
		Address nymAdrs = new Address(params, psnymKey.getPubKeyHash());
		w.importKey(psnymKey);
		tx.addOutput(new TransactionOutput(params, tx, suffInptValue.minus(PROOF_OF_BURN.minus(FEE)), nymAdrs));
		//TODO add change, for know we add everything except PoB and fees to the psnym
		
		
		try {
			System.out.println("verify the transaction");
			tx.verify();
		} catch (VerificationException e) {
			e.printStackTrace();
		}
	}

}
