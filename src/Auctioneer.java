import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
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
	
	private String item = "test_item_1";
	private int price = 100;
	private int priceLimit = 50;
	private int priceReduction = 10;
	
	private final String STATE_START_AUCTION = "start";
	private final String STATE_CFP = "cfp";
	private final String STATE_RECEIVE_PROPOSALS = "propose";
	private final String STATE_HANDLE_PROPOSALS = "handle";
	private final String STATE_NO_BIDS = "nobids";
	
	private final int CONTINUE_AUCTION = 1;
	private final int TERMINATE_AUCTION = -1;
	
	
	@Override
	protected void setup() {
		initArtifacts();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		item = "test_item_1";
		price = 100;
		priceLimit = 50;
		priceReduction = 10;
//		buyers = new ArrayList<>();
		buyers = getBuyers();
		receivedProposals = new Vector<>();
		
		FSMBehaviour fsm = new FSMBehaviour(this);
		fsm.registerFirstState(new StartAuction(), STATE_START_AUCTION);
		fsm.registerState(new CallForProposals(this), STATE_CFP);
		fsm.registerState(new ReceiveProposals(this), STATE_RECEIVE_PROPOSALS);
		fsm.registerState(new HandleProposals(), STATE_HANDLE_PROPOSALS);
		fsm.registerLastState(new TerminateAuction(), STATE_NO_BIDS);
		
		fsm.registerDefaultTransition(STATE_START_AUCTION, STATE_CFP);
		fsm.registerDefaultTransition(STATE_CFP, STATE_RECEIVE_PROPOSALS);
		fsm.registerDefaultTransition(STATE_RECEIVE_PROPOSALS, STATE_HANDLE_PROPOSALS);
		fsm.registerTransition(STATE_HANDLE_PROPOSALS, STATE_CFP, CONTINUE_AUCTION);
		fsm.registerTransition(STATE_HANDLE_PROPOSALS, STATE_NO_BIDS, TERMINATE_AUCTION);
		
		addBehaviour(fsm);
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
				System.out.println("Added a buyer");
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
			ACLMessage initMessage = new ACLMessage(ACLMessage.INFORM);
			initMessage.setSender(myAgent.getAID());
			initMessage.addReplyTo(myAgent.getAID());
			initMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			initMessage.setContent("inform-start-of-auction");
			for(AID aid : buyers)
				initMessage.addReceiver(aid);
			send(initMessage);
			
			System.out.println(getName() + ": Auction started.");
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
			message.setContent(item + " " + price);		//TODO: Temporary
			for(AID aid : buyers)
				message.addReceiver(aid);
			
			if(buyers.size() > 0)
				send(message);
			
			System.out.println(getName() + ": CFP sent to " + buyers.size() + " agents. Item: " + item + " price: " + price);
		}
	}
	
	private class ReceiveProposals extends SimpleBehaviour {
		
		private boolean allProposalsHandled = false;
		
		public ReceiveProposals(Agent a) {
			super(a);
			receivedProposals = new Vector<>();
			System.out.println(getName() + ": Waiting for proposals...");
		}
		
		@Override
		public void action() {
			allProposalsHandled = false;		//TODO Refactor...
			ACLMessage message = receive();
			if(message != null) {
				switch(message.getPerformative()) {
					case ACLMessage.PROPOSE:
						receivedProposals.add(message);
						allProposalsHandled = receivedProposals.size() == buyers.size();
						System.out.println(getName() + ": Received proposal from " + message.getSender().getName() + ": " + message.getContent());
						System.out.println("Received Proposals: " + receivedProposals.size());
						System.out.println("Buyers: " + buyers.size());
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
			System.out.println(getName() + ": Handling proposals...");
			
			Vector<AID> interestedBuyers = new Vector<>();
			for(ACLMessage msg : receivedProposals)
				if(msg.getContent().equals("yes"))				//TODO: Probably should make that string more accessible...
					interestedBuyers.add(msg.getSender());
			
			AID winner = new AID();
			if(interestedBuyers.size() > 0) {				
				winner = interestedBuyers.get(0);
				ACLMessage winMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				winMessage.setSender(myAgent.getAID());
				winMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				winMessage.addReceiver(winner);
				winMessage.setContent("You won!");
				send(winMessage);
//				interestedBuyers.remove(0);
				
//				ACLMessage rejectMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
//				rejectMessage.setSender(myAgent.getAID());
//				rejectMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
//				
//				for(AID loser : interestedBuyers)
//					rejectMessage.addReceiver(loser);
//				
//				if(interestedBuyers.size() > 0)
//					send(rejectMessage);
				status = TERMINATE_AUCTION;
				System.out.println(getName() + ": Somebody bought the item! Time to terminate..");
			} else {
				price -= priceReduction;
				boolean priceLimitReached = price <= priceLimit;
				if(priceLimitReached) {
					status = TERMINATE_AUCTION;
					System.out.println(getName() + ": Price limit reached... Time to terminate..");
				}
				else {
					System.out.println(getName() + ": Price was lowered annd auction will continue.");
					status = CONTINUE_AUCTION;
				}
			}
			
			ACLMessage rejectMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectMessage.setSender(myAgent.getAID());
			rejectMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
			rejectMessage.setContent("I rejected your proposal...");
			for(AID buyer : buyers)
				if(buyer != winner)
					rejectMessage.addReceiver(buyer);
			send(rejectMessage);
			receivedProposals = new Vector<>();				//TODO: Do this somewhere else....
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
			for(AID aid : buyers)
				noBidsMessage.addReceiver(aid);
			send(noBidsMessage);
			System.out.println(getName() + ": No-bids sent, auction is terminating.");
		}
	}
}
