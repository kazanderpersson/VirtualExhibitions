import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class CuratorAgent extends Agent {
	@Override
	protected void setup() {

		addBehaviour(new listenBehaviour());
	}
	
	class listenBehaviour extends CyclicBehaviour {
		public void action() {
			
			ACLMessage msg = myAgent.receive();
			if (msg != null) {
				if (msg.getContent().equals("ping")) {
						ACLMessage msgBack = new ACLMessage(ACLMessage.INFORM);
						msgBack.addReceiver(msg.getSender());
						msgBack.setLanguage("English");
						msgBack.setOntology("ping-message");
						msgBack.setContent("ping");
						send(msgBack);
				}
			}
			else {
			block();
			}
			

			
		}
}
}