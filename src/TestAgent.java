import java.util.Calendar;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class TestAgent extends Agent {

	public void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");
		addBehaviour(new sendBehaviour(this, 2000));
	}
	
	class sendBehaviour extends TickerBehaviour {
		sendBehaviour(Agent a, long interval) {
			super(a, interval);
		}
		@Override
		public void onTick() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(new AID("recieveagent", AID.ISLOCALNAME));
			msg.setOntology("test");
			msg.setContent("ping");
			send(msg);
		}
	}
}
