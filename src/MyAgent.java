import java.util.Calendar;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.SimpleAchieveREResponder;
import jade.proto.states.MsgReceiver;

public class MyAgent extends Agent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");
		
		MessageTemplate mt = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST);
		addBehaviour(new SimpleAchieveREResponderImpl(this, mt));
		
		
		/*OneShotBehaviour test = new OneShotBehaviour() {

			@Override
			public void action() {
				System.out.println("oneshat");
			}
			
		};
		
		addBehaviour(test);*/
		
		//addBehaviour(new ListenBehaviour());
		//addBehaviour(new MsgRecBehaviour(this, Calendar.getInstance().getTimeInMillis()+5000));
		

	}
	
	private class SimpleAchieveREResponderImpl extends SimpleAchieveREResponder {
		public SimpleAchieveREResponderImpl(Agent a, MessageTemplate mt) {
			super(a,mt);
		}
		
		protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
			ACLMessage informDone = request.createReply();
			if (request.getContent().equals("what is the time?")) {
				informDone.setPerformative(ACLMessage.INFORM);
				informDone.setContent(Calendar.getInstance().getTime().toString());
			}
			else
				informDone.setPerformative(ACLMessage.NOT_UNDERSTOOD);
			return informDone;
			
		}
		
		protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException {
			return super.prepareResponse(request);
		}
		
	}
	
	private class ListenBehaviour extends CyclicBehaviour {

		@Override
		public void action() {
			ACLMessage msg = myAgent.blockingReceive(MessageTemplate.MatchOntology("test"));
			if (msg != null) {
				System.out.println(msg.getContent());
				//myAgent.addBehaviour(new singleMessageBehaviour());
			}
				
		}
	}
	
	private class MsgRecBehaviour extends MsgReceiver {
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
