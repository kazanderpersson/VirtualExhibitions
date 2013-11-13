import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/*
 * RecieveItemInfoRequestBehaviour extends CyclicBehaviour
 * 
 */

public class CuratorAgent extends Agent {
	
	public static final String CURATOR_NAME = "curator";
	ArrayList<Artifact> artifacts;

	private final String ARTIFACTS_SOURCE = "Artifacts_database1.txt";
	
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
		
		addBehaviour(new UpdateArtifacts(this, 100000));
		addBehaviour(new HandleRequest());
		
		
		/*****************************************************************/
		ServiceDescription artifactInformation = new ServiceDescription();
		artifactInformation.setType("artifact-lookup");
		artifactInformation.setName("get-artifact-info");
		artifactInformation.addOntologies("get-item-information");
		
		ServiceDescription artifactSearch = new ServiceDescription();
		artifactSearch.setType("artifact-search");
		artifactSearch.setName("search-for-artifacts");
		artifactSearch.addOntologies("request-ids");
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(artifactInformation);
		dfd.addServices(artifactSearch);
		try {
			DFService.register(this, dfd);
			System.out.println(getName() + ": Successfully registered services.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		/****************************************************************/
	}
	
	private class HandleRequest extends CyclicBehaviour {
		@Override
		public void action() {
			ACLMessage request = receive();
			if(request != null) {
				if(request.getOntology().equals("request-ids")) {
					System.out.println(getName() + ": Received request from Tour Guide. Will handle.");
					handleTourGuideRequest(request);
				} else if(request.getOntology().equals("get-item-information")) {
					System.out.println(getName() + ": Received request from Profiler. Will handle.");
					handleProfilerRequest(request);
				}
			} else
				block();
		}
		
		private void handleTourGuideRequest(ACLMessage request) {
			AID tourGuide = request.getSender();
			ArrayList<String> interests;
			try {
				interests = (ArrayList<String>) request.getContentObject();
				System.out.println(getName() + ": Will handle " + interests.size() + " interests. (Successfully read message)");
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
			send(response);
			System.out.println(myAgent.getAID().getName() + ":Response message sent to TourGuide with " + ids.size() + " IDs.");
		}
		
		private void handleProfilerRequest(ACLMessage request) {
			AID profiler = request.getSender();
			ArrayList<Integer> requestedIDs;
			try {
				requestedIDs = (ArrayList<Integer>) request.getContentObject();
				System.out.println(getName() + ": Received request from Profiler. He requested " + requestedIDs.size() + " IDs.");
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
			send(response);
			System.out.println(myAgent.getAID().getName() + ":Response message sent to Profiler with " + relatedArtifacts.size() + " artifacts.");
		}
	}
	
	private class UpdateArtifacts extends TickerBehaviour {
		public UpdateArtifacts(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			try {
				File f = new File(ARTIFACTS_SOURCE);
				Scanner scan = new Scanner(f);
				
				scan.nextLine();
				scan.nextLine();
				
				while(scan.hasNextLine()) {
					scan.nextLine();
					String name = scan.nextLine();
					String creator = scan.nextLine();
					String date = scan.nextLine();
					String type = scan.nextLine();
					String description = scan.nextLine();
					String[] tags = scan.nextLine().split(",");
					
					ArrayList<String> tagList = new ArrayList<>(Arrays.asList(tags));
					
					Artifact artifact = new Artifact(name.hashCode(), name, creator, date, type, description, tagList);
					if(!artifacts.contains(artifact)) {
						artifacts.add(artifact);
//						System.out.println("Added artifact with id=" + artifact.getId());
					}
				}
				
				scan.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
}