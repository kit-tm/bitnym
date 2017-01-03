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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;




public class ProofMessage implements Serializable {
	/**
	 * 
	 */
	
	//TODO add event listener for last transaction if blockheight is not at least 1
	private static final long serialVersionUID = 1L;
	private String filePath;
	private List<Transaction> validationPath;
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

	private List<Integer> outputIndices;
	
	public ProofMessage() {
		this.validationPath = new ArrayList<Transaction>();
		
		//determines the corresponding output within the the mix txs
		this.outputIndices = new ArrayList<Integer>();
		this.filePath = System.getProperty("user.dir") + "/proofmessage.pm";
		try {
			File file = new File(filePath);
			if(file.exists()) {  
			   FileInputStream fin = new FileInputStream(file);
			   ObjectInputStream ois = new ObjectInputStream(fin);
			   ProofMessage tmp = (ProofMessage) ois.readObject();
			   this.outputIndices = tmp.outputIndices;
			   this.validationPath = tmp.validationPath;
			   ois.close();
			   fin.close();
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
			    //List<byte[]> txs = new ArrayList<byte[]>();
			    //List<Integer> intList = new ArrayList<Integer>();
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
	}

	public boolean isEmpty() {
		return this.validationPath.size() == 0;
		
	}
	
	@Override
	public String toString() {
		String retValue = null;
		StringBuilder sb = new StringBuilder();
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
			File file = new File(filePath);
			if(!file.exists()) {
				file.createNewFile();
			}
			FileOutputStream fout = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(this);
			oos.close();
			fout.close();
			System.out.println("saved proof message to file ");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
