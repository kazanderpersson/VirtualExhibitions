import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

/**
 * StartAuction -> CallForProposals -> ReceiveProposals -> HandleProposals -> NoBids/CallForProposals....
 */
public class Auctioneer extends Agent{
	
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
	
	@Override
	protected void setup() {
		initArtifacts();
		try {Thread.sleep(1500);} catch (InterruptedException e) {} //let curators do their setup
		
		System.out.println("\n" + getName() + ": ARTIFACT STOCK: ");
		for (Artifact a : artifacts) {
			System.out.print("- \"" + a.getName() + "\" (GENRES: ");
			for (int i=0; i < a.getGenre().size(); i++)
				System.out.print(a.getGenre().get(i) + ((i != a.getGenre().size()-1) ? ", " : ""));
			System.out.println(")");
		}

		buyers = getBuyers();
		receivedProposals = new Vector<>();
		
		addBehaviour(new SpawnAuction(this, 1000)); //auction every second
	}
	
	private class SpawnAuction extends TickerBehaviour {
		public SpawnAuction(Agent a, long period) {
			super(a, period);
		}
		@Override
		protected void onTick() {
			if(artifacts.size() > 0) {
				FSMBehaviour auctionLoop = new FSMBehaviour(myAgent);
				auctionLoop.registerFirstState(new StartAuction(), STATE_START_AUCTION);
				auctionLoop.registerState(new CallForProposals(myAgent), STATE_CFP);
				auctionLoop.registerState(new ReceiveProposals(myAgent), STATE_RECEIVE_PROPOSALS);
				auctionLoop.registerState(new HandleProposals(), STATE_HANDLE_PROPOSALS);
				auctionLoop.registerLastState(new TerminateAuction(), STATE_NO_BIDS);
				
				auctionLoop.registerDefaultTransition(STATE_START_AUCTION, STATE_CFP);
				auctionLoop.registerDefaultTransition(STATE_CFP, STATE_RECEIVE_PROPOSALS);
				auctionLoop.registerDefaultTransition(STATE_RECEIVE_PROPOSALS, STATE_HANDLE_PROPOSALS);
				auctionLoop.registerTransition(STATE_HANDLE_PROPOSALS, STATE_CFP, CONTINUE_AUCTION);
				auctionLoop.registerTransition(STATE_HANDLE_PROPOSALS, STATE_NO_BIDS, TERMINATE_AUCTION);
				addBehaviour(auctionLoop);
			} else
				System.out.println(getName() + ": I have nothing left to sell...");
		}
	}
	
	private ArrayList<AID> getBuyers() {
		/**********************************************/
		/******  Look for a Curator in the DF  *****/
		/**********************************************/
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setName("buying-artifacts");
		sd.setType("buying-artifacts");
		template.addServices(sd);
		ArrayList<AID> b = new ArrayList<>();
		try {
			DFAgentDescription[] result = DFService.searchUntilFound(this, getDefaultDF(), template, null, 20000);
			for(DFAgentDescription r : result) {
				b.add(r.getName());
				//System.out.println("Added a buyer");
			}
		} catch (FIPAException e1) {
			e1.printStackTrace();
			return null;
		}
		return b;
		/**********************************************/
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
	
	private class StartAuction extends OneShotBehaviour {
		@Override
		public void action() {
			int randomArtifact = (int)(Math.random()*artifacts.size());
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
