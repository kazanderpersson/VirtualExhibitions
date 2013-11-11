import jade.core.Agent;

public class MyAgent extends Agent {
	protected void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");
	}

}
