import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREResponder;
import jade.proto.SimpleAchieveREResponder;
import jade.proto.states.MsgReceiver;

/**
 * The Curator will take requests from a Profiler or TourAgent. 
 * It can either be a "search for related items" request, 
 * or a "give me information about these items" request.
 * 
 * Behaviours:
 * ArtifactLookup - Waits for a search request, and responds with some item IDs or item information.
 * UpdateArtifacts - Periodically update the list of artifacts.
 */
public class CuratorAgent extends Agent {
	
	public static final String CURATOR_NAME = "curator";
	ArrayList<Artifact> artifacts;
	
	private String item;
	private int price;
	private AID marketAgent;

	private String ARTIFACTS_SOURCE = "";
	
	@Override
	protected void setup() {
		initArtifacts();
		publishServices();
		addBehaviour(new UpdateArtifacts(this, 100000));
		
		MessageTemplate mt = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST);
		addBehaviour(new ArtifactLookup(this, mt));
		
		addBehaviour(new WaitForAuction());
		addBehaviour(new HandleCFP());
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
	
	private void publishServices() {
		/*****************************************************************/
		/**************  Publish the two services to DF  *****************/
		/*****************************************************************/
		ServiceDescription artifactInformation = new ServiceDescription();
		artifactInformation.setType("artifact-lookup");
		artifactInformation.setName("get-artifact-info");
		artifactInformation.addOntologies("get-item-information");
		Property args = new Property("args", "Send a Profile and use the ontology: get-item-information");
		artifactInformation.addProperties(args);
		
		ServiceDescription artifactSearch = new ServiceDescription();
		artifactSearch.setType("artifact-search");
		artifactSearch.setName("search-for-artifacts");
		artifactSearch.addOntologies("request-ids");
		args = new Property("args", "Send an ArrayList<Integer> of IDs and use the ontology: request-ids");
		artifactSearch.addProperties(args);
		
		ServiceDescription buyingArtifacts = new ServiceDescription();
		buyingArtifacts.setName("buying-artifacts");
		buyingArtifacts.setType("buying-artifacts");
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(artifactInformation);
		dfd.addServices(artifactSearch);
		dfd.addServices(buyingArtifacts);
		try {
			DFService.register(this, dfd);
			System.out.println(getName() + ": Successfully registered services.");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		/****************************************************************/
	}
	
	private void initArtifacts() {
		try {  //randomly assign an artifact database to agent
			Scanner sc;
			if ((int)(Math.random()*2) == 0) {
				ARTIFACTS_SOURCE = "Artifacts_database1.txt";
				sc = new Scanner(new File(ARTIFACTS_SOURCE));
			}

			else {
				ARTIFACTS_SOURCE = "Artifacts_database2.txt";
				sc = new Scanner(new File(ARTIFACTS_SOURCE));
			}

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

	private class ArtifactLookup extends SimpleAchieveREResponder {
		public ArtifactLookup(Agent a, MessageTemplate mt) {
			super(a,mt);
		}
		
		protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
			if (request.getOntology().equals("request-ids"))
				return handleTourGuideRequest(request);
			else if (request.getOntology().equals("get-item-information"))
				return handleProfilerRequest(request);
			else {
				ACLMessage informDone = request.createReply();
				informDone.setPerformative(ACLMessage.NOT_UNDERSTOOD);
				return informDone;
			}
		}
			
			protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException {
				return null;
			}
			
			private ACLMessage handleTourGuideRequest(ACLMessage request) {
				AID tourGuide = request.getSender();
				ArrayList<String> interests;
				try {
					interests = (ArrayList<String>) request.getContentObject();
					//System.out.println(getName() + ": Will handle " + interests.size() + " interests. (Successfully read message)");
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
				return response;
				//System.out.println(myAgent.getAID().getName() + ":Response message sent to TourGuide with " + ids.size() + " IDs.");
			}
			
			private ACLMessage handleProfilerRequest(ACLMessage request) {
				AID profiler = request.getSender();
				ArrayList<Integer> requestedIDs;
				try {
					requestedIDs = (ArrayList<Integer>) request.getContentObject();
					//System.out.println(getName() + ": Received request from Profiler. He requested " + requestedIDs.size() + " IDs.");
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
				return response;
				//System.out.println(myAgent.getAID().getName() + ":Response message sent to Profiler with " + relatedArtifacts.size() + " artifacts.");
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
					String[] tags = scan.nextLine().split(", ");
					
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

	private class WaitForAuction extends SimpleBehaviour {
		
		private boolean newAuctionStarted = false;
		
		@Override
		public void action() {
			MessageTemplate template = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.INFORM).MatchContent("inform-start-of-auction");
			ACLMessage message = receive(template);
			if(message != null) {
				marketAgent = message.getSender();
				newAuctionStarted = true;
				System.out.println(getName() + ": Received Auction start message!");
			}
		}

		@Override
		public boolean done() {
			return newAuctionStarted;
		}
	}

	private class HandleCFP extends SimpleBehaviour {
		private boolean cfpReceived = false;
		
		@Override
		public void action() {
			MessageTemplate template = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION).MatchPerformative(ACLMessage.CFP).MatchSender(marketAgent);
			ACLMessage cfp = receive(template);
			if(cfp != null) { //else block();
				System.out.println(getName() + ": Received CFP - " + cfp.getContent());
				try {
					item = cfp.getContent().split(" ")[0];
					price = Integer.parseInt(cfp.getContent().split(" ")[1]);
					
					ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
					proposal.addReceiver(marketAgent);
					boolean accept = acceptOffer(item, price);
					if(accept)
						proposal.setContent("yes");
					else
						proposal.setContent("no");
					send(proposal);
					System.out.println(getName() + ": Proposal sent to Auctioneer. Proposal= " + proposal.getContent());
				} catch(Exception e) {
					ACLMessage notUnderstood = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
					notUnderstood.addReceiver(marketAgent);
					send(notUnderstood);
				}
				cfpReceived = true;
			}
		}

		@Override
		public boolean done() {
			return cfpReceived;
		}
		
		private boolean acceptOffer(String item, int price) { //task 2, bidding strategy
			Random rand = new Random();
			int a = rand.nextInt(100);
			return a > 90;
		}
	}
}