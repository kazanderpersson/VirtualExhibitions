import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
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
import jade.domain.mobility.CloneAction;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREResponder;
import jade.proto.SimpleAchieveREResponder;
import jade.wrapper.ContainerController;

/**
 * The Curator will take requests from a Profiler or TourAgent. 
 * It can either be a "search for related items" request, 
 * or a "give me information about these items" request.
 * 
 * Behaviours:
 * ArtifactLookup - Waits for a search request, and responds with some item IDs or item information.
 * UpdateArtifacts - Periodically update the list of artifacts.
 */
public class CloningCurator extends Agent {
	
	public static final String CURATOR_NAME = "curator";
	private ArrayList<Artifact> artifacts = new ArrayList<>();
	private Artifact receivedItem;
	private String convID = "";
	
	private AID marketAgent;
	
	private final int PROPOSAL_ACCEPTED = 1;
	private final int PROPOSAL_REJECTED = -1;
	private final int CFP_RECEIVED = 2;
	private final int NO_BIDS = -2;
	
	private ArrayList<String> interests = new ArrayList<String>();
	private ArrayList<Integer> priorityInterests = new ArrayList<Integer>();
	private int money = 5000;
	
	public static final String DA_CONTAINER_1 = "da-container-1";
	public static final String DA_CONTAINER_2 = "da-container-2";
	
	private Artifact boughtArtifact;
	private int boughtArtifactPrice;

