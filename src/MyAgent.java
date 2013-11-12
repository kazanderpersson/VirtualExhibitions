import java.util.Calendar;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.states.MsgReceiver;

public class MyAgent extends Agent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");
		
		OneShotBehaviour test = new OneShotBehaviour() {

			@Override
			public void action() {
				System.out.println("oneshat");
			}
			
		};
		
		addBehaviour(test);
		
		//addBehaviour(new ListenBehaviour());
		addBehaviour(new MsgRecBehaviour(this, Calendar.getInstance().getTimeInMillis()+5000));
		

	}
	
	class ListenBehaviour extends CyclicBehaviour {

		@Override
		public void action() {
			ACLMessage msg = myAgent.blockingReceive(MessageTemplate.MatchOntology("test"));
			if (msg != null) {
				System.out.println("message received!");
				System.out.println(msg.getContent());
				myAgent.addBehaviour(new singleMessageBehaviour());
			}
				
		}
	}
	
	class MsgRecBehaviour extends MsgReceiver {
		public MsgRecBehaviour(Agent a, long deadline) {
			super(a, MessageTemplate.MatchOntology("test"), deadline, new DataStore(), "key");
		}
		public void handleMessage(ACLMessage message) {
			if (message == null)
				System.out.println("timeout");
			else {
				System.out.println(message.getContent());
				//addBehaviour(new MsgRecBehaviour(myAgent, Calendar.getInstance().getTimeInMillis()+5000));
			}
		}
	}
	class singleMessageBehaviour extends OneShotBehaviour {

		@Override
		public void action() {
			System.out.println("oneshot");
			
		}
		
	}

}
