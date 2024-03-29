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
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.wrapper.ControllerException;

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
	
	private Artifact boughtArtifact;	// 	The artifact that was bought in an auction.
	private int boughtArtifactPrice;	//	Price of said artifact.
	private String homeContainer;		//	The "Default" container where the Curator is initially started.
	private AID originalCurator;		//	We need a reference to the original Curator, to report results.
	
	@Override
	protected void setup() {
		try {Thread.sleep((int)(Math.random()*500));} catch (InterruptedException e) {} //spread console output
		
		//	Get some references to the original container/Curator  (Used by the clones)
		originalCurator = getAID();
		try {
			homeContainer = getContainerController().getContainerName();
		} catch (ControllerException e) {
			e.printStackTrace();
		}
		
		//	Init utilities
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
			
		//	Create a clone and move it to Container 1
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("curator")) {
					cloneTo(DA_CONTAINER_1, "curator-clone-1");			
				}
			}
		});
		
		//	Create another clone and move it to Container 2
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("curator")) {
					cloneTo(DA_CONTAINER_2, "curator-clone-2");					
				}
			}
		});
		
		//	Wait for results from the clones and compare them
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("curator")) {
					System.out.println("Waiting for clones to finnish...");
					boolean done = false;
					int receivedMessages = 0;
					while(!done) {
						MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
						ACLMessage doneMessage = blockingReceive(template);
						if(doneMessage != null) {
							String content = doneMessage.getContent();
							String item = content.split("::")[0];
							int price = Integer.parseInt(content.split("::")[1]);
							String sender = doneMessage.getSender().getName();
							
							if(item.equals("null"))
								System.out.println(getName() + ": Clone " + sender + " didn't buy anything....");
							else
								System.out.println(getName() + ": Clone " + sender + " bought " + item + " at price " + price);
							receivedMessages++;
						}
						done = receivedMessages == 2;
					}
				}
			}
		});
	}
	
	/**
	 *	Create a clone with the given cloneName and move it to container.
	 */
	private void cloneTo(String container, String cloneName) {
		Runtime rt = Runtime.instance();
		ProfileImpl profile = new ProfileImpl(false);
		profile.setParameter(ProfileImpl.CONTAINER_NAME, container);
		rt.createAgentContainer(profile);
		
		ContainerID destination = new ContainerID();
		destination.setName(container);
		doClone(destination, cloneName);
//		System.out.println(getName() + "I'm still the original, right?");
	}
	
	@Override
	protected void beforeClone() {
		super.beforeClone();
		System.out.println(getName() + ": I'm being cloned.");
	}
	
	/**
	 * 	The agent (clone) is now in a new container.
	 */
	@Override
	protected void afterClone() {
		super.afterClone();

		//	The behaviours of the clone is to participate in ONE auction and then move back to the original container.
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
				ContainerID destination = new ContainerID();
				destination.setName(homeContainer);
				doMove(destination);
			}
		});
		addBehaviour(seq);
	}
	
	/**
	 *	The clone is now in the original container.
	 */
	@Override
	protected void afterMove() {
		super.afterMove();
		//	Inform the original Curator about our results from the auction.
		ACLMessage doneMessage = new ACLMessage(ACLMessage.INFORM);
		doneMessage.setSender(getAID());
		doneMessage.setContent(boughtArtifact + "::" + boughtArtifactPrice);
		doneMessage.addReceiver(originalCurator);
		send(doneMessage);
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
			//int i=0;
			while(receive() != null) //Clear the message queue. TODO: Remove if this causes problems...
				;//i++;
//			System.out.println(getName() + ": Cleared " + i + " messages from queue.");
		}
	}
}