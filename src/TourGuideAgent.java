import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;


/**
 * 	Sequential... 
	 *	ReceiveInterestingItemsRequestBehaviour - MsgReceiver
	 *	AskCuratorForInterestingItems - OneShot
	 *	ReceiveCuratorAnswer - MsgReceiver
	 *	SendAnswerToTourGuide - OneShot
 *....
 *
 */
public class TourGuideAgent extends Agent{
	
	private ArrayList<Integer> itemIDs;
	private Profile profile;
	private AID profilerAgent;
	
	public static final String TOUR_GUIDE_NAME = "tourguide";
	private final int TOUR_SIZE = 5;
	
	private String conversationID;
	
	private static final String STATE_RECEIVE_FROM_PROFILER = "A";
	private static final String STATE_SEND_TO_CURATOR = "B";
	private static final String STATE_RECEIVE_FROM_CURATOR = "C";
	private static final String STATE_RESPOND_TO_PROFILER = "D";
	
	@Override
	protected void setup() {
//		addBehaviour(new HandleTourRequestBehaviour());
		
		FSMBehaviour fsm = new FSMBehaviour(this);
		fsm.registerFirstState(new ReceiveTourRequest(this, MsgReceiver.INFINITE), STATE_RECEIVE_FROM_PROFILER);
		fsm.registerState(new SendArtifactRequest(), STATE_SEND_TO_CURATOR);
		fsm.registerState(new ReceiveArtifactIDs(this, MsgReceiver.INFINITE), STATE_RECEIVE_FROM_CURATOR);
		fsm.registerState(new SendItemIDs(), STATE_RESPOND_TO_PROFILER);
		
		fsm.registerDefaultTransition(STATE_RECEIVE_FROM_PROFILER, STATE_SEND_TO_CURATOR);
		fsm.registerDefaultTransition(STATE_SEND_TO_CURATOR, STATE_RECEIVE_FROM_CURATOR);
		fsm.registerDefaultTransition(STATE_RECEIVE_FROM_CURATOR, STATE_RESPOND_TO_PROFILER);
		fsm.registerDefaultTransition(STATE_RESPOND_TO_PROFILER, STATE_RECEIVE_FROM_PROFILER);
		addBehaviour(fsm);
		
		/*****************************************************************/
		ServiceDescription giveTour = new ServiceDescription();
		giveTour.setType("give-tour");
		giveTour.setName("get-tour");
		giveTour.addOntologies("get-tour-guide");
		Property args = new Property("args", "Send a Profile and use the ontology: get-tour-guide");
		giveTour.addProperties(args);
		register(giveTour);
		/****************************************************************/
	}
	
	/**
	 * Register this agent with the DF, and publish the given Service
	 */
	private void register(ServiceDescription sd) {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
			//System.out.println(getName() + ": Successfully registered service " + sd.getName());
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}
	
//	private class HandleTourRequestBehaviour extends CyclicBehaviour {
//
//		@Override
//		public void action() {
//			try {
//				System.out.println("Waiting for Tour Request from ProfilerAgent.");
//				//Receive message from ProfilerAgent
//				ACLMessage profilerRequest = blockingReceive(MessageTemplate.MatchOntology("get-tour-guide"));
//				AID profiler = profilerRequest.getSender();
//				conversationID = profilerRequest.getConversationId();
//				profile = (Profile) profilerRequest.getContentObject();
//				
//				
//				//Send request to CuratorAgent
//				AID curator = new AID(CuratorAgent.CURATOR_NAME, AID.ISLOCALNAME);
//				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
//				msg.addReceiver(curator);
//				msg.setOntology("request-ids");
//				msg.setConversationId(conversationID);
//				msg.setContentObject(profile.getInterests());
//				send(msg);
//				
//				
//				//Receive response from CuratorAgent
//				ACLMessage curatorResponse = blockingReceive(MessageTemplate.MatchOntology("get-artifact-ids").MatchConversationId(conversationID));
//				itemIDs = (ArrayList<Integer>) curatorResponse.getContentObject();
//				
//				
//				//Send the IDs to the ProfilerAgent
//				ArrayList<Integer> idsToSend = new ArrayList<>();
//				for(Integer id : itemIDs) {
//					boolean idIsVisited = profile.getVisitedItemsID().contains(id);
//					boolean tourIsFull = idsToSend.size() > TOUR_SIZE;
//					boolean idAlreadyInTour = idsToSend.contains(id);
//					
//					if(!idIsVisited && !tourIsFull && !idAlreadyInTour)
//						idsToSend.add(id);
//				}
//				
//				//Send the IDs to the ProfilerAgent
//	//			AID profiler = new AID(ProfilerAgent.PROFILER_NAME, AID.ISLOCALNAME);
//				ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
//				reply.addReceiver(profiler);
//				reply.setOntology("tour-ids");
//				reply.setContentObject(idsToSend);
//				send(reply);
//				System.out.println("Request was handled and a response have been sent to the Profiler.");
//			} catch (UnreadableException e) {
//				e.printStackTrace();
//				System.err.println(myAgent.getAID().getName() + ": Could not decode message. Aborting.");
//			} catch (IOException e) {
//				e.printStackTrace();
//				System.err.println(myAgent.getAID().getName() + ": Could not serialize some object... Aborting.");
//			}
//		}
//	}
	
