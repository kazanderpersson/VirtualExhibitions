import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SimpleAchieveREInitiator;

public class TestAgent extends Agent {

	String STATE_A = "one day ";
	String STATE_B = "Alex ";
	String STATE_C = "Eric ";
	String STATE_D = "Went to the Beach ";
	public void setup() {
		System.out.println("Agent \"" + getAID().getName() + "\" is ready!");
		
		addBehaviour(new sendBehaviour(this, 1000));
		//addBehaviour(new sendBehaviour(this, 2000));
		
		/*FSMBehaviour fsm = new FSMBehaviour(this) {
			public void onStart() {
				System.out.println("So, ");
			}
			public int onEnd() {
				System.out.println("The end");
				myAgent.doDelete();
				return super.onEnd();
			}
			
		};	
		fsm.registerFirstState(new RandomGenerator(2), STATE_A);
		fsm.registerState(new NamePrinter(), STATE_B);
		fsm.registerState(new NamePrinter(), STATE_C);
		fsm.registerLastState(new NamePrinter(), STATE_D);
		fsm.registerTransition(STATE_A, STATE_B, 0);
		fsm.registerTransition(STATE_A, STATE_C, 1);
		fsm.registerDefaultTransition(STATE_B, STATE_D);
		fsm.registerDefaultTransition(STATE_C, STATE_D);
		addBehaviour(fsm);*/
		
		/*ParallelBehaviourImpl pb = new ParallelBehaviourImpl();
			pb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("one");
				}
			});
			pb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("two");
				}
			});
			pb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("three");
				}
			});
			pb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("four");
				}
			});

			
			SequentialBehaviourImpl sb = new SequentialBehaviourImpl();
			sb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("oneS");
				}
			});
			sb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("twoS");
				}
			});
			sb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("threeS");
				}
			});
			sb.addSubBehaviour(new OneShotBehaviour(this) {
				public void action(){
					System.out.println("fourS");
				}
			});
			
			SequentialBehaviourImpl sb2 = new SequentialBehaviourImpl();
			sb2.addSubBehaviour(sb);
			sb2.addSubBehaviour(pb);
			addBehaviour(sb2);*/
		}
	
	private class SimpleAchieveREInitiatorImpl extends SimpleAchieveREInitiator {
		
		public SimpleAchieveREInitiatorImpl(Agent a, ACLMessage msg) {
			super(a,msg);
		}
		
		protected ACLMessage prepareRequest(ACLMessage msg) {
			//msg.setContent("what is the time?");
			//msg.setContent("yadayada");
			//System.out.println("yadayada");
			
			//if (msg.getContent().equals("what is the time?"))
			//	System.out.println("Sending: what is the time?");
			return super.prepareRequest(msg);
		}
		
		protected void handleInform(ACLMessage inform) {
			System.out.println("Received:"+inform.getContent());
		}
		
		protected void handleNotUnderstood(ACLMessage msg) {
			System.err.println("Question not understood");
			super.handleNotUnderstood(msg);
		}
		
	}
	
	private class sendBehaviour extends TickerBehaviour {
		sendBehaviour(Agent a, long interval) {
			super(a, interval);
		}
		@Override
		public void onTick() {
			Artifact a = new Artifact(1, "testartifact23", "kaz", "1943", "film", "artifact for testing serialization", new ArrayList<String>());
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
			request.addReceiver(new AID("recieveagent", AID.ISLOCALNAME));
			//request.setContent("what is the time?");
			try {request.setContentObject(a);} catch (IOException e) {e.printStackTrace();}
			myAgent.addBehaviour(new SimpleAchieveREInitiatorImpl(myAgent, request));
			//myAgent.addBehaviour(b);
			/*ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(new AID("recieveagent", AID.ISLOCALNAME));
			msg.setOntology("test");
			msg.setContent("ping");
			send(msg);*/
		}
	}
	
/*	// This behaviour just prints its name
	private class NamePrinter extends OneShotBehaviour {
		public void action() {
			System.out.println(getBehaviourName());
		}
	}
	
	/**
	   This behaviour prints its name and exits with a random value between 0 and a given integer value
	   
	private class RandomGenerator extends NamePrinter {
		private int maxExitValue;
		private int exitValue;
		
		private RandomGenerator(int max) {
			super();
			maxExitValue = max;
		}
		
		public void action() {
			System.out.println(getBehaviourName());
			exitValue = (int) (Math.random() * maxExitValue);
		}
		
		public int onEnd() {
			return exitValue;
		}
	}

	private class ParallelBehaviourImpl extends ParallelBehaviour {
		private static final long serialVersionUID = 1L;
		
	}
	
	private class SequentialBehaviourImpl extends SequentialBehaviour {
		private static final long serialVersionUID = 1L;
		
	}*/
}
