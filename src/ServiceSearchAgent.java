import java.util.ArrayList;
import java.util.Iterator;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.proto.SubscriptionInitiator;


public class ServiceSearchAgent extends Agent {
	@Override
	protected void setup() {
		subscribeToService("artifact-lookup");
		subscribeToService("give-tour");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("___________________________________________");
		System.out.println(getName() + ": Available services: ");
		ArrayList<String> services = avaliableServices();
		for(String s : services)
			System.out.println(s);
		System.out.println("___________________________________________");
		
	}
	
	private void subscribeToService(final String service) {
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(service);		//Look for this service to be published..
		template.addServices(sd);
		
		Behaviour b = new SubscriptionInitiator(this, DFService.createSubscriptionMessage(this, getDefaultDF(), template, null)) {
			@Override
			protected void handleInform(ACLMessage inform) {
				try {
					DFAgentDescription[] dfds = DFService.decodeNotification(inform.getContent());
					System.out.println(myAgent.getName() + ": " + dfds[0].getName().getName() + " published the \""+ service + "\" service (Or was terminated...?).");
				} catch (FIPAException e) {
					e.printStackTrace();
				}
			}
		};
		
		addBehaviour(b);
	}
	
	private ArrayList<String> avaliableServices() {
		ArrayList<String> services = new ArrayList<>();
		DFAgentDescription dfd = new DFAgentDescription();
		SearchConstraints ALL = new SearchConstraints();
		ALL.setMaxResults(new Long(-1));
		try {
			DFAgentDescription[] result = DFService.search(this, dfd, ALL);
			for(DFAgentDescription d : result) {
				Iterator it = d.getAllServices();
				while(it.hasNext()) {
					ServiceDescription sd = (ServiceDescription) it.next();
					String service = sd.getType();
					if(!services.contains(service))
						services.add(service);
				}
			}
				
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		
		return services;
	}
}
