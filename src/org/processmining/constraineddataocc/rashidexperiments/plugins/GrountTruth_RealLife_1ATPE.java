package org.processmining.constraineddataocc.rashidexperiments.plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.helper.MeanMedianMode;
import org.processmining.constraineddataocc.helper.PublishResults;
import org.processmining.constraineddataocc.helper.ResultsCollection2;
import org.processmining.constraineddataocc.helper.StatesCalculator;
import org.processmining.constraineddataocc.helper.TimeStampsBasedLogToStreamConverter;
import org.processmining.constraineddataocc.models.IncrementalReplayResult;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.parameters.IncrementalReplayerParametersImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = " A 04_1a Compute Prefix Alignments Incrementally - With Bounded Traces- Ground Truth (RealData ATPE)", parameterLabels = {"Model", "Event Data" }, 
returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class },
help = "Bounded TRACES, dynamic WINDOWS, COST plus EVENT CHAR. ANALYSIS.")

public class GrountTruth_RealLife_1ATPE {
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "Compute Prefix Alignments Incrementally", requiredParameterLabels = { 0, 1})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final UIPluginContext context, final Petrinet net, XLog log) throws IOException {

		Map<Transition, String> modelElementsToLabelMap = new HashMap<>();
		Map<String, Collection<Transition>> labelsToModelElementsMap = new HashMap<>();
		TObjectDoubleMap<Transition> modelMoveCosts = new TObjectDoubleHashMap<>();
		TObjectDoubleMap<String> labelMoveCosts = new TObjectDoubleHashMap<>();

		Marking initialMarking = getInitialMarking(net);
		Marking finalMarking = getFinalMarking(net);

		setupLabelMap(net, modelElementsToLabelMap, labelsToModelElementsMap);
		setupModelMoveCosts(net, modelMoveCosts, labelMoveCosts, labelsToModelElementsMap);
		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters = new IncrementalRevBasedReplayerParametersImpl<>();
		parameters.setUseMultiThreading(false);
		parameters.setLabelMoveCosts(labelMoveCosts);
		parameters.setLabelToModelElementsMap(labelsToModelElementsMap);
		parameters.setModelMoveCosts(modelMoveCosts);
		parameters.setModelElementsToLabelMap(modelElementsToLabelMap);
		parameters.setSearchAlgorithm(IncrementalReplayer.SearchAlgorithm.A_STAR);
		parameters.setUseSolutionUpperBound(false);
		parameters.setExperiment(false);
		/////fixed parameter
		parameters.setLookBackWindow(Integer.MAX_VALUE);	
		//parameters.setLookBackWindow(Integer.MAX_VALUE);
		//parameters.setLookBackWindowType(true);  //if set to True then alignments will be reverted on the basis of observed events, not moves/states

		//-----------------------------------------------------PARAMETERS TO SET

		String[] evaluationChoices = {"cummulative", "10windows"};

		String outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results/Ground Truth/";
		String evaluationChoice = evaluationChoices[0];

		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

		ResultsCollection2 resultsCollection2 = applyGeneric(net, initialMarking, finalMarking, log, parameters, evaluationChoice/*, classifierChoice, evaluation, parallelCases, fileName, offlineCCResults*/); 

		//here we record the results for each "n" value
		globalResults.put("(" + Integer.MAX_VALUE + "/" + Integer.MAX_VALUE + ")", resultsCollection2);

		PublishResults.writeToFilesATPE(globalResults, "BPIC12", "", outputFolderPath);

		return null;		
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 applyGeneric(
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, String evaluationChoice) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}

		Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
				.construct(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
						IncrementalReplayer.Strategy.REVERT_BASED);
		return processXLog(log, net, initialMarking, replayer, Integer.MAX_VALUE, Integer.MAX_VALUE, evaluationChoice);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 processXLog(
			XLog log, Petrinet net, Marking iMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer,
					int wLimit, int nLimit, String evaluationChoice) throws IOException{

		ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);			

		if(evaluationChoice.equals("cummulative")) {
			
			final int runs = 50;
			Map<Integer, Double> elapsedTime = new HashMap<>();
			
			for(int i=0; i<=runs; i++) {                //i<runs+1 because we need to discard the first run.
				
				System.out.println("\nRun No. " + (i+1));
				//System.out.println("Window, Time Elapsed in Millis, Observed Events,Avg. Time per Event");
				
				String caseId;
				String event;
				//Date eventTimeStamp;
				int observedEvents = 0;
				
				ArrayList<Triplet<String,String,Date>>	eventLogSortedByDateCopy = new ArrayList<>();	

				for (Triplet<String,String, Date> entry : eventLogSortedByDate) {  //creates a clone of the event log with distinct case ids to stress memo
					eventLogSortedByDateCopy.add(new Triplet<String, String, Date>(entry.getValue0()+i, entry.getValue1(), entry.getValue2()));
				}
				
				System.gc();
				Instant start = Instant.now();
				
				for (Triplet<String,String, Date> entry : eventLogSortedByDateCopy) {

					caseId = entry.getValue0();
					event = entry.getValue1();

					PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event /*(window+1)*/);  //Prefix Alignment of the current observed event

					observedEvents++;
				}
				
				Instant end = Instant.now(); 
				Duration timeElapsed = Duration.between(start, end);
				elapsedTime.put(i, ((double)timeElapsed.toMillis()/(double)observedEvents));
				System.out.println(elapsedTime.get(i));
			}

			double sumATPE = 0;
			for(int j=1; j<elapsedTime.size(); j++) {  //we discard the first run.
				sumATPE += elapsedTime.get(j);
			}

			ResultsCollection2 resultsCollection2 = new ResultsCollection2();

			resultsCollection2.ATPE = sumATPE/(elapsedTime.size()-1);

			return resultsCollection2;

		}else if (evaluationChoice.equals("10window")) {

			Map<String, ArrayList<PartialAlignment>> alignmentsLife = new HashMap<String, ArrayList<PartialAlignment>>();
			Map<String, ArrayList<Integer>> alignmentsWindowsStates = new HashMap<>();
			ArrayList<Triplet<Integer, Integer, Double>> CostRecords = new ArrayList<>();	

			ArrayList<Triplet<Integer, String, Double>> universalCostRecords = new ArrayList<>();
			//LinkedHashMap<String, Double> test = new LinkedHashMap<>();
			HashMap<Integer, ArrayList<Integer>> universalStateRecords = new HashMap<>();
			HashMap<Integer, ArrayList<Integer>> universalStateRecordsInRC = new HashMap<>();


			StringBuilder allStatesSum = new StringBuilder();		
			allStatesSum.append("\n");
			allStatesSum.append("\n");
			allStatesSum.append("Window, States, Events");


			String caseId;
			String event;
			//Date eventTimeStamp;
			int observedEvents = 0;
			final double noOfWindows = 10d;

			int eventsWindowSize = (int) Math.ceil(eventLogSortedByDate.size()/noOfWindows);
			int remainder = eventLogSortedByDate.size()%eventsWindowSize;
			int window = 0;
			//replayer.getDataStore().clear();


			for (Triplet<String,String, Date> entry : eventLogSortedByDate) {

				caseId = entry.getValue0();
				event = entry.getValue1();

				//				if(caseId.equals("173718")) {
				//					System.out.println("Case found");
				//				}

				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event/*, (window+1)*/);  //Prefix Alignment of the current observed event

				if(alignmentsWindowsStates.containsKey(caseId)) {
					alignmentsWindowsStates.get(caseId).add(StatesCalculator.getNumberOfStates(partialAlignment));
				}else {
					ArrayList<Integer> tempStates = new ArrayList<>();
					tempStates.add(StatesCalculator.getNumberOfStates(partialAlignment));
					alignmentsWindowsStates.put(caseId, tempStates);
				}

				if(alignmentsLife.containsKey(caseId)) {
					alignmentsLife.get(caseId).add(partialAlignment);
				}else {
					ArrayList<PartialAlignment> temp = new ArrayList<>();
					temp.add(partialAlignment);
					alignmentsLife.put(caseId, temp);
				}

				java.util.Iterator<Triplet<Integer, String, Double>> iterator = universalCostRecords.iterator();			//recording fitness costs
				while(iterator.hasNext()) {
					Triplet<Integer, String, Double> temp = iterator.next();
					if(temp.getValue0()==(window+1) && temp.getValue1().equals(caseId)) {
						iterator.remove();
						break;
					}
				}			
				universalCostRecords.add(new Triplet<Integer, String, Double>(window+1, caseId, partialAlignment.getCost()));

				//recording states			
				if(universalStateRecords.containsKey(window+1)) {
					universalStateRecords.get(window+1).add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore())); 
				}else {
					ArrayList<Integer> tempStates = new ArrayList<>();
					tempStates.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));
					universalStateRecords.put(window+1, tempStates);
				}

				observedEvents++;

				if(observedEvents==eventsWindowSize || (window+1 == noOfWindows && observedEvents == remainder)){				

					//System.out.println("window changed");

					//					allStatesSum.append("\n");
					//					allStatesSum.append((window+1) + "," + countAllStates(alignmentsWindowsStates) + "," + observedEvents);
					//					alignmentsWindowsStates.clear();
					observedEvents = 0;

					//					int noOfObservedCases=0;
					//					int noOfNonConformantCases=0;
					//					double nonConformanceCosts=0.0;
					//
					//					for(Entry<String, List<Double>> record: replayer.getCompoundCost().entrySet()){
					//						boolean nonConformant=false;
					//						noOfObservedCases++;
					//						for(Double cost : record.getValue()) {
					//							nonConformanceCosts += cost;
					//							if(cost>0.0) {
					//								nonConformant = true;
					//							}
					//						}
					//
					//						if(nonConformant) {
					//							noOfNonConformantCases++;
					//						}
					//
					//					}
					//					CostRecords.add(new Triplet<Integer, Integer, Double>(noOfObservedCases, noOfNonConformantCases, nonConformanceCosts));
					//					replayer.getCompoundCost().clear();

					/*for(Entry<String, ArrayList<PartialAlignment>> entry_:alignmentsLife.entrySet()) {
						boolean nonconf = false;
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append(entry_.getKey() + " : ");
						for(PartialAlignment list : entry_.getValue()) {
							if(list.getCost()>0.0) {
								nonconf = true;
							}
							stringBuilder.append(list + ", ");
						}
						if(nonconf) {
							System.out.println(stringBuilder);
						}

					}*/
					window++; 

				}

			}	

			//------------------------------statistics


			//			for(Entry<String, ArrayList<PartialAlignment>> entry:alignmentsLife.entrySet()) {
			//				System.out.print(entry.getKey() + " : ");
			//				for(PartialAlignment list : entry.getValue()) {
			//					System.out.print(list + ", ");
			//				}
			//				System.out.println();
			//			}

			writeRecordsToFile(universalCostRecords, wLimit, nLimit);

			//			System.out.println("\n");
			//			System.out.println(",Non-conformant");
			//			System.out.println("Window,Cases,Costs");
			//			int index = 1;
			//			for(Triplet<Integer, Integer, Double> entry : CostRecords) {
			//				System.out.println(index + "," + entry.getValue1() + "," + entry.getValue2());
			//				index++;
			//			}
			//			
			//			System.out.println("\n");
			//
			System.out.println(allStatesSum);

			calculateStatesInWindows(universalStateRecords, "Max");

			System.out.println("\n");

			System.out.println("Window,Type1,Type2,Distinct Cases,Fresh Cases,Type1 Events,Type2 Events ");
			for(int j=1; j<=noOfWindows; j++) {
				int type1=0;
				int type2=0;
				Map<String, Integer> EventsType1 = new TreeMap<>();
				Map<String, Integer> EventsType2 = new TreeMap<>();
				Set<String> distinctCases = new HashSet<>();

				for(Entry<String, ArrayList<Triplet<String, Integer, Integer>>> entry: replayer.getEventsCategorisation().entrySet()) {
					//System.out.print(entry.getKey() + ":");
					for(Triplet<String, Integer, Integer> triplet : entry.getValue()) {
						if(triplet.getValue1()==j) {

							distinctCases.add(entry.getKey());

							if(triplet.getValue2()==0) {
								type1++;
								if(EventsType1.containsKey(triplet.getValue0())) {
									EventsType1.replace(triplet.getValue0(), EventsType1.get(triplet.getValue0())+1);
								}else {
									EventsType1.put(triplet.getValue0(), 1);
								}
							}else if(triplet.getValue2()==2){
								type2++;
								if(EventsType2.containsKey(triplet.getValue0())) {
									EventsType2.replace(triplet.getValue0(), EventsType2.get(triplet.getValue0())+1);
								}else {
									EventsType2.put(triplet.getValue0(), 1);
								}
							}
						}						
					}
				}

				System.out.println(j + "," + type1 + "," + type2 + ","  + distinctCases.size() 
				+ "," + EventsType1.get("A_SUBMITTED") + "," + EventsType1 + "," + EventsType2);
				/*System.out.println("Type1 events: " + EventsType1);
				System.out.println("Type2 events: " + EventsType2);
				System.out.println("No. of distinct cases: " + distinctCases.size());*/
				//System.out.println();
			}



			//System.out.println(eventLogSortedByDate);


			return null;
		}else {
			System.out.println("ALERT: Wrong evaluation choice");
			return null;
		}
	}

	private static void setupLabelMap(final Petrinet net, Map<Transition, String> modelElementsToLabelMap, Map<String, Collection<Transition>> labelsToModelElementsMap) {
		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				String label = t.getLabel();
				modelElementsToLabelMap.put(t, label);
				if (!labelsToModelElementsMap.containsKey(label)) {
					Collection collection = new ArrayList<org.processmining.models.graphbased.directed.petrinet.elements.Transition>();
					collection.add(t);
					labelsToModelElementsMap.put(label, collection);
					//labelsToModelElementsMap.put(label, Collections.singleton(t));
				} else {
					labelsToModelElementsMap.get(label).add(t);
				}
			}
		}				
	}	

	//TODO: needs a parameter object
	private static void setupModelMoveCosts(final Petrinet net, TObjectDoubleMap<Transition> modelMoveCosts, TObjectDoubleMap<String> labelMoveCosts,
			Map<String, Collection<Transition>> labelsToModelElementsMap) {
		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition t : net.getTransitions()) {
			if (t.isInvisible() /*|| (t.getLabel().equals("A_FINALIZED"))*/) {
				modelMoveCosts.put(t, (short) 0);
				//labelMoveCosts.put(t.getLabel(), (short) 0);
			} else {
				modelMoveCosts.put(t, (short) 1);
				//labelMoveCosts.put(t.getLabel(), (short) 1);
				//labelMoveCosts.put("A_FINALIZED", (short) 1);
			}
		}

		for(String label : labelsToModelElementsMap.keySet()) {
			labelMoveCosts.put(label, (short) 1);
		}
	}


	public static Marking getFinalMarking(PetrinetGraph net) {
		Marking finalMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getOutEdges(p).isEmpty())
				finalMarking.add(p);
		}

		return finalMarking;
	}

	public static Marking getInitialMarking(PetrinetGraph net) {
		Marking initMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getInEdges(p).isEmpty())
				initMarking.add(p);
		}

		return initMarking;
	}

	public static Integer countFinalStates(Map<String, ArrayList<Integer>> alignmentsWindowsStates) {
		Integer sum = 0;
		for(Entry<String, ArrayList<Integer>> entry: alignmentsWindowsStates.entrySet()) {
			sum += entry.getValue().get(entry.getValue().size()-1);
		}
		return sum;
	}

	public static Integer countAllStates(Map<String, ArrayList<Integer>> alignmentsWindowsStates) {
		Integer sum = 0;
		for(Entry<String, ArrayList<Integer>> entry: alignmentsWindowsStates.entrySet()) {
			sum += getSum(entry.getValue());
		}
		return sum;
	}

	public static int getSum(ArrayList<Integer> list) {
		int sum = 0;
		for (int i: list) {
			sum += i;
		}
		return sum;
	}


	public static double sumArrayList(ArrayList<Double> arrayList) {
		Double sum = 0.0;
		for(int i = 0; i < arrayList.size(); i++)
		{
			sum += arrayList.get(i);
		}
		return sum;		
	}

	//////ARCHIVE

	private boolean isFeasible(String caseId, List<Move<String, Transition>> moves, List<String> trace, Petrinet net,
			Marking iMarking) {
		boolean res = true;
		EfficientPetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(net);
		semantics.setState(semantics.convert(iMarking));
		int i = 0;
		for (Move<String, Transition> move : moves) {
			if (move.getTransition() != null) {
				res &= semantics.isEnabled(move.getTransition());
				if (!res) {
					System.out.println("Violation for case " + caseId + ", " + "move " + move.toString() + ", at: "
							+ semantics.getStateAsMarking().toString());
				}
				semantics.directExecuteExecutableTransition(move.getTransition());
			}
			if (move.getEventLabel() != null) {
				//				res &= move.getEventLabel().equals(trace.get(i).toString() + "+complete");
				res &= move.getEventLabel().equals(trace.get(i).toString());
				if (!res) {
					System.out.println("Violation for case " + caseId + " on label part. original: " + trace.toString()
					+ ", moves: " + moves.toString());
				}
				i++;
			}
			if (!res)
				break;
		}
		return res;
	}


	private static int calculateDiffWindowRelatedCases(Set<String> parentCasesSet, Set<String> childCasesSet){
		int score = 0;
		for (String item: childCasesSet) {
			if(parentCasesSet.contains(item)) {
				score++;
			}
		}
		return score;
	}

	private static Date getWindowTimeStamp(ArrayList<Triplet<String,String,Date>> sortedByValue, String choice) {
		//LinkedList<Pair<String,String>> listKeys = new LinkedList<Pair<String,String>>(sortedByValue.keySet());
		if(choice.equals("start")) {
			//return sortedByValue.get( listKeys.getFirst());
			return sortedByValue.get(0).getValue2();
		}else {
			//return sortedByValue.get( listKeys.getLast());
			return sortedByValue.get(sortedByValue.size()-1).getValue2();
		}		
	}	

	public static <A extends PartialAlignment<String, Transition, Marking>> A processEventUsingReplayer(String caseId,
			String event,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer) {
		return replayer.processEvent(caseId, event);
	}

	private List<String> toStringList(XTrace trace, XEventClasses classes) {
		List<String> l = new ArrayList<>(trace.size());
		for (int i = 0; i < trace.size(); i++) {
			l.add(i, classes.getByIdentity(XConceptExtension.instance().extractName(trace.get(i))).toString());
		}
		return l;
	}

	private static double calculateCurrentCosts(TObjectDoubleMap<String> costPerTrace) {
		double totalCost = 0;
		for (String t : costPerTrace.keySet()) {
			//totalCost += count.get(t) * costPerTrace.get(t);
			totalCost += costPerTrace.get(t);
		}
		return totalCost;
	}

	private static void writeRecordsToFile(ArrayList<Triplet<Integer, String, Double>> universalRecords, int w, int n) {

		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/fresh/N/w="+  w + ",n=" + n + ".csv";
		File file = new File(outputFilePath);	
		BufferedWriter bf = null;
		boolean first = true;
		try {
			bf = new BufferedWriter(new FileWriter(file));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		try {
			bf.write("window,case,cost");
			bf.newLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for(Triplet<Integer, String, Double> rec:universalRecords) {
			//System.out.println(rec);
			try {
				if(first) {
					first = false;
				}else {
					bf.newLine();
				}
				bf.write(rec.getValue0() + "," + rec.getValue1() + "," + rec.getValue2());

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			bf.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			bf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void calculateStatesInWindows(HashMap<Integer, ArrayList<Integer>> universalStateRecords, String option) {
		System.out.println("\nWindow," + option + " states stored");
		//if(option.equals("Max")) {
		for(Entry<Integer, ArrayList<Integer>> rec : universalStateRecords.entrySet()) {
			System.out.println(rec.getKey() + "," + Collections.max(rec.getValue()));

		}
		//}else if (option.equals("Mean")) {
		System.out.println("\nWindow, Mean states stored");
		for(Entry<Integer, ArrayList<Integer>> rec2 : universalStateRecords.entrySet()) {
			System.out.println(rec2.getKey() + "," + MeanMedianMode.findMean(rec2.getValue()));
		}
	}

	private static void publishResults(LinkedHashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice, String outputFolderPath) {

		//-------------- CC information

		StringBuilder stringBuilderHeader = new StringBuilder();
		stringBuilderHeader.append("Case,");

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderHeader.append(entryOuter.getKey() + ",");
		}		

		StringBuilder stringBuilderCosts = new StringBuilder();

		boolean first = true;

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			if(first) {
				for(Entry<String, Double> entryInner : entryOuter.getValue().costRecords.entrySet()) {
					String caseId = entryInner.getKey();
					stringBuilderCosts.append(caseId + ",");				

					for(Entry<String, ResultsCollection2> records : globalResults.entrySet()) {
						stringBuilderCosts.append(records.getValue().costRecords.get(caseId) + ",");						
					}
					//stringBuilderCosts.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
					//stringBuilderCosts.append(entryOuter.getValue().sumOfEternalPrematureCases);
					stringBuilderCosts.append("\n");
				}
				first=false;				
			}			
		}	

		String outputFilePath = outputFolderPath +  classifierChoice + "_" + fileName + "_CC.csv";
		File file = new File(outputFilePath);

		BufferedWriter bf = null;

		if(file.exists() && !file.isDirectory()){
			try {
				bf = new BufferedWriter(new FileWriter(file, true));
				bf.newLine();
				bf.write(stringBuilderHeader.toString());
				bf.write(stringBuilderCosts.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else {
			try {
				bf = new BufferedWriter(new FileWriter(file));
				bf.write(stringBuilderHeader.toString());
				bf.newLine();
				bf.write(stringBuilderCosts.toString());

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		try {			
			bf.flush();
			bf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//-------------- States information

		StringBuilder stringBuilderATPE = new StringBuilder();
		//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getKey() + ",");
		}

		stringBuilderATPE.append("\n");

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getValue().maxStates + ",");
		}

		System.out.println(stringBuilderATPE.toString());	

		outputFilePath = outputFolderPath +  classifierChoice + "_" + fileName + "_States.csv";
		file = new File(outputFilePath);
		BufferedWriter bf2 = null;

		try {	
			bf2 = new BufferedWriter(new FileWriter(file, true));
			bf2.write(stringBuilderATPE.toString());
			bf2.flush();
			bf2.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//-------------- additional information

		StringBuilder stringBuilderCasesMeta = new StringBuilder();
		//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");
		stringBuilderCasesMeta.append("W and N combination, Forgotten Premature, Forgotten Eternal");
		stringBuilderCasesMeta.append("\n");
		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderCasesMeta.append(entryOuter.getKey() + ",");
			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfEternalPrematureCases + "\n");
		}

		System.out.println(stringBuilderCasesMeta.toString());	

		outputFilePath = outputFolderPath +  classifierChoice + "_" + fileName + "_meta.csv";
		file = new File(outputFilePath);
		BufferedWriter bf3 = null;

		try {	
			bf3 = new BufferedWriter(new FileWriter(file, true));
			bf3.write(stringBuilderCasesMeta.toString());
			bf3.flush();
			bf3.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
