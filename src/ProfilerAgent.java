import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;


/**
 * Behaviours: 
 *		StartTourBehaviour extends Ticker
 *			FetchTourInformationBehaviour - SequentialBehaviour?
 *					AskForInterestingItems - OneShot
 *					ReceiveInterestingItems - MsgReceiver
 *					AskForItemInformation - OneShot
 *					ReceiveItemInformation - MsgReceiver
 *					ShowTourToUser.... - OneShot
 */
public class ProfilerAgent extends Agent {
	
	Profile profile;
	
	private ArrayList<Integer> itemIDs;
	private ArrayList<Artifact> tourArtifacts;
	
//	private final String TOUR_GUIDE_NAME = "bob";
//	private final String CURATOR_NAME = "alice";
	public static final String PROFILER_NAME = "profiler";
	private final int TOUR_FREQUENCY = 10000;
	
	@Override
	protected void setup() {
		addBehaviour(new StartTourBehaviour(this, TOUR_FREQUENCY));
	}
	
	private class StartTourBehaviour extends TickerBehaviour {

		public StartTourBehaviour(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			SequentialBehaviour seq = new SequentialBehaviour(myAgent);
			seq.addSubBehaviour(new AskForInterestingItems());
			seq.addSubBehaviour(new ReceiveInterestingItemsBehaviour(myAgent, 10000));
			seq.addSubBehaviour(new AskForItemInformation());
			seq.addSubBehaviour(new ReceiveTourContentBehaviour(myAgent, 10000));
			addBehaviour(seq);			
		}
	}
	
	private class AskForInterestingItems extends OneShotBehaviour {
		@Override
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			AID receiver = new AID(TourGuideAgent.TOUR_GUIDE_NAME, AID.ISLOCALNAME);	//TODO remove name later..
			msg.addReceiver(receiver);
			try {
				msg.setContentObject(profile);
			} catch (IOException e) {
				msg.setContent(profile.getName());		//......
			}
			msg.setOntology("get-tour-guide");
			send(msg);
			System.out.println(myAgent.getAID().getName() + ": Profile sent to tour agent.");
		}
	}
	
	private class ReceiveInterestingItemsBehaviour extends MsgReceiver {
		
		public ReceiveInterestingItemsBehaviour(Agent a,long deadline) {
			super(a, 
					MessageTemplate.MatchOntology("tour-idsaa"), 
					deadline, 
					new DataStore(), 
					"key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			if(msg.getOntology().equals("tour-ids")) {		//TODO hmmm...... NullPointerException
				try {
					itemIDs = (ArrayList<Integer>) msg.getContentObject();
					System.out.println(myAgent.getAID().getName() + ": " + itemIDs.size() + " Item ID:s received.");
				} catch (UnreadableException e) {
					System.err.println("Received tour-ids, but can't read them! Aborting...");
					myAgent.doSuspend();
				}
			}
		}
	}

	private class AskForItemInformation extends OneShotBehaviour {
		@Override
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			AID receiver = new AID(CuratorAgent.CURATOR_NAME, AID.ISLOCALNAME);		//TODO remove name later...
			msg.addReceiver(receiver);
			try {
				msg.setContentObject(itemIDs);
			} catch (IOException e) {
				msg.setContent(profile.getName());		//......
			}
			msg.setOntology("get-item-information");
			send(msg);
			System.out.println(myAgent.getAID().getName() + ": Asked Curator for item information on " + itemIDs.size() + " items.");
		}
	}

	private class ReceiveTourContentBehaviour extends MsgReceiver {
		
		public ReceiveTourContentBehaviour(Agent a,long deadline) {
			super(a, 
					MessageTemplate.MatchPerformative(ACLMessage.INFORM), 
					deadline, 
					new DataStore(), 
					"key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			if(msg.getOntology().equals("tour-info")) {		//TODO hmmm......
				try {
					tourArtifacts = (ArrayList<Artifact>) msg.getContentObject();
					System.out.println(myAgent.getAID().getName() + ": Received " + tourArtifacts.size() + " items from curator.");
				} catch (UnreadableException e) {
					System.err.println("Received artifacts, but can't read them! Aborting...");
					myAgent.doDelete();
				}
			}
		}
	}
	
	private class EmulateTour extends OneShotBehaviour {
		@Override
		public void action() {
			System.out.println("Welcome to the Virtual Exhibition!");
			for(Artifact art : tourArtifacts) {
				System.out.println("_______________________________________");
				System.out.println("Name: " + art.getName());
				System.out.println("Creator: " + art.getCreator());
				System.out.println("Date of creation: " + art.getCreationDate());
				System.out.println("Type: " + art.getType());
				System.out.println("Genre: " + art.getGenre());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.out.println("_______________________________________");
			System.out.println("That was the end of this exhibition. A new one will start shortly.");
			
		}
	}
}