	/**
	 *	This behavior is used to wait for requests from a ProfilerAgent.
	 */
	private class ReceiveTourRequest extends MsgReceiver {
		public ReceiveTourRequest(Agent a,long deadline) {
			super(a, MessageTemplate.MatchOntology("get-tour-guide"), deadline, new DataStore(), "key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			try {
				//System.out.println("Waiting for Tour Request from ProfilerAgent.");
				//Receive message from ProfilerAgent
				profilerAgent = msg.getSender();
				conversationID = msg.getConversationId();
				profile = (Profile) msg.getContentObject();
				//System.out.println(getName() + ": Received a profile with name: " + profile.getName());
			} catch (UnreadableException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not read profile. Terminating...");
				myAgent.doDelete();
			}
		}
	}
	
	/**
	 *	This will send a message to the CuratorAgent, and ask it for some interesting artifacts.
	 */
	private class SendArtifactRequest extends OneShotBehaviour {
		@Override
		public void action() {
			try {
				//Send request to CuratorAgent
				AID curator = new AID(CuratorAgent.CURATOR_NAME, AID.ISLOCALNAME);
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.addReceiver(curator);
				msg.setOntology("request-ids");
				msg.setConversationId(conversationID);
				msg.setContentObject(profile.getInterests());
				send(msg);
				//System.out.println(getName() + ": Sent request to Curator. Number of interests= " + profile.getInterests().size());
			} catch (IOException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not serialize interest list. Terminating...");
				myAgent.doDelete();
			}
		}
	}
	
	/**
	 *	Wait for the Curator to reply with some interesting artifact IDs.
	 */
	private class ReceiveArtifactIDs extends MsgReceiver {
		public ReceiveArtifactIDs(Agent a,long deadline) {
			super(a, MessageTemplate.MatchOntology("get-artifact-ids"), deadline, new DataStore(), "key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			try {
				//Receive response from CuratorAgent
				//System.out.println(getName() + ": Waiting for curator....");
				itemIDs = (ArrayList<Integer>) msg.getContentObject();
				//System.out.println(getName() + ": Received response from Curator, number of IDs = " + itemIDs.size());
				
			} catch (UnreadableException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not read item IDs. Terminating...");
				myAgent.doDelete();
			}
		}
	}
	
	/**
	 *	Look through the list of interesting IDs, and apply some filters to remove duplicates and remove items that are already visited. 
	 *	Then send the resulting IDs to the ProfilerAgent who asked for them.
	 */
	private class SendItemIDs extends OneShotBehaviour {
		@Override
		public void action() {
			try {
				//Filter the results and make a list of IDs to send to Profiler.
				ArrayList<Integer> idsToSend = new ArrayList<>();
				for(Integer id : itemIDs) {
					boolean idIsVisited = profile.getVisitedItemsID().contains(id);
					boolean tourIsFull = idsToSend.size() >= TOUR_SIZE;
					boolean idAlreadyInTour = idsToSend.contains(id);
					
					if(!tourIsFull && !idIsVisited && !idAlreadyInTour)
						idsToSend.add(id);
				}
				
				//Send the IDs to the ProfilerAgent
	//			AID profiler = new AID(ProfilerAgent.PROFILER_NAME, AID.ISLOCALNAME);
				ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
				reply.addReceiver(profilerAgent);
				reply.setOntology("tour-ids");
				reply.setContentObject(idsToSend);
				send(reply);
				//System.out.println("Request was handled and a response have been sent to the Profiler. Number of IDs sent = " + idsToSend.size());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
