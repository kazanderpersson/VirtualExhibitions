import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/*
 * RecieveItemInfoRequestBehaviour extends CyclicBehaviour
 * 
 */

public class CuratorAgent extends Agent {
	
	public static final String CURATOR_NAME = "alice";
	ArrayList<Artifact> artifacts;
	
	@Override
	protected void setup() {
		try {  //randomly assign an artifact database to agent
			Scanner sc;
			if ((int)(Math.random()*2) == 0)
				sc = new Scanner(new File("Artifacts_database1.txt"));
			else
				sc = new Scanner(new File("Artifacts_database2.txt"));
			
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

		addBehaviour(new listenBehaviour());
	}
	
	class listenBehaviour extends CyclicBehaviour {
		public void action() {
			
			ACLMessage msg = myAgent.receive();
			if (msg != null) {
				if (msg.getContent().equals("ping")) {
					System.out.println("Received a ping message");
					ACLMessage msgBack = new ACLMessage(ACLMessage.INFORM);
					msgBack.addReceiver(msg.getSender());
					msgBack.setLanguage("English");
					msgBack.setOntology("ping-message");
					msgBack.setContent("ping");
					send(msgBack);
					System.out.println("Reply sent to " + msg.getSender());
				}
			}
			else {
			block();
			}
			

			
		}
}
}