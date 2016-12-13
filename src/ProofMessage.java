import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
		this.validationPath = new ArrayList<>();
		this.outputIndices = new ArrayList<>(); 
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
		return validationPath.get(validationPath.size()-1); 
	}
	
	public int getLastOutputIndex() {
		return outputIndices.get(outputIndices.size()-1).intValue();
	}
	
	public TransactionOutput getLastTransactionOutput() {
		return this.getLastTransaction().getOutput(getLastOutputIndex());
	}

}
