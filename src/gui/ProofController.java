package gui;

import bitnymWallet.BitNymWallet;

public class ProofController {

	
	private ProofView proofView;
	private BitNymWallet wallet;

	public void loadView() {
		proofView = new ProofView();
		proofView.getDisplay().setText(wallet.getProofMessageString());
	}
	
	public ProofView getView() {
		return this.proofView;
	}
	
	public void loadWallet(BitNymWallet wallet) {
		this.wallet = wallet;
	}
}
