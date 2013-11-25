import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREResponder;
import jade.proto.SimpleAchieveREResponder;
import jade.proto.states.MsgReceiver;

/**
 * The Curator will take requests from a Profiler or TourAgent. 
 * It can either be a "search for related items" request, 
 * or a "give me information about these items" request.
 * 
 * Behaviours:
 * ArtifactLookup - Waits for a search request, and responds with some item IDs or item information.
 * UpdateArtifacts - Periodically update the list of artifacts.
 */
public class CuratorAgent extends Agent {
	
	public static final String CURATOR_NAME = "curator";
	private ArrayList<Artifact> artifacts = new ArrayList<>();
	
	private String item;
	private int price;
	private AID marketAgent;
	
	private final int PROPOSAL_ACCEPTED = 1;
	private final int PROPOSAL_REJECTED = -1;
	private final int CFP_RECEIVED = 2;
	private final int NO_BIDS = -2;
	
	@Override
	protected void setup() {
		publishServices();
		
		MessageTemplate mt = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST);
		addBehaviour(new ArtifactLookup(this, mt));
		
//		addBehaviour(new WaitForAuction());
//		addBehaviour(new HandleCFP());
		addBehaviour(new AuctionFSM(this));
	}
	
	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		System.out.println(getName() + ": I'm going down...");
	}
	
	private void publishServices() {
		/*****************************************************************/
		/**************  Publish the two services to DF  *****************/
		/*****************************************************************/
		ServiceDescription artifactInformation = new ServiceDescription();
		artifactInformation.setType("artifact-lookup");
		artifactInformation.setName("get-artifact-info");
		artifactInformation.addOntologies("get-item-information");
		Property args = new Property("args", "Send a Profile and use the ontology: get-item-information");
		artifactInformation.addProperties(args);
		
		ServiceDescription artifactSearch = new ServiceDescription();
		artifactSearch.setType("artifact-search");
		artifactSearch.setName("search-for-artifacts");
		artifactSearch.addOntologies("request-ids");
		args = new Property("args", "Send an ArrayList<Integer> of IDs and use the ontology: request-ids");
		artifactSearch.addProperties(args);
		
		ServiceDescription buyingArtifacts = new ServiceDescription();
		buyingArtifacts.setName("buying-artifacts");
		buyingArtifacts.setType("buying-artifacts");
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(artifactInformation);
		dfd.addServices(artifactSearch);
		dfd.addServices(buyingArtifacts);
		try {
			DFService.register(this, dfd);
			System.out.println(getName() + ": Successfully registered services.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		/****************************************************************/
	}
	
	private class AuctionFSM extends FSMBehaviour {
		public AuctionFSM(Agent agent) {
			String INIT = "init";
			String HANDLE_CFP = "hcfp";
			String PROPOSAL_RESPONSE = "presopnse";
			String AUCTION_ENDED = "ended";
			
			registerFirstState(new WaitForAuction(), INIT);
			registerState(new HandleCFP(), HANDLE_CFP);
			registerState(new HandleResponse(), PROPOSAL_RESPONSE);
			registerState(new AuctionEnded(), AUCTION_ENDED);
			
			registerDefaultTransition(INIT, HANDLE_CFP);
			registerTransition(HANDLE_CFP, AUCTION_ENDED, NO_BIDS);
			registerTransition(HANDLE_CFP, PROPOSAL_RESPONSE, CFP_RECEIVED);
			registerTransition(PROPOSAL_RESPONSE, HANDLE_CFP, PROPOSAL_REJECTED);
			registerTransition(PROPOSAL_RESPONSE, AUCTION_ENDED, PROPOSAL_ACCEPTED);
			registerDefaultTransition(AUCTION_ENDED, INIT);
		}
	}

	private class ArtifactLookup extends SimpleAchieveREResponder {
		public ArtifactLookup(Agent a, MessageTemplate mt) {
			super(a,mt);
		}
		
		protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
			if (request.getOntology().equals("request-ids"))
				return handleTourGuideRequest(request);
			else if (request.getOntology().equals("get-item-information"))
				return handleProfilerRequest(request);
			else {
				ACLMessage informDone = request.createReply();
				informDone.setPerformative(ACLMessage.NOT_UNDERSTOOD);
				return informDone;
			}
		}
			
		protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException {
			return null;
		}
			
		private ACLMessage handleTourGuideRequest(ACLMessage request) {
			AID tourGuide = request.getSender();
			ArrayList<String> interests;
			try {
				interests = (ArrayList<String>) request.getContentObject();
				//System.out.println(getName() + ": Will handle " + interests.size() + " interests. (Successfully read message)");
			} catch (UnreadableException e) {
				System.out.println(myAgent.getAID().getName() + ":ERROR Couldn't get interests. Will respond with an empty list...");
				interests = new ArrayList<>();
			}
			String conversationID = request.getConversationId();
			ArrayList<Integer> ids = new ArrayList<>();
			
			for(Artifact artifact : artifacts)
				for(String interest : interests)
					if(artifact.getGenre().contains(interest) || artifact.getType().equals(interest))
						ids.add(artifact.getId());
			
			ACLMessage response = new ACLMessage(ACLMessage.INFORM);
			response.addReceiver(tourGuide);
			response.setConversationId(conversationID);
			response.setOntology("get-artifact-ids");
			try {
				response.setContentObject(ids);
			} catch (IOException e) {
				System.err.println(myAgent.getAID().getName() + ": Couldn't serialize the ID-list... Will cause problems with other agents.");
			}
			return response;
			//System.out.println(myAgent.getAID().getName() + ":Response message sent to TourGuide with " + ids.size() + " IDs.");
		}
			
		private ACLMessage handleProfilerRequest(ACLMessage request) {
			AID profiler = request.getSender();
			ArrayList<Integer> requestedIDs;
			try {
				requestedIDs = (ArrayList<Integer>) request.getContentObject();
				//System.out.println(getName() + ": Received request from Profiler. He requested " + requestedIDs.size() + " IDs.");
			} catch (UnreadableException e) {
				System.err.println(myAgent.getAID().getName() + ": Couldn't get IDs to look up. Will respond with an empty list...");
				requestedIDs= new ArrayList<>();
			}
			String conversationID = request.getConversationId();
			ArrayList<Artifact> relatedArtifacts = new ArrayList<>();
			
			for(Integer id : requestedIDs)
				for(Artifact a : artifacts)
					if(a.getId() == id)
						relatedArtifacts.add(a);
			
			ACLMessage response = new ACLMessage(ACLMessage.INFORM);
			response.addReceiver(profiler);
			response.setConversationId(conversationID);
			response.setOntology("tour-info");
			try {
				response.setContentObject(relatedArtifacts);
			} catch (IOException e) {
				System.err.println(myAgent.getAID().getName() + ": Couldn't serialize the Artifact list... Will cause problems with other agents.");
			}
			return response;
			//System.out.println(myAgent.getAID().getName() + ":Response message sent to Profiler with " + relatedArtifacts.size() + " artifacts.");
		}		
	}

	private class WaitForAuction extends SimpleBehaviour {
		
		private boolean newAuctionStarted = false;
		
		@Override
		public void action() {
			newAuctionStarted = false;
			MessageTemplate template = MessageTemplate.MatchContent("inform-start-of-auction");
//			MessageTemplate template = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.INFORM).MatchContent("inform-start-of-auction");
			ACLMessage message = receive(template);
			if(message != null) {
				marketAgent = message.getSender();
				newAuctionStarted = true;
				System.out.println(getName() + ": Received Auction start message!");
			} else
				block();
		}

		@Override
		public boolean done() {
			return newAuctionStarted;
		}
	}

	private class HandleCFP extends SimpleBehaviour {
		private boolean informed = false;
		private int status = 0;
		
		@Override
		public void action() {
			informed = false;
//			MessageTemplate template_cfp = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.CFP).MatchSender(marketAgent);
			MessageTemplate template_cfp = MessageTemplate.MatchPerformative(ACLMessage.CFP);
//			MessageTemplate template_nobids = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.INFORM).MatchSender(marketAgent).MatchContent("no-bids");
			MessageTemplate template_nobids = MessageTemplate.MatchContent("no-bids");
			ACLMessage cfp = receive(template_cfp);
			ACLMessage nobids = receive(template_nobids);
			if(cfp != null) { 		//CFP received
				System.out.println(getName() + ": Received CFP - " + cfp.getContent());
				try {
					item = cfp.getContent().split(" ")[0];
					price = Integer.parseInt(cfp.getContent().split(" ")[1]);
					
					ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
					proposal.addReceiver(marketAgent);
					boolean accept = acceptOffer(item, price);
					if(accept)
						proposal.setContent("yes");
					else
						proposal.setContent("no");
					send(proposal);
					System.out.println(getName() + ": Proposal sent to Auctioneer. Proposal= " + proposal.getContent());
				} catch(Exception e) {
					ACLMessage notUnderstood = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
					notUnderstood.addReceiver(marketAgent);
					send(notUnderstood);
				}
				informed = true;
				status = CFP_RECEIVED;
			} else if(nobids != null) {
				System.out.println(getName() + ": No-bids received...");
				informed = true;
				status = NO_BIDS;
			} else
				block();
		}

		@Override
		public boolean done() {
			return informed;
		}
		
		@Override
		public int onEnd() {
			return status;
		}
		
		private boolean acceptOffer(String item, int price) { //task 2, bidding strategy
			Random rand = new Random();
			int a = rand.nextInt(100);
			return a > 90;
		}
	}

	private class HandleResponse extends SimpleBehaviour {
		private int accepted = 0;
		private boolean receivedResponse = false;
		@Override
		public void action() {
			receivedResponse = false;
			MessageTemplate template_accepted = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			MessageTemplate template_rejected = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
//			MessageTemplate template_accepted = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.ACCEPT_PROPOSAL).MatchSender(marketAgent);
//			MessageTemplate template_rejected = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.REJECT_PROPOSAL).MatchSender(marketAgent);
			ACLMessage acceptMessage = receive(template_accepted);
			ACLMessage rejectMessage = receive(template_rejected);
			
			if(acceptMessage != null) {
				System.out.println(getName() + ": My proposal was accepted! Content: " + acceptMessage.getContent());
				accepted = PROPOSAL_ACCEPTED;
				receivedResponse = true;
			} else if(rejectMessage != null) {
				System.out.println(getName() + ": My proposal was rejected... Content: " + rejectMessage.getContent());
				accepted = PROPOSAL_REJECTED;
				receivedResponse = true;
			}
			else
				block();
		}

		@Override
		public boolean done() {
			return receivedResponse;
		}
		
		@Override
		public int onEnd() {
			return accepted;
		}
	}

	
	private class AuctionEnded extends OneShotBehaviour {
		@Override
		public void action() {
			
		}
	}
}