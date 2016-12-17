import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;




public class ProofMessage implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private List<Transaction> validationPath;
	private List<Integer> outputIndices;
	
	public ProofMessage() {
		this.validationPath = new ArrayList<Transaction>();
		this.outputIndices = new ArrayList<Integer>();
	}
	
	public ProofMessage(List<Transaction> vP, List<Integer> oIdices) {
		this.validationPath = vP;
		this.outputIndices = oIdices;
		assert(vP.size() == oIdices.size());
	}
	
	public boolean isValidPath() {
		return false;
	}
	
	public boolean isValidGPTx() {
		return false;
	}
	
	public boolean isNymNotSpend() {
		return false;
	}
	
	public boolean isNymTxInBlockChain() {
		return false;
	}
	
	public boolean isValidProof() {
		return true;
		//return isValidPath() && isValidGPTx() &&
		//		isNymNotSpend() && isNymTxInBlockChain();
	}
	
	public void addTransaction(Transaction tx, int index) {
		validationPath.add(tx);
		outputIndices.add(new Integer(index));
	}
	
	public Transaction getLastTransaction() {
		if (validationPath.size() > 0) {
			return validationPath.get(validationPath.size()-1);
		} else {
			return null;
		}
	}
	
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
			    List<byte[]> txs = new ArrayList<byte[]>();
			    for(Transaction tx : this.validationPath) {
			    	txs.add(tx.bitcoinSerialize());
			    }
			    oos.writeObject(txs);
			    for(Integer i : this.outputIndices) {
			    	oos.writeInt(i);
			    }
	}

	private void readObject(ObjectInputStream ois)
			throws ClassNotFoundException, IOException {
			    List<Object> loc = (List<Object>)ois.readObject();
			    List vPath, oIndices;
			    List<Transaction> txList = new ArrayList<Transaction>();
			    List<Integer> intList = new ArrayList<Integer>();
			    vPath = loc.subList(0, loc.size()/2);
			    oIndices = loc.subList(loc.size()/2, loc.size());
			    BitcoinSerializer bs = new BitcoinSerializer(MainClass.params, false);
			    for(Object i : vPath) {
			    	ByteBuffer bb = ByteBuffer.wrap((byte[]) i);
					Transaction rcvdTx = null;
					try {
						rcvdTx = (Transaction) bs.deserialize(bb);
						txList.add(rcvdTx);
					} catch (ProtocolException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			    }
			    for(Object i : oIndices) {
			    	intList.add((Integer) i);
			    }
			    validationPath = txList;
			    outputIndices = intList;
	}

}
