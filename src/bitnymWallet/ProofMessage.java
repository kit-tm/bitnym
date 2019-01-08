package bitnymWallet;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

import org.bitcoinj.core.*;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionConfidence.Listener;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.ECKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class ProofMessage implements Serializable {
	/**
	 * 
	 */
	
	//TODO: refactor change listener to caller, not super important, but cleaner
	
	private static final Logger log = LoggerFactory.getLogger(ProofMessage.class);
	private static final long serialVersionUID = 1L;
	private String filePath;
	private List<Transaction> validationPath;
	private List<Integer> outputIndices;
	private CLTVScriptPair sp;
	private int appearedInChainheight;
	private List<ProofConfidenceChangeEventListener> proofConfidenceChangeListeners;
	private List<ProofChangeEventListener> proofChangeListeners;

	private List<WaitForDataListener> waitForDataListeners;
	
	public void setValidationPath(List<Transaction> validationPath) {
		this.validationPath = validationPath;
	}

	public void setOutputIndices(List<Integer> outputIndices) {
		this.outputIndices = outputIndices;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public List<Transaction> getValidationPath() {
		return validationPath;
	}

	public List<Integer> getOutputIndices() {
		return outputIndices;
	}

	
	
	//default file is proofmessage.pm
	//TODO use config file for specification of proofmessage filename
	public ProofMessage() {		
		this(System.getProperty("user.dir") + "/proofmessage.pm");
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
	}
	
	//use certain proof message file
	public ProofMessage(String path) {
		//determines the corresponding output within the the mix txs
		this.proofConfidenceChangeListeners = new ArrayList<ProofConfidenceChangeEventListener>();
		this.proofChangeListeners = new ArrayList<ProofChangeEventListener>();
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		this.sp = new CLTVScriptPair();
		this.validationPath = new ArrayList<Transaction>();
		this.outputIndices = new ArrayList<Integer>();
		this.filePath = path;
		try {
			File file = new File(filePath);
			if(file.exists()) {
			   log.info("Proof Message file " + path + " exists, read data structure");
			   FileInputStream fin = new FileInputStream(file);
			   ObjectInputStream ois = new ObjectInputStream(fin);
			   ProofMessage tmp = (ProofMessage) ois.readObject();
			   this.outputIndices = tmp.outputIndices;
			   this.validationPath = tmp.validationPath;
			   this.sp = tmp.sp;
			   this.appearedInChainheight = tmp.appearedInChainheight;
			   this.proofChangeListeners = tmp.proofChangeListeners;
			   this.proofConfidenceChangeListeners = tmp.proofConfidenceChangeListeners;
			   //TODO register listener here, not at readobject
			   ois.close();
			   fin.close();
			   log.info("completed reading in proof message file");
			}
			
		} catch (FileNotFoundException e4) {
			e4.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public ProofMessage(List<Transaction> vP, List<Integer> oIdices) {
		this.validationPath = vP;
		this.outputIndices = oIdices;
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		
		assert(vP.size() == oIdices.size());
	}
	
	//TODO Constructor with certain path string or file in constructor
	
	public boolean isValidPath() {
		//check that the transaction build a path and are not just random txs, 
		//by checking the tx hashes with those of the outpoints
		System.out.println("DEBUG PATH:");
		for (Transaction tx : validationPath) {
			System.out.println("TX: " + tx.getHash().toString());
		}
		for(int i=validationPath.size()-1; i > 1; i--) {
			Transaction tx = validationPath.get(i);
			System.out.println("DEBUG: output is " + tx.getInput(outputIndices.get(i)).getOutpoint().getHash());
			System.out.println("DEBUG: Validationpath is " + validationPath.get(i-1).getHash());
			if(!tx.getInput(outputIndices.get(i)).getOutpoint().getHash().equals(validationPath.get(i-1).getHash())) {
				System.out.println("not a valid path!");
				return false;
			}
		}
		System.out.println("valid path");
		return true;
	}
	
	public boolean isValidGPTx() {
		//subclass transaction class for methods like isgenesistx and isbroadcastannouncementtx?
		return isGenesisTransaction(validationPath.get(0));
	}
	
	//first output is op_return output
	//seconds is the psynym, we don't care about how the inputs are set up
	//or whether change outputs or similar outputs are include,
	//proof_of_burn requirement should be also be met
	private boolean isGenesisTransaction(Transaction tx) {
		if(tx.getOutputs().size() < 2) {
			System.out.println("first transaction is not a valid genesis transaction");
			return false;
		}
		
		if(!tx.getOutput(0).getScriptPubKey().isOpReturn() ||
				tx.getOutput(0).getValue().isLessThan(MainClass.PROOF_OF_BURN)) {
			System.out.println("genesis tx has not apropriate op_return or doesn't pay the proof of burn");
			return false;
		}
		System.out.println("is genesis");
		return true;
	}
	
	//check that pub key corresponds to the key
	//of the p2sh-output
	public boolean isPubKeyCorrect(NetworkParameters params) {
		byte[] pubkeyHashFromP2SHOutput;
		if(!ScriptBuilder.createP2SHOutputScript(sp.getRedeemScript()).equals(getLastTransactionOutput().getScriptPubKey())) {
			System.out.println("received redeemscript doesn't match the redeemscript hash of the transaction output, abort");
			return false;
		}
		
		int chunklength = sp.getRedeemScript().getChunks().size();
		pubkeyHashFromP2SHOutput = sp.getRedeemScript().getChunks().get(chunklength-3).data;
		return Arrays.equals(Utils.sha256hash160(sp.getPubKey()), pubkeyHashFromP2SHOutput);
	}

	//important: my still be utxo, the locking is just a sufficient condition, not necessary
	//for clients with have the utxo set (eg. full node clients)
	public boolean isNymNotSpend(BlockChain bc) {
		//check locktime and compare to current bip113 time
		//we only accept p2sh
		if(!getLastTransactionOutput().getScriptPubKey().isPayToScriptHash()) {
			System.out.println("the psynym is not a p2sh");
			return false;
		}
		
		//check that redeem script matches the p2sh output hash
		if(!ScriptBuilder.createP2SHOutputScript(sp.getRedeemScript()).equals(getLastTransactionOutput().getScriptPubKey())) {
			System.out.println("the redeem script doesn't match the p2sh output hash");
			return false;
		}
		
		//check that the redeem script is the one we specified and check that it is still locked
		if(!sp.isRedeemScriptRightFormat() || !sp.isLocked(bc)) {
			System.out.println("redeem script is either not in right format or transaction is not locked, might be not spend, but we can't know without utxo set");
			return false;
		}
		return true;
	}
	
	//check the locktime to know which filteredblocks to request
	//modify the bloomfilter from downloadpeer to include the tx hash of the tx we want to check
	//then check merkle branch 
	public boolean isNymTxInBlockChain(NetworkParameters params, BlockChain bc, PeerGroup pg) {
		//get filtered block with transaction and check merkel tree
		final BlockStore blockstore = bc.getBlockStore();
		System.out.println("check that transaction is in blockchain");
		System.out.println("DEBUG: Last TX: " + getLastTransaction());
		final Peer dpeer = pg.getDownloadPeer();
		System.out.println("Download peer: " + dpeer.toString());
		System.out.println("Peers connected : " + pg.getConnectedPeers().size());


		final Object monitor = new Object();
		//listener forces monitorstate to be final, so we use a wrapper class, to still be able to modify it
		class BooleanWrapper {
			boolean monitorState = false;

			void setMonitorState(boolean b) {
				this.monitorState = b;
			}

			boolean getMonitorState() {
				return this.monitorState;
			}
		}
		final BooleanWrapper monState = new BooleanWrapper();
		final BooleanWrapper isTxInBlockchain = new BooleanWrapper();
		GetDataMessage msg = new GetDataMessage(params);
		assert(appearedInChainheight > 0);
		msg.addFilteredBlock(getBlockHashByHeight(bc, appearedInChainheight));
		System.out.println("Block requested: " + getBlockHashByHeight(bc, appearedInChainheight));
		for(Peer peer : pg.getConnectedPeers()) {
			BloomFilter filter = peer.getBloomFilter();

			filter.insert(getLastTransaction().getInput(0).getOutpoint().unsafeBitcoinSerialize());

			peer.setBloomFilter(filter);

			peer.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {

				@Override
				public void onBlocksDownloaded(Peer peer, Block block,
											   @Nullable FilteredBlock filteredBlock, int blocksLeft) {
					System.out.println("Peer: " + peer.toString());
					System.out.println("execute onblocksdownloaded listener on block + " + filteredBlock.getBlockHeader().getHashAsString());
					List<Sha256Hash> matchedHashesOut = new ArrayList<>();
					PartialMerkleTree tree = filteredBlock.getPartialMerkleTree();
					Sha256Hash merkleroot = tree.getTxnHashAndMerkleRoot(matchedHashesOut);
					System.out.println("DEBUG: merkleroot:" + merkleroot.toString());
					if (!matchedHashesOut.isEmpty()) {
						System.out.println(matchedHashesOut.get(0));
					}
					try {
						System.out.println("DEBUG: Hashes: " + matchedHashesOut.contains(getLastTransaction().getHash()));
						System.out.println("Hashes should contain " + getLastTransaction().getHash());
						System.out.println("DEBUG: merkleroot blockHeader " + merkleroot.equals(filteredBlock.getBlockHeader().getMerkleRoot()));
						System.out.println("DEBUG: merkleroot blockstore" + merkleroot.equals(blockstore.get(filteredBlock.getBlockHeader().getHash()).getHeader().getMerkleRoot()));
						System.out.println("DEBUG: blocksLeft is " + blocksLeft);
						if(matchedHashesOut.contains(getLastTransaction().getHash()) &&
								merkleroot.equals(filteredBlock.getBlockHeader().getMerkleRoot()) &&
								merkleroot.equals(blockstore.get(filteredBlock.getBlockHeader().getHash()).getHeader().getMerkleRoot())) {
							System.out.println("DEBUG: isTxInBlockchain TRUE");
							isTxInBlockchain.setMonitorState(true);
						}
					} catch (BlockStoreException e) {
						e.printStackTrace();
					}

					synchronized (monitor) {
						monState.setMonitorState(false);
						System.out.println("DEBUG: isTxInBlockchain Finished!");
						monitor.notifyAll(); // unlock again
					}
					peer.removeBlocksDownloadedEventListener(this);
				}
			});
			peer.sendMessage(msg);
		}
		System.out.println("send getdatamessage to verify tx is in blockchain");
		waitForData(true);
		//return when finished
		monState.setMonitorState(true);
		while(monState.getMonitorState()) {
			synchronized (monitor) {
				try {
					monitor.wait();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		waitForData(false);
		System.out.println("DEBUG: isTxInBlockschain " + isTxInBlockchain.getMonitorState());
		System.out.println("DEBUG: completed send getdatamessage to verify tx is in blockchain");
		return isTxInBlockchain.getMonitorState();
	}
	
	//neither blockchain nor blockstore allow retrieval of a block by height
	//so we need to do this ourselves
	private Sha256Hash getBlockHashByHeight(BlockChain bc, int appearedInChainheight2) {
		int bestchainheight = bc.getBestChainHeight();
		StoredBlock block = bc.getChainHead();
		assert(block != null);
		for(int i=0; i < bestchainheight-appearedInChainheight2; i++) {
			try {
				//System.out.println("iteration: " + i);
				assert(block != null);
				block = block.getPrev(bc.getBlockStore());
			} catch (BlockStoreException e) {
				e.printStackTrace();
			}
		}
		return block.getHeader().getHash();
	}

	public boolean isValidProof(BlockChain bc, PeerGroup pg, NetworkParameters params) {
		if(!isValidPath()) {
			System.out.println("path is not valid, thus proof not valid, abort");
			return false;
		}
		if(!isValidGPTx()) {
			System.out.println("gptx is not valid, abort");
			return false;
		}
		if(!isNymNotSpend(bc)) {
			System.out.println("nym is not locked anymore, consider spent, abort");
			return false;
		}
		if(!isNymTxInBlockChain(params, bc, pg)) {
			System.out.println("nymtx is not in blockchain, consider proof invalid, abort");
			return false;
		}
		System.out.println("proof seems valid");
		return true;
	}
	
	//when we want to create a mixtx, the outputs are not locked, so we do not know for sure whether the outputs are utxo
	//until we try to broadcast the mixtx
	public boolean isProbablyValid(NetworkParameters params, BlockChain bc, PeerGroup pg) {
		return isValidPath() && isValidGPTx() && isNymTxInBlockChain(params, bc, pg);
	}
	
	public void addTransaction(final Transaction tx, int index, CLTVScriptPair sp) {
		validationPath.add(tx);
		outputIndices.add(new Integer(index));
		this.sp = sp;
		this.appearedInChainheight = 0; 
		tx.getConfidence().addEventListener(new Listener() {
			
			@Override
			public void onConfidenceChanged(TransactionConfidence confidence,
					ChangeReason reason) {
				//TODO make sure that listener is only registered once, and not in many different places
				if(confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
					appearedInChainheight = confidence.getAppearedAtChainHeight();
					for(ProofConfidenceChangeEventListener l : proofConfidenceChangeListeners) {
						l.onProofConfidenceChanged();
					}
					tx.getConfidence().removeEventListener(this);
					System.out.println("call confidence listener, set appearedinchainheight to " + appearedInChainheight);
				}
				
			}
		});
		for(ProofChangeEventListener l : proofChangeListeners) {
			l.onProofChanged();
		}
	}
	
	public Transaction getLastTransaction() {
		if (validationPath.size() > 0) {
			return validationPath.get(validationPath.size()-1);
		} else {
			return null;
		}
	}
	
	//TODO throw exception instead of -1, or assertion that caller assure that a tx exists?
	public int getLastOutputIndex() {
		if (validationPath.size() > 0) {
			assert(outputIndices.get(outputIndices.size()-1).intValue() <= 2);
			return outputIndices.get(outputIndices.size()-1).intValue();
		} else {
			return -1;
		}
	}
	
	public TransactionOutput getLastTransactionOutput() {
		return this.getLastTransaction().getOutput(getLastOutputIndex());
	}
	
	private void writeObject(ObjectOutputStream oos)
			throws IOException {
			    //List<byte[]> txs = new ArrayList<byte[]>();
			    //List<Integer> intList = new ArrayList<Integer>();
		System.out.println("serialize proof message, appearedinchainheight is " + this.appearedInChainheight);
		oos.writeInt(appearedInChainheight);
		oos.writeObject(sp);
		for(Integer i : this.outputIndices) {
			oos.writeObject(i);
		}
		for(Transaction tx : this.validationPath) {
			oos.writeObject(tx.bitcoinSerialize());
		}

	}

	private void readObject(ObjectInputStream ois)
			throws ClassNotFoundException, IOException {

		proofConfidenceChangeListeners = new ArrayList<ProofConfidenceChangeEventListener>();
		proofChangeListeners = new ArrayList<ProofChangeEventListener>();
		this.waitForDataListeners = new ArrayList<WaitForDataListener>();
		List vPath, oIndices;
		List<Transaction> txList = new ArrayList<Transaction>();
		List<Integer> intList = new ArrayList<Integer>();
		List<Object> l = new ArrayList<Object>();
		BitcoinSerializer bs = new BitcoinSerializer(MainClass.params, false);
		
		appearedInChainheight = ois.readInt();

		Object a = ois.readObject();
		try {
			for (;;)
			{
				Object o = ois.readObject();
				l.add(o);
			}
		} catch (SocketTimeoutException exc) {
		    // you got the timeout
		} catch (EOFException exc) {
		    // end of stream
		} catch (IOException exc) {
		    // some other I/O error: print it, log it, etc.
		    //exc.printStackTrace(); // for example
		}

		oIndices = l.subList(0, l.size()/2);
		vPath = l.subList(l.size()/2, l.size());

		for(Object i : oIndices) {
			intList.add((Integer) i);
		}
		for(Object o : vPath) {
			Transaction rcvdTx = null;
			try {
				rcvdTx = bs.makeTransaction((byte[]) o);
				//rcvdTx = (Transaction) bs.deserialize(bb);
				txList.add(rcvdTx);
			} catch (ProtocolException e) {
				e.printStackTrace();
			} //catch (IOException e) {
			//	e.printStackTrace();
			//}
		}

		validationPath = txList;
		outputIndices = intList;
		sp = (CLTVScriptPair) a;
		if(appearedInChainheight == 0 && validationPath.size() > 0) {
			//if appearedInHeight isn't set yet, register listener, which sets at it, when the tx
			//appears in the block chain
			if(getLastTransaction().hasConfidence() && getLastTransaction().getConfidence().getConfidenceType().equals(ConfidenceType.BUILDING)) {
				appearedInChainheight = getLastTransaction().getConfidence().getAppearedAtChainHeight();
				System.out.println("set appeared in chainheight without listener to " + appearedInChainheight);
			} else {
				System.out.println("has no confidence, so set confidence listener to set the appeared in chainheight later on");
				getLastTransaction().getConfidence().addEventListener(new Listener() {

					@Override
					public void onConfidenceChanged(TransactionConfidence confidence,
							ChangeReason reason) {
						if(confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
							appearedInChainheight = confidence.getAppearedAtChainHeight();
							System.out.println("call confidence listener, set appearedinchainheight to " + appearedInChainheight);
							for(ProofConfidenceChangeEventListener l : proofConfidenceChangeListeners) {
								l.onProofConfidenceChanged();
							}
							getLastTransaction().getConfidence().removeEventListener(this);
						}

					}
				});
			}
		}
	}

	public boolean isEmpty() {
		return this.validationPath.size() == 0;
		
	}
	
	public CLTVScriptPair getScriptPair() {
		return this.sp;
	}
	
	@Override
	public String toString() {
		String retValue = null;
		StringBuilder sb = new StringBuilder();
		sb.append(this.sp.toString());
		sb.append("----------\n");
		for(Transaction tx : this.validationPath) {
			sb.append(tx.toString());
			sb.append("----------\n");
		}
		retValue = sb.toString();
		return retValue;
	}

	//TODO move file management of the proof message into this class
	public void writeToFile() {
		try {
			log.info("try saving proof message to disk");
			File file = new File(filePath);
			if(!file.exists()) {
				file.createNewFile();
			}
			FileOutputStream fout = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(this);
			oos.close();
			fout.close();
			log.info("saved proof message to file: " + filePath);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		if(appearedInChainheight == 0 && validationPath.size() > 0) {
//			if(getLastTransaction().hasConfidence() && getLastTransaction().getConfidence().getConfidenceType().equals(ConfidenceType.BUILDING)) {
//				appearedInChainheight = getLastTransaction().getConfidence().getAppearedAtChainHeight();
//				System.out.println("set appeared in chainheight without listener to " + appearedInChainheight);
//			} else {
//				System.out.println("has no confidence, so set confidence listener to set the appeared in chainheight later on");
//				getLastTransaction().getConfidence().addEventListener(new Listener() {
//
//					@Override
//					public void onConfidenceChanged(TransactionConfidence arg0,
//							ChangeReason arg1) {
//						if(arg0.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
//							appearedInChainheight = arg0.getAppearedAtChainHeight();
//							System.out.println("call confidence listener, set appearedinchainheight to " + appearedInChainheight);
//							for(ProofConfidenceChangeEventListener l : proofConfidenceChangeListeners) {
//								l.onProofConfidenceChanged();
//							}
//							getLastTransaction().getConfidence().removeEventListener(this);
//						}
//
//					}
//				});
//			}
//		}	
		for(ProofChangeEventListener l : proofChangeListeners) {
			l.onProofChanged();
		}
		
	}
	
	public void setFilePath(String fp) {
		this.filePath = fp;
	}
	
	public String getFilePath() {
		return this.filePath;
	}
	

	public void addProofConfidenceChangeEventListener(ProofConfidenceChangeEventListener listener) {
		proofConfidenceChangeListeners.add(listener);
	}
	
	public void removeProofConfidenceChangeEventListener(ProofConfidenceChangeEventListener listener) {
		proofConfidenceChangeListeners.remove(listener);
	}

	public void addProofChangeEventListener(ProofChangeEventListener listener) {
		this.proofChangeListeners.add(listener);
	}

	public void removeProofChangeEventListener(
			ProofChangeEventListener listener) {
		this.proofChangeListeners.remove(listener);
	}
	
	public List<ProofChangeEventListener> getProofChangeEventListeners() {
		return this.proofChangeListeners;
	}
	
	public void setProofChangeEventListeners(List<ProofChangeEventListener> listeners) {
		this.proofChangeListeners = listeners;
	}
	
	public List<ProofConfidenceChangeEventListener> getProofConfidenceChangeEventListeners() {
		return this.proofConfidenceChangeListeners;
	}
	
	public void setProofConfidenceChangeEventListeners(List<ProofConfidenceChangeEventListener> listeners) {
		this.proofConfidenceChangeListeners = listeners;
	}
	public void addWaitForDataListener(WaitForDataListener listener) {
		this.waitForDataListeners.add(listener);
	}
	private void waitForData(boolean waiting) {
		for (WaitForDataListener listener : waitForDataListeners) {
			listener.waitForData(waiting);
		}
	}

}
