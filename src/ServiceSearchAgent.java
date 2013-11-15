import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.proto.SubscriptionInitiator;


/**
 *	This agent will subscribe to two services, 
 *	and print some information every time someone publishes those services.
 *
 *	It will also print all available services, ask the user for its' choice, 
 *	and print the parameters to use the selected service.
 *
 *	Behaviours:
 *		Print available services and wait for user input.
 */
public class ServiceSearchAgent extends Agent {
	@Override
	protected void setup() {
		subscribeToService("artifact-lookup");
		subscribeToService("give-tour");
		
		/*********  Spaghetti code, yay!  **********/
		addBehaviour(new CyclicBehaviour(this) {
			@Override
			public void action() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("___________________________________________");
				System.out.println(getName() + ": Available services: ");
				ArrayList<String> services = avaliableServices();
				for(String s : services)
					System.out.println(s.split("::")[0]);
				System.out.println("___________________________________________");
				
				System.out.println("To see parameters, enter the name of the service: ");
				Scanner scan = new Scanner(System.in);
				String n = scan.nextLine();
				String args = "not found";
				for(String s : services) {
					String sName = s.split("::")[0];
					String sArgs = s.split("::")[1];
					if(n.equals(sName))
						args = sArgs;
				}
				scan.close();
				System.out.println(args);
				/******  End of spaghetti.  **********/
			}
		});
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
					if(!services.contains(service)) {
						String prop = "";
						if(sd.getAllProperties().hasNext())
							prop = "" + ((Property)sd.getAllProperties().next()).getValue();
						services.add(service + "::" + prop);
//						System.out.println("Some property: " + ((Property)sd.getAllProperties().next()).getValue());
					}
				}
			}
				
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		
		return services;
	}
}
