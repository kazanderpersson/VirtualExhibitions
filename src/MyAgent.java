import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;

public class MyAgent extends Agent {
	protected void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");

		System.out.println("Hello, this line was edited by Andreas.......");
		addBehaviour(new PrintStuffBehaviour());
	}
	
	private class PrintStuffBehaviour extends CyclicBehaviour {

		@Override
		public void action() {
			System.out.println("Some cyclic behaviour...");
		}
	}
}
