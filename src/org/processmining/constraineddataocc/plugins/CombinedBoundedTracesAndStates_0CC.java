package org.processmining.constraineddataocc.plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;
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
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = " 03_1 Compute Prefix Alignments Incrementally With Bounds on States and Traces", parameterLabels = {"Model", "Event Data" }, 
returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class },
help = "Conformance checking for unlimited cases but storing only a limited number of cases in memory.")

public class CombinedBoundedTracesAndStates_0CC {
	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {0,1})

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
		//parameters.setLookBackWindowType(true);  //if set to True then alignments will be reverted on the basis of observed events, not moves/states
		
		
		
		//-----------------------------------------------------parameters to set
		String logName = "BPIC12";
		int[] lookBackWindowChoices = {1,2,3,4,5,10};     //here it represents the state limit.  For offline approach: parameters.setLookBackWindow(Integer.MAX_VALUE);
		int[] maxCasesToStoreChoices = {50,100,200,300,400,500,1000};		
		String[] evaluationChoices = {"cummulative", "10windows"};

		String evaluationChoice = evaluationChoices[0];
		String outputFolderPath = "D:/Experiments/Results/WN/";

		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

		for(int i=0; i<lookBackWindowChoices.length; i++) {  //w size
			parameters.setLookBackWindow(lookBackWindowChoices[i]);
			
			for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size

				parameters.setMaxCasesToStore(maxCasesToStoreChoices[j]);				
				System.out.println("\t Feature Size: " + parameters.getLookBackWindow() + ", Max. Cases: " + parameters.getMaxCasesToStore());

				ResultsCollection2 resultsCollection2 = applyGeneric(net, initialMarking, finalMarking, log, parameters, parameters.getMaxCasesToStore(), evaluationChoice/*, classifierChoice, evaluation, parallelCases, fileName, offlineCCResults*/); 

				//here we record the results for each (w,n) pair
				globalResults.put("(" + parameters.getLookBackWindow() + "/" + parameters.getMaxCasesToStore() + ")", resultsCollection2);
			}
		}		
		PublishResults.writeToFilesCC(globalResults, logName, "", outputFolderPath);	

		return null;
	}
	
	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {0})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final UIPluginContext context, final Petrinet net) throws IOException {

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
		//parameters.setLookBackWindowType(true);  //if set to True then alignments will be reverted on the basis of observed events, not moves/states

		
		//-----------------------------------------------------parameters to set
		
		String[] logTypes = {"a12", "a22", "a32"};
		int[] lookBackWindowChoices = {1,2,3,4,5,10};     //here it represent the numOfFeatures
		int[] maxCasesToStoreChoices = {5,10,15,20,25,50};
		String[] evaluationChoices = {"cummulative", "10windows"};

		String evaluationChoice = evaluationChoices[0];
		//String[] logVariantChoices = {"a12f0timed", "a12f1timed", "a12f5timed", "a12f9timed"};
		String logType = logTypes[1];
		String inputFolderPath = "D:/Experiments/Event Logs/" + logType + "/";
		String outputFolderPath = "D:/Experiments/Results/WN/";

		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();
		
		File inputFolder = new File(inputFolderPath);
		//File newInputFolder = new File(inputFolder + "/timed logs/");
		
		for (File file : inputFolder.listFiles()) { 
			System.out.println(file.getName());

			String fileName = FilenameUtils.getBaseName(file.getName());
			String fileExtension = FilenameUtils.getExtension(file.getName());
			
			XLog log = null; 

			if(!fileExtension.equals("xes")) {
				System.out.println("error!! not an xes file");
				continue;
			}

			try {
				log = new XUniversalParser().parse(file).iterator().next();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			for(int i=0; i<lookBackWindowChoices.length; i++) {  //w size
				parameters.setLookBackWindow(lookBackWindowChoices[i]);

				for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size
					
					parameters.setMaxCasesToStore(maxCasesToStoreChoices[j]);						
					System.out.println("\t Feature Size: " + parameters.getLookBackWindow() + ", Max. Cases: " + parameters.getMaxCasesToStore());
					
					ResultsCollection2 resultsCollection2 = applyGeneric(net, initialMarking, finalMarking, log, parameters, parameters.getMaxCasesToStore(), evaluationChoice/*, classifierChoice, evaluation, parallelCases, fileName, offlineCCResults*/); 
					globalResults.put("(" + parameters.getLookBackWindow() + "/" + parameters.getMaxCasesToStore() + ")", resultsCollection2);
				}
			}
			PublishResults.writeToFilesCC(globalResults, fileName, "", outputFolderPath);
					
				}
		return null;
		}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 applyGeneric(
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, int maxCasesToStore, String evaluationChoice) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}		
		Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
				.construct3(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
						IncrementalReplayer.Strategy.REVERT_BASED);
		return processXLog(log, net, initialMarking, replayer, parameters.getLookBackWindow(), maxCasesToStore, evaluationChoice);		
	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 processXLog(
			XLog log, Petrinet net, Marking iMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer, int wLimit, int nLimit, String evaluationChoice) throws IOException{

		ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);			

		if (evaluationChoice.equals("cummulative")) {

			ArrayList<Triplet<Integer, String, Double>> universalCostRecords = new ArrayList<>();
			ArrayList<Integer> universalStateRecords = new ArrayList<>();

			String caseId;
			String event;

			for (Triplet<String,String, Date> entry : eventLogSortedByDate) {

				caseId = entry.getValue0();
				event = entry.getValue1();

				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event, /*window+1*/0);  //Prefix Alignment of the current observed event

				java.util.Iterator<Triplet<Integer, String, Double>> iterator = universalCostRecords.iterator();			//recording fitness costs
				while(iterator.hasNext()) {
					Triplet<Integer, String, Double> temp = iterator.next();
					if(temp.getValue0()==(0) && temp.getValue1().equals(caseId)) {
						iterator.remove();
						break;
					}
				}			
				universalCostRecords.add(new Triplet<Integer, String, Double>(/*window+1*/0, caseId, partialAlignment.getCost()));		
				universalStateRecords.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));			
			}

			ResultsCollection2 resultsCollection2 = new ResultsCollection2();

			for(Triplet<Integer, String, Double> rec : universalCostRecords) {
				resultsCollection2.costRecords.put(rec.getValue1(), rec.getValue2());
			}

			resultsCollection2.maxStates = Collections.max(universalStateRecords);
			resultsCollection2.featureSize = wLimit;
			resultsCollection2.caseLimitSize = nLimit;

			return resultsCollection2;

		}else if(evaluationChoice.equals("10windows")) {

			Map<String, ArrayList<PartialAlignment>> alignmentsLife = new HashMap<String, ArrayList<PartialAlignment>>();
			Map<String, ArrayList<Integer>> alignmentsWindowsStates = new HashMap<>();

			ArrayList<Triplet<Integer, String, Double>> universalCostRecords = new ArrayList<>();
			HashMap<Integer, ArrayList<Integer>> universalStateRecords = new HashMap<>();


			String caseId;
			String event;
			//Date eventTimeStamp;
			int observedEvents = 0;
			final double noOfWindows = 10d;

			int eventsWindowSize = (int) Math.ceil(eventLogSortedByDate.size()/noOfWindows);
			int remainder = eventLogSortedByDate.size()%eventsWindowSize;
			int window = 0;
			//replayer.getDataStore().clear();

			StringBuilder allStatesSum = new StringBuilder();		
			allStatesSum.append("\n");
			allStatesSum.append("\n");
			allStatesSum.append("Window, States, Events");


			for (Triplet<String,String, Date> entry : eventLogSortedByDate) {

				caseId = entry.getValue0();
				event = entry.getValue1();

				//				if(caseId.equals("173718")) {
				//					System.out.println("Case found");
				//				}

				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event, (window+1));  //Prefix Alignment of the current observed event

				if(alignmentsWindowsStates.containsKey(caseId)) {
					alignmentsWindowsStates.get(caseId).add(StatesCalculator.getNumberOfStates(partialAlignment));
				}else {
					ArrayList<Integer> tempStates = new ArrayList<>();
					tempStates.add(StatesCalculator.getNumberOfStates(partialAlignment));
					alignmentsWindowsStates.put(caseId, tempStates);
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


					window++; 
					observedEvents = 0;

				}

			}	

			writeRecordsToFile(universalCostRecords, wLimit, nLimit);

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
				+ "," + EventsType1.get("A_Submitted") + "," + EventsType1 + "," + EventsType2);
				
			}

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

	

	
	private static void writeRecordsToFile(ArrayList<Triplet<Integer, String, Double>> universalRecords, int w, int n) {

		String outputFilePath = "D:/Experiments/results/WN/w="+  w + ",n=" + n + ".csv";
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

	
}
