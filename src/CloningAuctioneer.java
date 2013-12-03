import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.ControllerException;

/**
 * 	The Auctioneer/MarketAgent will start a Dutch Auction with all available Curators.
 * 		---------------------------------------------------------------------------------
 * 		|					--------------------------------------------------------	|
 * 		V					V														|	|
 * StartAuction -> CallForProposals -> ReceiveProposals -> HandleProposals -> NoBids/CallForProposals....
 */
public class CloningAuctioneer extends Agent{
	
	private ArrayList<AID> buyers = new ArrayList<>();
	private Vector<ACLMessage> receivedProposals;
	private ArrayList<Artifact> artifacts;
	private Artifact item;
	private String convID = "";
	
	private final int startPrice = 6000;
	private final int priceLimit = 1000;
	private final int priceReduction = 1000;
	
	private final String STATE_START_AUCTION = "start";
	private final String STATE_CFP = "cfp";
	private final String STATE_RECEIVE_PROPOSALS = "propose";
	private final String STATE_HANDLE_PROPOSALS = "handle";
	private final String STATE_NO_BIDS = "nobids";
	
	private final int CONTINUE_AUCTION = 1;
	private final int TERMINATE_AUCTION = -1;
	
	public static final String[] genres = {"geology", "animals", "religon", "history", "sport", "graffiti", "pop art", "surrealism", "cubism", "realism", "romanticism", "19th century", "20th century", "21th century"};
	
	private Artifact artifactSold;		//	The artifact that is sold in an auction.
	private int artifactSellPrice;		//	Price of the artifact.
	private String homeContainer;		//	Default container (where the original auctioneer will stay).
	private AID originalAuctioneer;		//	The original auctioneer
	
