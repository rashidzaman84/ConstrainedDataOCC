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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.helper.MeanMedianMode;
import org.processmining.constraineddataocc.helper.PublishResults;
import org.processmining.constraineddataocc.helper.ResultsCollection2;
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
@Plugin(name = " A 03_2a Compute Prefix Alignments Incrementally - With Combined Bounded States and Traces- C+M (Synthetic ATPE)", parameterLabels = {"Model"}, 
returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class },
help = "Bounded TRACES, dynamic WINDOWS, COST plus EVENT CHAR. ANALYSIS.")

public class CombinedBoundedTracesAndStates_Synthetic_1ATPE {
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "Compute Prefix Alignments Incrementally", requiredParameterLabels = { 0})

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

		int[] lookBackWindowChoices = {1,2,3,4,5};     //here it represent the numOfFeatures
		int[] maxCasesToStoreChoices = {5,10,15,20,25};
		String[] logVariantChoices = {"a12f0timed", "a12f1timed", "a12f5timed", "a12f9timed"};
		String outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Synthetic data/WN/";

		for(int lv=0; lv<logVariantChoices.length; lv++) {
			String logVariant = logVariantChoices[lv];


			File inputFolder = new File("D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/a12/" + logVariant);
			LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();		

			for (File variant : inputFolder.listFiles()) {  //variant refers to a distinct noise value

				if (!variant.isDirectory()) {
					System.out.print("Variant:" + variant.getName());

					String variantName = FilenameUtils.getBaseName(variant.getName());
					String variantExtension = FilenameUtils.getExtension(variant.getName());
					if(!variantExtension.equals("xes") || !variantName.endsWith("0")) {

						System.out.println(variant.getName() + "\t error!! either not an xes file or not a variant file");
						continue;
					}

					String prefixVariant = variantName.substring(0, variantName.length()-2);
					//String suffixVariant = variantName.substring(variantName.length()-1, variantName.length());

					for(int i=0; i<lookBackWindowChoices.length; i++) {  //w size
						parameters.setLookBackWindow(lookBackWindowChoices[i]);

						for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size

							parameters.setMaxCasesToStore(maxCasesToStoreChoices[j]);						
							System.out.println("\t Feature Size: " + parameters.getLookBackWindow() + ", Max. Cases: " + parameters.getMaxCasesToStore());

							LinkedHashMap<String, ResultsCollection2> allInstancesResults = new LinkedHashMap<>();

							for(File instance: inputFolder.listFiles()){  //select only the relevant 10 timed versions of the variant
								String instanceName = FilenameUtils.getBaseName(instance.getName());
								String instanceExtension = FilenameUtils.getExtension(instance.getName());
								String prefixInstance = instanceName.substring(1, instanceName.length()-2);
								String suffixInstance = instanceName.substring(instanceName.length()-1, instanceName.length());

								if(prefixVariant.equals(prefixInstance)) {
									XLog instanceLog = null; 
									System.out.println("\t Instance:" + instance.getName());

									if(!instanceExtension.equals("xes")) {
										//continue;
										System.out.println("error!! not an xes file");
									}

									String instanceLogFile = inputFolder + "/" + instance.getName();

									try {
										instanceLog = new XUniversalParser().parse(new File(instanceLogFile)).iterator().next();
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									ResultsCollection2 resultsCollection2 = applyGeneric(net, initialMarking, finalMarking, instanceLog, parameters, parameters.getMaxCasesToStore()/*, classifierChoice, evaluation, parallelCases, fileName, offlineCCResults*/); 
									allInstancesResults.put(suffixInstance, resultsCollection2);
								}
							}
							//here we should aggregate the results of 10 variants and put it is another structure as one (f,n) column
							globalResults.put("(" + parameters.getLookBackWindow() + "/" + parameters.getMaxCasesToStore() + ")", aggregateVariantsResults(allInstancesResults));

						}
					}
					//here we publish the results for each noise value
					PublishResults.writeToFilesATPE(globalResults, prefixVariant, "", outputFolderPath);
				} else {
					continue;
				}
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 applyGeneric(
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, int maxCasesToStore ) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}

		Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
				.construct3a(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
						IncrementalReplayer.Strategy.REVERT_BASED);
		return processXLog(log, net, initialMarking, replayer, parameters.getLookBackWindow(), maxCasesToStore);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>>  ResultsCollection2 processXLog(
			XLog log, Petrinet net, Marking iMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer,
					int wLimit, int nLimit) throws IOException{

		ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);			

		final int runs = 50;
		Map<Integer, Double> elapsedTime = new HashMap<>();

		for(int i=0; i<=runs; i++) {                //i<runs+1 because we need to discard the first run.

			System.out.println("\nRun No. " + (i+1));
			System.out.println("Window, Time Elapsed in Millis, Observed Events,Avg. Time per Event");

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

				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event, /*window+1*/0);  //Prefix Alignment of the current observed event

				observedEvents++;
			}

			Instant end = Instant.now(); 
			Duration timeElapsed = Duration.between(start, end);
			elapsedTime.put(i, ((double)timeElapsed.toMillis()/(double)observedEvents));
		}


		double sumATPE = 0;
		for(int j=1; j<elapsedTime.size(); j++) {  //we discard the first run.
			sumATPE += elapsedTime.get(j);
		}

		ResultsCollection2 resultsCollection2 = new ResultsCollection2();

		resultsCollection2.ATPE = sumATPE/(elapsedTime.size()-1);
		resultsCollection2.caseLimitSize = nLimit;
		resultsCollection2.featureSize = wLimit;

		return resultsCollection2;
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

		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/fresh/WN/w="+  w + ",n=" + n + ".csv";
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

	private static void calculateStatesInWindows(HashMap<Integer, ArrayList<Integer>> universalStateRecords, String option, int wLimit, int nLimit) {
		System.out.println("\nW and N combination," + option + " states stored");
		//if(option.equals("Max")) {
		for(Entry<Integer, ArrayList<Integer>> rec : universalStateRecords.entrySet()) {
			System.out.println("(" + wLimit + "\\" + nLimit + ")," + Collections.max(rec.getValue()));

		}
		//}else if (option.equals("Mean")) {
		System.out.println("\nWindow, Mean states stored");
		for(Entry<Integer, ArrayList<Integer>> rec2 : universalStateRecords.entrySet()) {
			System.out.println(rec2.getKey() + "," + MeanMedianMode.findMean(rec2.getValue()));
		}
	}

	private static ResultsCollection2 aggregateVariantsResults(LinkedHashMap<String, ResultsCollection2> allInstancesResults) {

		ResultsCollection2 consolidatedResults = new ResultsCollection2();

		double sumATPE = 0;

		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
			sumATPE += entryOuter.getValue().ATPE;						
		}
		//	int avgSumOfForgottenPrematureCases=0;
		//	int avgOfEternalPrematureCases=0;
		//	
		//	for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
		//		avgSumOfForgottenPrematureCases += entryOuter.getValue().sumOfForgottenPrematureCases;
		//		avgOfEternalPrematureCases += entryOuter.getValue().sumOfEternalPrematureCases;
		//	}

		//	consolidatedResults.sumOfEternalPrematureCases = Math.round(avgOfEternalPrematureCases/allInstancesResults.size());
		//	consolidatedResults.sumOfForgottenPrematureCases = Math.round(avgSumOfForgottenPrematureCases/allInstancesResults.size());
		//	consolidatedResults.caseLimitSize = caseLimitSize;
		//	consolidatedResults.featureSize = featureSize;	
		consolidatedResults.ATPE = sumATPE/allInstancesResults.size();

		return consolidatedResults;
	}

	private static void publishResults(HashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice, String outputFolderPath) {

		StringBuilder stringBuilderATPE = new StringBuilder();

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getKey() + ",");
		}	

		stringBuilderATPE.append("\n");

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getValue().ATPE + ",");						
		}	

		String outputFilePath = outputFolderPath +  classifierChoice + "_" + fileName + "_ATPE.csv";
		File file = new File(outputFilePath);

		BufferedWriter bf = null;

		if(file.exists() && !file.isDirectory()){
			try {
				bf = new BufferedWriter(new FileWriter(file, true));
				bf.newLine();
				bf.write(stringBuilderATPE.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else {
			try {
				bf = new BufferedWriter(new FileWriter(file));
				bf.write(stringBuilderATPE.toString());

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
	}
}