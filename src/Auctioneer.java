import java.util.ArrayList;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;
import jade.proto.SimpleAchieveREInitiator;

/**
 * StartAuction -> CallForProposals -> ReceiveProposals -> HandleProposals -> NoBids/CallForProposals....
 */
public class Auctioneer extends Agent{
	
	private ArrayList<AID> buyers = new ArrayList<>();
	private Vector<ACLMessage> receivedProposals;
	
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
		
		item = "test_item_1";
		price = 100;
		priceLimit = 50;
		priceReduction = 10;
		buyers = new ArrayList<>();
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
	
	private class ReceiveProposals extends Behaviour {
		
		private boolean allProposalsHandled = false;
		
		public ReceiveProposals(Agent a) {
			super(a);
			receivedProposals = new Vector<>();
			System.out.println(getName() + ": Waiting for proposals...");
		}
		
		@Override
		public void action() {
			ACLMessage message = receive();
			if(message != null) {
				switch(message.getPerformative()) {
					case ACLMessage.PROPOSE:
						receivedProposals.add(message);
						allProposalsHandled = receivedProposals.size() == buyers.size();
						System.out.println(getName() + ": Received proposal from " + message.getSender().getName() + ": " + message.getContent());
						break;
					case ACLMessage.NOT_UNDERSTOOD:
						System.out.println(getName() + ": Received \"NOT_UNDERSTOOD\"...");
						myAgent.doDelete();
						break;
					default:
						break;
				}
			}
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
			
			if(interestedBuyers.size() > 0) {				
				AID winner = interestedBuyers.get(0);
				ACLMessage winMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				winMessage.setSender(myAgent.getAID());
				winMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				winMessage.addReceiver(winner);
				send(winMessage);
				interestedBuyers.remove(0);
				
				ACLMessage loseMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
				loseMessage.setSender(myAgent.getAID());
				loseMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				
				for(AID loser : interestedBuyers)
					loseMessage.addReceiver(loser);
				
				if(interestedBuyers.size() > 0)
					send(loseMessage);
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