	@Override
	protected void setup() {
		initArtifacts();
		try {Thread.sleep(1500);} catch (InterruptedException e) {} //let curators do their setup
		
		originalAuctioneer = getAID();
		try {
			homeContainer = getContainerController().getContainerName();
		} catch (ControllerException e) {
			e.printStackTrace();
		}
		
		//	Create a clone and move it to Container 1.
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("auctioneer")) {
					cloneTo(CloningCurator.DA_CONTAINER_1, "auctioneer-clone-1");			
				}
			}
		});
		
		//	Create another clone and move it to Container 2.
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("auctioneer")) {
					cloneTo(CloningCurator.DA_CONTAINER_2, "auctioneer-clone-2");					
				}
			}
		});
		
		//	Wait for the clones to finish, compare results.
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(myAgent.getName().split("@")[0].equals("auctioneer")) {
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
								System.out.println(getName() + ": Clone " + sender + " didn't manage to sell.");
							else
								System.out.println(getName() + ": Clone " + sender + " sold " + item + " at price " + price);
							receivedMessages++;
						}
						done = receivedMessages == 2;
					}
				}
			}
		});
	}
	
	/**
	 * 	Clone the Auctioneer to a new container. Give the clone a new name.
	 */
	private void cloneTo(String container, String cloneName) {
		ContainerID destination = new ContainerID();
		destination.setName(container);
		doClone(destination, cloneName);
	}
	
	@Override
	protected void beforeClone() {
		super.beforeClone();
		System.out.println(getName() + ": Cloning myself.");
	}
	
	/**
	 * 	Clone is now in a new container. Start an auction!
	 */
	@Override
	protected void afterClone() {
		super.afterClone();
		
		buyers = getBuyers();		//No need to use the DF service, we know the name of the Curator clone.
		receivedProposals = new Vector<>();
		
		//Initiate Auction FSM
		FSMBehaviour dutchAuction = new FSMBehaviour(this);
		dutchAuction.registerFirstState(new StartAuction(), STATE_START_AUCTION);
		dutchAuction.registerState(new CallForProposals(this), STATE_CFP);
		dutchAuction.registerState(new ReceiveProposals(this), STATE_RECEIVE_PROPOSALS);
		dutchAuction.registerState(new HandleProposals(), STATE_HANDLE_PROPOSALS);
		dutchAuction.registerLastState(new TerminateAuction(), STATE_NO_BIDS);
		
		dutchAuction.registerDefaultTransition(STATE_START_AUCTION, STATE_CFP);
		dutchAuction.registerDefaultTransition(STATE_CFP, STATE_RECEIVE_PROPOSALS);
		dutchAuction.registerDefaultTransition(STATE_RECEIVE_PROPOSALS, STATE_HANDLE_PROPOSALS);
		dutchAuction.registerTransition(STATE_HANDLE_PROPOSALS, STATE_CFP, CONTINUE_AUCTION);
		dutchAuction.registerTransition(STATE_HANDLE_PROPOSALS, STATE_NO_BIDS, TERMINATE_AUCTION);
		
		
		
		// Perform auction, handle results, go home.
		SequentialBehaviour seq = new SequentialBehaviour();
		seq.addSubBehaviour(dutchAuction);
		seq.addSubBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				if(artifactSold == null)
					System.out.println(getName() + ": Auction is over, i didn't sell :(");
				else {
					System.out.println(getName() + ": Auction is over, I sold: " + artifactSold + " at price:" + artifactSellPrice);
				}
				
				System.out.println("I should move back to the main container..");
				
				ContainerID destination = new ContainerID();
				destination.setName(homeContainer);
				doMove(destination);
			}
		});
		addBehaviour(seq);
	}
	
	@Override
	protected void beforeMove() {
		super.beforeMove();
//		System.out.println(getName() + ": I'm returning home.");
	}
	
	/**
	 * 	Now back in the original container, notify the original Auctioneer about results.
	 */
	@Override
	protected void afterMove() {
		super.afterMove();
//		System.out.println(getName() + ": Honey, I'm home!");
		
		ACLMessage doneMessage = new ACLMessage(ACLMessage.INFORM);
		doneMessage.setSender(getAID());
		doneMessage.setContent(artifactSold + "::" + artifactSellPrice);
		doneMessage.addReceiver(originalAuctioneer);
		send(doneMessage);
	}
	
	/**
	 * @return a list of potential buyers (Curator clones). DF Service is not used since it's platform-wide.
	 */
	private ArrayList<AID> getBuyers() {
		/**********/
		AID buyer = new AID();
		String myName = getName().split("@")[0];
		String buyerName = (myName.equals("auctioneer-clone-1") ? "curator-clone-1" : "curator-clone-2");
		buyer.setLocalName(buyerName);
		ArrayList<AID> b = new ArrayList<>();
		b.add(buyer);
		System.out.println(getName() + ": Added buyer: " + buyer);
		return b;
		/**********/
	}
	
	private void initArtifacts() {
		try {  //randomly assign an artifact database to agent
			Scanner sc;
			String ARTIFACTS_SOURCE = "Artifacts_database1.txt";
			sc = new Scanner(new File(ARTIFACTS_SOURCE));

			sc.nextLine(); //jump first two description rows in text file
			sc.nextLine();

			artifacts = new ArrayList<Artifact>();
			String[] input = new String[6];
			int id = 1;
			do { //read in all artifact entries in file
				sc.nextLine();
				for (int i=0; i<input.length; i++) 
					input[i] = sc.nextLine();
				artifacts.add(new Artifact(id, input[0], input[1], input[2], input[3], input[4], new ArrayList<String>(Arrays.asList(input[5].split(", ")))));
				id++;
			} while (sc.hasNextLine());
			sc.close();
		} catch (IOException e) {}
	}
	
	/**
	 *	The first state of the Auction FSM, send an inform-start-of-auction message.
	 */
	private class StartAuction extends OneShotBehaviour {
		@Override
		public void action() {
//			int randomArtifact = (int)(Math.random()*artifacts.size());
			int randomArtifact = (int)(0.4*artifacts.size());				//TODO: Alla clones väljer samma artifact.
			artifacts.get(randomArtifact).setPrice(startPrice);
			item = artifacts.get(randomArtifact); //randomly choose and auction out an available artifact
			
			ACLMessage initMessage = new ACLMessage(ACLMessage.INFORM);
			initMessage.setSender(myAgent.getAID());
			initMessage.addReplyTo(myAgent.getAID());
			initMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			initMessage.setContent("inform-start-of-auction");
			convID = ""+(new Random()).nextInt(); //avoid mixing up simultaneously ongoing messaging
			initMessage.setConversationId(convID);
			buyers = getBuyers();
			for(AID aid : buyers)
				initMessage.addReceiver(aid);
			System.out.println("\n" + getName() + ": Auction started. ITEM: \"" + item.getName() + "\"");
			send(initMessage);
		}
	}
	
	/**
	 *	Send the CFP-message.
	 */
	private class CallForProposals extends OneShotBehaviour {
		
		public CallForProposals(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			ACLMessage message = new ACLMessage(ACLMessage.CFP);
			message.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			message.setSender(myAgent.getAID());
			message.addReplyTo(myAgent.getAID());
			message.setConversationId(convID);
			try {message.setContentObject(item);} catch (IOException e) {e.printStackTrace();} //send artifact
			for(AID aid : buyers)
				message.addReceiver(aid);
			
			System.out.println(getName() + ": CFP sent to " + buyers.size() + " agents. PRICE: " + item.getPrice());
			if(buyers.size() > 0)
				send(message);
		}
	}
	
	/**
	 *	Wait for proposals from all potential buyers. If a not-understood is received, something is wrong and we should terminate.
	 */
	private class ReceiveProposals extends SimpleBehaviour {
		private boolean allProposalsHandled = false;
		
		public ReceiveProposals(Agent a) {
			super(a);
			receivedProposals = new Vector<>();
			//System.out.println(getName() + ": Waiting for proposals...");
		}
		
		@Override
		public void action() {
			allProposalsHandled = false; //TODO Refactor...
			ACLMessage message = receive();
			if(message != null && message.getConversationId().equals(convID)) {
				switch(message.getPerformative()) {
					case ACLMessage.PROPOSE:
						receivedProposals.add(message);
						allProposalsHandled = receivedProposals.size() == buyers.size();
						System.out.println(getName() + ": Received proposal from " + message.getSender().getName() + ": " + message.getContent());
						//System.out.println("Received Proposals: " + receivedProposals.size());
						//System.out.println("Buyers: " + buyers.size());
						break;
					case ACLMessage.NOT_UNDERSTOOD:
						System.out.println(getName() + ": Received \"NOT_UNDERSTOOD\"...");
						myAgent.doDelete();
						break;
					default:
						break;
				}
			} else
				block();
		}

		@Override
		public boolean done() {
			return allProposalsHandled;
		}
	}
	
	/**
	 *	Handle the proposals, elect a winner and go to the end state, or the CFP-state if nobody wanted the item at the current price.
	 */
	private class HandleProposals extends OneShotBehaviour {
		private int status = 0;
		
		@Override
		public void action() {
			//System.out.println(getName() + ": Handling proposals...");
			
			Vector<AID> interestedBuyers = new Vector<>();
			for(ACLMessage msg : receivedProposals)
				if(msg.getContent().equals("yes"))				//TODO: Probably should make that string more accessible...
					interestedBuyers.add(msg.getSender());
			
			AID winner = new AID();
			if(interestedBuyers.size() > 0) {				
				winner = interestedBuyers.get(0); //if multiple buyers bid at same price, take the first one who responded
				ACLMessage winMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				winMessage.setSender(myAgent.getAID());
				winMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				winMessage.addReceiver(winner);
				winMessage.setContent("you won!");
				winMessage.setConversationId(convID);
				send(winMessage);
				artifacts.remove(item);
				
				status = TERMINATE_AUCTION;
				System.out.println(getName() + ": SOLD TO " + winner.getName() + "! Auction terminated");
				artifactSold = item;
				artifactSellPrice = item.getPrice();
			} 
			else {
				item.setPrice(item.getPrice() - priceReduction);
				if(item.getPrice() < priceLimit) {
					status = TERMINATE_AUCTION;
					System.out.println(getName() + ": NO BUY! Price limit reached, auction terminated\n");
				}
				else {
					System.out.println(getName() + ": NO BUY! Price was lowered and auction continue.");
					status = CONTINUE_AUCTION;
				}
			}
			
			ACLMessage rejectMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectMessage.setSender(myAgent.getAID());
			rejectMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			rejectMessage.setContent("I rejected your proposal...");
			rejectMessage.setConversationId(convID);
			for(AID buyer : buyers)
				if(buyer != winner) //send reject to all that isn't winner, even though there isn't a winner at all in this round
					rejectMessage.addReceiver(buyer);
			send(rejectMessage);
			receivedProposals = new Vector<>(); //TODO: Do this somewhere else....
		}
		
		@Override
		public int onEnd() {
			return status;
		}
	}
	
	/**
	 *	The auction has ended, send no-bids.
	 */
	private class TerminateAuction extends OneShotBehaviour {
		@Override
		public void action() {
			ACLMessage noBidsMessage = new ACLMessage(ACLMessage.INFORM);
			noBidsMessage.setSender(myAgent.getAID());
			noBidsMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			noBidsMessage.setContent("no-bids");
			noBidsMessage.setConversationId(convID);
			for(AID aid : buyers)
				noBidsMessage.addReceiver(aid);
			send(noBidsMessage);
			//System.out.println(getName() + ": No-bids sent, auction is terminating.");
		}
	}
}
