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
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;


/**
 * Behaviours: 
 *		StartTourBehaviour extends Ticker/Cyclic
 *		FetchTourInformationBehaviour - SequentialBehaviour?
 *					AskForInterestingItems - OneShot
 *					ReceiveInterestingItems - MsgReceiver
 *					AskForItemInformation - OneShot
 *					ReceiveItemInformation - MsgReceiver
 *		
 */
public class ProfilerAgent extends Agent {
	
	Profile profile;
	
	private ArrayList<Integer> itemIDs;
	private ArrayList<Artifact> tourArtifacts;
	
	private final String TOUR_GUIDE_NAME = "bob";
	private final String CURATOR_NAME = "alice";
	
	@Override
	protected void setup() {
		
		ArrayList<Profile> profiles = new ArrayList<Profile>();
		ArrayList<Artifact> artifacts = new ArrayList<Artifact>();
		try{ 
		Scanner sc = new Scanner(new File("Profiles.txt")); 
		sc.nextLine();
		sc.nextLine();
		
		
		
		sc = new Scanner(new File("Artifacts.txt")); 
		}
		catch(IOException e){}
		

		
		
		SequentialBehaviour seq = new SequentialBehaviour(this);
		seq.addSubBehaviour(new AskForInterestingItems());
		seq.addSubBehaviour(new ReceiveInterestingItemsBehaviour(this, 10000));
		seq.addSubBehaviour(new AskForItemInformation());
		seq.addSubBehaviour(new ReceiveTourContentBehaviour(this, 10000));
		addBehaviour(seq);
	}
	
	private class AskForInterestingItems extends OneShotBehaviour {
		@Override
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			AID receiver = new AID(TOUR_GUIDE_NAME, AID.ISLOCALNAME);
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
			AID receiver = new AID(CURATOR_NAME, AID.ISLOCALNAME);
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
					myAgent.doSuspend();
				}
			}
		}
	}
}