	@Override
	protected void setup() {
		try {Thread.sleep((int)(Math.random()*500));} catch (InterruptedException e) {} //spread console output
		//curator interest setup
		int numberOfInterests = 2+((int)(Math.random()*8)); //2-9
		int i = 0;
		int randomGenre;
		while (i < numberOfInterests) {
			randomGenre = (int)(Math.random()*Auctioneer.genres.length);
			if (!interests.contains(Auctioneer.genres[randomGenre])) { //already taken?
				interests.add(Auctioneer.genres[randomGenre]);
				priorityInterests.add(1+((int)(Math.random()*5)));
				i++;
			}
		}
		System.out.print(getName() + ": My interests & priority: ");
		for (int j=0; j<interests.size(); j++)
			System.out.print(interests.get(j) + "(" + priorityInterests.get(j) + ")" + ((j != interests.size()-1) ? ", " : "")); System.out.print("\n");
			
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("curator")) {
					cloneTo(DA_CONTAINER_1, "curator-clone-1");			
				}
			}
		});
		
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("curator")) {
					cloneTo(DA_CONTAINER_2, "curator-clone-2");					
				}
			}
		});
	}
	
	private void cloneTo(String container, String cloneName) {
		Runtime rt = Runtime.instance();
		ProfileImpl profile = new ProfileImpl(false);
		profile.setParameter(ProfileImpl.CONTAINER_NAME, container);
		ContainerController controller = rt.createAgentContainer(profile);
		
		ContainerID destination = new ContainerID();
		destination.setName(container);
		doClone(destination, cloneName);
		System.out.println(getName() + "I'm still the original, right?");
	}
	
	@Override
	protected void beforeClone() {
		super.beforeClone();
		System.out.println(getName() + ": I'm being cloned.");
	}
	
	@Override
	protected void afterClone() {
		super.afterClone();
		//addBehaviour(new AuctionFSM(this)); //participate in auction
		
		SequentialBehaviour seq = new SequentialBehaviour();
		seq.addSubBehaviour(new AuctionFSM(this));
		seq.addSubBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(boughtArtifact == null)
					System.out.println(getName() + ": I didn't buy anything...");
				else
					System.out.println(getName() + ": I bought: " + boughtArtifact + " at price: " + boughtArtifactPrice);
				
				System.out.println(getName() + ": I should head back home.");
			}
		});
		addBehaviour(seq);
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
			//System.out.println(getName() + ": Successfully registered services.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		/****************************************************************/
	}
	
	
	/**
	 *	The FSM behaviour for participating in an auction.
	 *		_________________________________auction has ended__________________________
	 *		|																			|
	 *		|					  ______________________________no-bids______________	|
	 *		|					  |	_____rejected________							|	|
	 *		V					  | V					|							V	|
	 *	WaitForAuction	-start->  HandleCFP	 -->	HandleResponse	-accepted->    AuctionEnded
	 */
	private class AuctionFSM extends FSMBehaviour {
		public AuctionFSM(Agent agent) {
			final String INIT = "init";
			final String HANDLE_CFP = "hcfp";
			final String PROPOSAL_RESPONSE = "presopnse";
			final String AUCTION_ENDED = "ended";
			
			registerFirstState(new WaitForAuction(), INIT);
			registerState(new HandleCFP(), HANDLE_CFP);
			registerState(new HandleResponse(), PROPOSAL_RESPONSE);
			registerLastState(new AuctionEnded(), AUCTION_ENDED);
			
			registerDefaultTransition(INIT, HANDLE_CFP);
			registerTransition(HANDLE_CFP, AUCTION_ENDED, NO_BIDS);
			registerTransition(HANDLE_CFP, PROPOSAL_RESPONSE, CFP_RECEIVED);
			registerTransition(PROPOSAL_RESPONSE, HANDLE_CFP, PROPOSAL_REJECTED);
			registerTransition(PROPOSAL_RESPONSE, AUCTION_ENDED, PROPOSAL_ACCEPTED);
//			registerDefaultTransition(AUCTION_ENDED, INIT);
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

	/**
	 *	Wait for a new auction to start.
	 */
	private class WaitForAuction extends SimpleBehaviour {
		
		private boolean newAuctionStarted = false;
		
		@Override
		public void action() {
//			System.out.println(getName() + ": state=Waiting...");
			newAuctionStarted = false;
			MessageTemplate template = MessageTemplate.MatchContent("inform-start-of-auction");
			ACLMessage message = receive(template);
			if(message != null) {
				marketAgent = message.getSender();
				newAuctionStarted = true;
				convID = message.getConversationId();
				//System.out.println(getName() + ": Received Auction start message!");
			} else
				block();
		}

		@Override
		public boolean done() {
			return newAuctionStarted;
		}
	}

	/**
	 *	Wait for a CFP-message and reply with a proposal.
	 */
	private class HandleCFP extends SimpleBehaviour {
		private boolean informed = false;
		private int status = 0;
		
		@Override
		public void action() {
//			System.out.println(getName() + ": state=Handle_CFP");
			informed = false;
			MessageTemplate template_cfp = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			MessageTemplate template_nobids = MessageTemplate.MatchContent("no-bids");
			ACLMessage cfp = receive(template_cfp);
			ACLMessage nobids = receive(template_nobids);
			if(cfp != null && cfp.getConversationId().equals(convID)) { //CFP received
				try { 
					receivedItem = (Artifact)cfp.getContentObject();
					//System.out.println(getName() + ": Received CFP - " + receivedItem.getName());	
					ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
					proposal.addReceiver(marketAgent);
					proposal.setConversationId(convID);
					
					if(acceptOffer(receivedItem)) 
						proposal.setContent("yes");
					else
						proposal.setContent("no");
					send(proposal);
					//System.out.println(getName() + ": Proposal sent to Auctioneer. Proposal= " + proposal.getContent());
				}
				catch(UnreadableException e) {
					ACLMessage notUnderstood = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
					notUnderstood.addReceiver(marketAgent);
					notUnderstood.setConversationId(convID);
					send(notUnderstood);
				}
				
				informed = true;
				status = CFP_RECEIVED;
			} else if(nobids != null && nobids.getConversationId().equals(convID)) {
				//System.out.println(getName() + ": No-bids received...");
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
		
		private boolean acceptOffer(Artifact item) { //task 2, bidding strategy
			ArrayList<Integer> genresInCommon = new ArrayList<Integer>();
			for (int i=0; i < interests.size(); i++)
				if (item.getGenre().contains(interests.get(i))) //interested in a genre item has
					genresInCommon.add(priorityInterests.get(i));
				
			if (genresInCommon.size() > 0) {
				int prioritySum = 0;
				for (int sum : genresInCommon)
					prioritySum += sum;
				prioritySum /= genresInCommon.size(); //average priority points taken all interests/genre match into consideration
				
					if ((prioritySum*1000 >= item.getPrice()) && (item.getPrice() <= ((int)(money*0.7)))) //willing to pay the current price? 1000 for every priority point and dont spend more than 70% money
						return true;
					else
						return false;
			}
			else
				return false;
		}
	}

	/**
	 *	Wait for the Auctioneer to handle the proposal, and receive its' response (accept/reject).
	 */
	private class HandleResponse extends SimpleBehaviour {
		private int accepted = 0;
		private boolean receivedResponse = false;
		@Override
		public void action() {
//			System.out.println(getName() + ": state=HandleResponse");
			receivedResponse = false;
			MessageTemplate template_accepted = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			MessageTemplate template_rejected = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
			ACLMessage acceptMessage = receive(template_accepted);
			ACLMessage rejectMessage = receive(template_rejected);
			
			if(acceptMessage != null && acceptMessage.getConversationId().equals(convID)) {
				//System.out.println(getName() + ": My proposal was accepted! Content: " + acceptMessage.getContent());
				accepted = PROPOSAL_ACCEPTED;
				receivedResponse = true;
				artifacts.add(receivedItem);
				money -= receivedItem.getPrice(); //subtract item's cost from bank balance
				System.out.print(getName() + ": ARTIFACT STOCK: ");
				for (int i=0; i < artifacts.size(); i++)
					System.out.print("\"" + artifacts.get(i).getName() + "\"" + ((i != artifacts.size()-1) ? ", " : "")); System.out.print("\n\n");
					
				boughtArtifact = receivedItem;
				boughtArtifactPrice = receivedItem.getPrice();
		
			} else if(rejectMessage != null && rejectMessage.getConversationId().equals(convID)) {
				//System.out.println(getName() + ": My proposal was rejected... Content: " + rejectMessage.getContent());
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

	/**
	 *	The final state of the auction, clear the message queue (may not be necessary, and may cause errors!)
	 */
	private class AuctionEnded extends OneShotBehaviour {
		@Override
		public void action() {
			int i=0;
			while(receive() != null) //Clear the message queue. TODO: Remove if this causes problems...
				i++;
//			System.out.println(getName() + ": Cleared " + i + " messages from queue.");
		}
	}
	
	/**
	 *	Refill our stack of money, so that we can buy new stuff!
	 */
	private class Income extends TickerBehaviour { //randomly give curator more money
		public Income(Agent a, long period) {
			super(a, period);
		}
		@Override
		protected void onTick() {
			int income = 1+((int)(Math.random()*4000)); //1-4000
			money += income;
			System.out.println(getName() + ": Received income: " + income + ":- Total: " + money + ":-");
		}
	}
}