import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;


/**
 * Behaviours: 
 *		StartTourBehaviour extends Ticker/Cyclic
 *		FetchTourInformationBehaviour - SequentialBehaviour?
 *					AskForInterestingItems - OneShot
 *					ReceiveInterestingItems - MsgReceiver
 *					AskForItemInformation - OneShot
 *					ReceiveItemInformation - MsgReceiver
 *		
 */
public class ProfilerAgent extends Agent {
	@Override
	protected void setup() {
		addBehaviour(new SendPingBehaviour(this, 2000));
		addBehaviour(new ReceivePingResponseBehaviour());
	}
	
	private class SendPingBehaviour extends TickerBehaviour {

		public SendPingBehaviour(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(new AID("bob", AID.ISLOCALNAME));
			msg.setLanguage("English");
			msg.setOntology("ping-message");
			msg.setContent("ping");
			send(msg);
			System.out.println("Sending ping to bob.");
		}		
	}
	
	private class ReceivePingResponseBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			ACLMessage msg = receive();
			if(msg != null) {
				System.out.println(myAgent.getName() + " received a ping response. Content=" + msg.getContent());
			} else
				block();
		}	
	}

}
