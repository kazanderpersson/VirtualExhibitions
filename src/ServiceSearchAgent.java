import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.proto.SubscriptionInitiator;


public class ServiceSearchAgent extends Agent {
	@Override
	protected void setup() {
		subscribeToService();
	}
	
	private void subscribeToService() {
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("artifact-lookup");		//Look for this service to be published..
		template.addServices(sd);
		
		Behaviour b = new SubscriptionInitiator(this, DFService.createSubscriptionMessage(this, getDefaultDF(), template, null)) {
			@Override
			protected void handleInform(ACLMessage inform) {
				try {
					DFAgentDescription[] dfds = DFService.decodeNotification(inform.getContent());
					System.out.println(dfds[0].getName().getName() + " published the \"artifact-lookup\" service (Or was terminated...?).");
				} catch (FIPAException e) {
					e.printStackTrace();
				}
			}
		};
		
		addBehaviour(b);
	}
}
