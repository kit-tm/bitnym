package gui;

public class ConsoleController {
	
	
	private ConsoleView consoleView;


	public void loadView() {
		consoleView = new ConsoleView();
	}
	
	
	public ConsoleView getView() {
		return this.consoleView;
	}

}
