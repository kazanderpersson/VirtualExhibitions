import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
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
	
	public static final String TOUR_GUIDE_NAME = "bob";
	private final int TOUR_SIZE = 5;
	
	private String conversationID;
	
	@Override
	protected void setup() {
		addBehaviour(new HandleTourRequestBehaviour());
	}
	
	private class HandleTourRequestBehaviour extends CyclicBehaviour {

		@Override
		public void action() {
			try {
				System.out.println("Waiting for Tour Request from ProfilerAgent.");
				//Receive message from ProfilerAgent
				ACLMessage profilerRequest = blockingReceive(MessageTemplate.MatchOntology("get-tour-guide"));
				AID profiler = profilerRequest.getSender();
				conversationID = profilerRequest.getConversationId();
				profile = (Profile) profilerRequest.getContentObject();
				
				
				//Send request to CuratorAgent
				AID curator = new AID(CuratorAgent.CURATOR_NAME, AID.ISLOCALNAME);
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.addReceiver(curator);
				msg.setOntology("request-ids");
				msg.setConversationId(conversationID);
				msg.setContentObject(profile.getInterests());
				send(msg);
				
				
				//Receive response from CuratorAgent
				ACLMessage curatorResponse = blockingReceive(MessageTemplate.MatchOntology("get-artifact-ids").MatchConversationId(conversationID));
				itemIDs = (ArrayList<Integer>) curatorResponse.getContentObject();
				
				
				//Send the IDs to the ProfilerAgent
				ArrayList<Integer> idsToSend = new ArrayList<>();
				for(Integer id : itemIDs) {
					boolean idIsVisited = profile.getVisitedItemsID().contains(id);
					boolean tourIsFull = idsToSend.size() > TOUR_SIZE;
					boolean idAlreadyInTour = idsToSend.contains(id);
					
					if(!idIsVisited && !tourIsFull && !idAlreadyInTour)
						idsToSend.add(id);
				}
				
				//Send the IDs to the ProfilerAgent
	//			AID profiler = new AID(ProfilerAgent.PROFILER_NAME, AID.ISLOCALNAME);
				ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
				reply.addReceiver(profiler);
				reply.setOntology("tour-ids");
				reply.setContentObject(idsToSend);
				send(reply);
				System.out.println("Request was handled and a response have been sent to the Profiler.");
			} catch (UnreadableException e) {
				e.printStackTrace();
				System.err.println(myAgent.getAID().getName() + ": Could not decode message. Aborting.");
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println(myAgent.getAID().getName() + ": Could not serialize some object... Aborting.");
			}
		}
	}
	
	private class ReceiveTourRequest extends MsgReceiver {
		public ReceiveTourRequest(Agent a,long deadline) {
			super(a, MessageTemplate.MatchOntology("get-tour-guide"), deadline, new DataStore(), "key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			try {
				conversationID = msg.getConversationId();
				profile = (Profile) msg.getContentObject();
			} catch (UnreadableException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not read profile. Terminating...");
				myAgent.doDelete();
			}
		}
	}
	
	
	private class SendArtifactRequest extends OneShotBehaviour {
		@Override
		public void action() {
			AID curator = new AID(CuratorAgent.CURATOR_NAME, AID.ISLOCALNAME);
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(curator);
			msg.setOntology("request-ids");
			msg.setConversationId(conversationID);
			try {
				msg.setContentObject(profile.getInterests());	//TODO change to get better results?
			} catch (IOException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not serialize interest list. Terminating...");
				myAgent.doDelete();
			}
			send(msg);
		}
	}
	
	private class ReceiveArtifactIDs extends MsgReceiver {
		public ReceiveArtifactIDs(Agent a,long deadline) {
			super(a, MessageTemplate.MatchOntology("get-artifact-ids").MatchConversationId(conversationID), deadline, new DataStore(), "key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			try {
				itemIDs = (ArrayList<Integer>) msg.getContentObject();
				
			} catch (UnreadableException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not read item IDs. Terminating...");
				myAgent.doDelete();
			}
		}
	}
	
	private class SendItemIDs extends OneShotBehaviour {
		@Override
		public void action() {
			ArrayList<Integer> idsToSend = new ArrayList<>();
			for(Integer id : itemIDs) {
				boolean idIsVisited = profile.getVisitedItemsID().contains(id);
				boolean tourIsFull = idsToSend.size() > TOUR_SIZE;
				boolean idAlreadyInTour = idsToSend.contains(id);
				if(!idIsVisited && !tourIsFull && !idAlreadyInTour)
					idsToSend.add(id);
			}
			
			AID profiler = new AID(ProfilerAgent.PROFILER_NAME, AID.ISLOCALNAME);
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(profiler);
			msg.setOntology("tour-ids");
			try {
				msg.setContentObject(idsToSend);
			} catch (IOException e) {
				System.err.println(myAgent.getAID().getName() + ": Could not serialize the tour IDs. Terminating...");
				myAgent.doDelete();
			}
			send(msg);
		}
	}
}
