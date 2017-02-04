package gui;

public class ProofController {

	
	private ProofView proofView;

	public void loadView() {
		proofView = new ProofView();
	}
	
	public ProofView getView() {
		return this.proofView;
	}
}
