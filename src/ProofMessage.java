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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class ProofMessage implements Serializable {
	/**
	 * 
	 */
	
	private static final Logger log = LoggerFactory.getLogger(ProofMessage.class);
	//TODO add event listener for last transaction if blockheight is not at least 1
	private static final long serialVersionUID = 1L;
	private String filePath;
	private List<Transaction> validationPath;
	private List<Integer> outputIndices;
	private CLTVScriptPair sp;
	
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
	}
	
	//use certain proof message file
	public ProofMessage(String path) {
		//determines the corresponding output within the the mix txs
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
			   ois.close();
			   fin.close();
			   log.info("completed reading in proof message file");
			}
			
		} catch (FileNotFoundException e4) {
			// TODO Auto-generated catch block
			e4.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public ProofMessage(List<Transaction> vP, List<Integer> oIdices) {
		this.validationPath = vP;
		this.outputIndices = oIdices;
		
		
		assert(vP.size() == oIdices.size());
	}
	
	//TODO Constructor with certain path string or file in constructor
	
	public boolean isValidPath() {
		//check that the transaction build a path and are not just random txs, 
		//by checking the tx hashes with those of the outpoints
		for(int i=validationPath.size(); i > 1; i--) {
			Transaction tx = validationPath.get(i);
			if(!tx.getInput(outputIndices.get(i)).getOutpoint().getHash().equals(validationPath.get(i-1).getHash())) {
				return false;
			}
		}
		
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
			return false;
		}
		
		if(!tx.getOutput(0).getScriptPubKey().isOpReturn() ||
				tx.getOutput(0).getValue().isLessThan(MainClass.PROOF_OF_BURN)) {
			return false;
		}
		
		return true;
	}

	//important: my still be utxo, the locking is just a sufficient condition, not necessary
	//for clients with have the utxo set (eg. full node clients)
	public boolean isNymNotSpend(BlockChain bc) {
		//check locktime and compare to current bip113 time
		//we only accept p2sh
		if(!getLastTransactionOutput().getScriptPubKey().isPayToScriptHash()) {
			return false;
		}
		
		//check that redeem script matches the p2sh output hash
		if(!ScriptBuilder.createP2SHOutputScript(sp.getRedeemScript()).equals(getLastTransactionOutput().getScriptPubKey())) {
			return false;
		}
		
		//check that the redeem script is the one we specified and check that it is still locked
		if(!sp.isRedeemScriptRightFormat() || !sp.isLocked(bc)) {
			return false;
		}
		return true;
	}
	
	public boolean isNymTxInBlockChain() {
		//get filtered block with transaction and check merkel tree
		return false;
	}
	
	public boolean isValidProof(BlockChain bc) {
		return true;
		//return isValidPath() && isValidGPTx() &&
		//		isNymNotSpend(bc) && isNymTxInBlockChain();
	}
	
	//when we want to create a mixtx, the outputs are not locked, so we do not know for sure whether the outputs are utxo
	//until we try to broadcast the mixtx
	public boolean isProbablyValid() {
		return isValidPath() && isValidGPTx() && isNymTxInBlockChain();
	}
	
	public void addTransaction(Transaction tx, int index, CLTVScriptPair sp) {
		validationPath.add(tx);
		outputIndices.add(new Integer(index));
		this.sp = sp;
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

		List vPath, oIndices;
		List<Transaction> txList = new ArrayList<Transaction>();
		List<Integer> intList = new ArrayList<Integer>();
		List<Object> l = new ArrayList<Object>();
		BitcoinSerializer bs = new BitcoinSerializer(MainClass.params, false);
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			} //catch (IOException e) {
				// TODO Auto-generated catch block
			//	e.printStackTrace();
			//}
		}

		validationPath = txList;
		outputIndices = intList;
		sp = (CLTVScriptPair) a;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void setFilePath(String fp) {
		this.filePath = fp;
	}
	
	public String getFilePath() {
		return this.filePath;
	}

}
