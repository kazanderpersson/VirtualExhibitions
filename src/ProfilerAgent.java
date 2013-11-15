import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
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

	public static final String PROFILER_NAME = "profiler";
	private final int TOUR_FREQUENCY = 10000;
	
	@Override
	protected void setup() {	
		initiatieProfile();
		addBehaviour(new StartTourBehaviour(this, TOUR_FREQUENCY));
	}
	
	private void initiatieProfile() {
		try { //randomly create (from database file) and assign a profile for agent
			int counter = 0;
			Scanner count = new Scanner(new File("Profiles.txt")); 
			while (count.hasNextLine()) {
				count.nextLine();
				counter++;
			}
			count.close();
			
			counter -= 2; //remove first rows	
			int numberOfProfiles = (int)(counter/6);
			int pickRandomProfile = (int)(Math.random()*numberOfProfiles); 
			int startLineOfProfile = pickRandomProfile*6;	
			Scanner sc = new Scanner(new File("Profiles.txt"));
			sc.nextLine();
			sc.nextLine();
			for (int i=0; i < startLineOfProfile; i++)
				sc.nextLine();
			
			sc.nextLine();
			String[] input = new String[5];
			for (int i=0; i<input.length; i++) { //read in the chosen profile from file
				input[i] = sc.nextLine();
				System.out.println(input[i]); //(print which profile that was created)
			}
			sc.close();
			profile = new Profile(pickRandomProfile+1, input[0], input[1], Integer.parseInt(input[2]), input[3], new ArrayList<String>(Arrays.asList(input[4].split(", "))));
		} catch (IOException e) {}
	}
	
	private class StartTourBehaviour extends TickerBehaviour {

		public StartTourBehaviour(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			SequentialBehaviour seq = new SequentialBehaviour(myAgent);
			seq.addSubBehaviour(new AskForInterestingItems());
			seq.addSubBehaviour(new ReceiveInterestingItemsBehaviour(myAgent, MsgReceiver.INFINITE));
			seq.addSubBehaviour(new AskForItemInformation());
			seq.addSubBehaviour(new ReceiveTourContentBehaviour(myAgent, MsgReceiver.INFINITE));
			seq.addSubBehaviour(new EmulateTour());
			addBehaviour(seq);			
		}
	}
	
	private class AskForInterestingItems extends OneShotBehaviour {
		@Override
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			
			/**********************************************/
			/******  Look for a tour guide in the DF  *****/
			/**********************************************/
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("give-tour");
			template.addServices(sd);
			
			try {
				DFAgentDescription[] result = DFService.searchUntilFound(myAgent, getDefaultDF(), template, null, 20000);
				if(result.length>0) {
					AID receiver = result[0].getName();
					msg.addReceiver(receiver);
					System.out.println("Found a tour guide: " + receiver.getName());
				}
			} catch (FIPAException e1) {
				e1.printStackTrace();
				return;
			}
			
			/**********************************************/
			
			try {
				msg.setContentObject(profile);
				System.out.println(getName() + ": Successfully added profile with name: " + profile.getName() + " to a message.");
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
					MessageTemplate.MatchOntology("tour-ids"), 
					deadline, 
					new DataStore(), 
					"key");
		}
		
		@Override
		public void handleMessage(ACLMessage msg) {
			if(msg != null && msg.getOntology().equals("tour-ids")) {		//TODO hmmm...... NullPointerException
				try {
					itemIDs = (ArrayList<Integer>) msg.getContentObject();
					System.out.println(myAgent.getAID().getName() + ": " + itemIDs.size() + " Item ID:s received.");
				} catch (UnreadableException e) {
					System.err.println("Received tour-ids, but can't read them! Aborting...");
					myAgent.doDelete();
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
				System.err.println("Could not add Item IDs to a message for Curator...");
				msg.setContent(profile.getName());		//......
			}
			msg.setOntology("get-item-information");
			send(msg);
			System.out.println(myAgent.getAID().getName() + ": Asked Curator for item information on some items.");
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
			if(msg != null && msg.getOntology().equals("tour-info")) {		//TODO hmmm......
				try {
					tourArtifacts = (ArrayList<Artifact>) msg.getContentObject();
					System.out.println(myAgent.getAID().getName() + ": Received " + tourArtifacts.size() + " items from curator.");
				} catch (UnreadableException e) {
					System.out.println("Received artifacts, but can't read them! Aborting...");
					myAgent.doDelete();
				}
			} else
				System.out.println(getName() + ": Received an unexpected message...............................");
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
				profile.setVisitedItem(art.getId());				//TODO Kanske onödig?
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
