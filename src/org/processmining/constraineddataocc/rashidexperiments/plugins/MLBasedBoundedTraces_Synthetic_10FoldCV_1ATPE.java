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
import org.processmining.constraineddataocc.helper.ClassifiersContainer;
import org.processmining.constraineddataocc.helper.ForgettingCases;
import org.processmining.constraineddataocc.helper.MeanMedianMode;
import org.processmining.constraineddataocc.helper.PublishResults;
import org.processmining.constraineddataocc.helper.ResultsCollection2;
import org.processmining.constraineddataocc.helper.TimeStampsBasedLogToStreamConverter;
import org.processmining.constraineddataocc.helper.WekaDataSetsCreation;
import org.processmining.constraineddataocc.helper.XLogHelper;
import org.processmining.constraineddataocc.models.IncrementalReplayResult;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.models.PartialAlignment.State;
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
import weka.classifiers.Classifier;
import weka.core.Instances;

//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = " A 05_2a Experiment 10FoldCV Compute Prefix Alignments Incrementally - With Marking Prediction (Synthetic ATPE)", parameterLabels = {"Model"/*, "Event Data"*/ }, 
returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class },
help = "Bounded TRACES, dynamic WINDOWS, COST plus EVENT CHAR. ANALYSIS.")

public class MLBasedBoundedTraces_Synthetic_10FoldCV_1ATPE {
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "0 Experiment 10FoldCV Compute Prefix Alignments Incrementally - With Marking Prediction", requiredParameterLabels = { 0/*, 1*/})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final UIPluginContext context, final Petrinet net/*, XLog log*/) throws IOException {

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
		parameters.setLookBackWindow(Integer.MAX_VALUE);

		//-----------------------------------------------------parameters to set

		Boolean endMarkerEnabled = false;
		
		int[] featureSizes = {1,2,3/*,4,5*/};
		int[] maxCasesToStoreChoices = {5,10,15,20,25,50};
		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		String[] logVariantChoices = {"a12f0timed", "a12f1timed", "a12f5timed", "a12f9timed"};
		String[] evaluationChoices = {"10foldCV", "70train-30test", "offline"};

		String classifierChoice = classifierChoices[0];
		String evaluation = evaluationChoices[0];	//For synthetic data String evaluation = "10foldCV";
		String outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Synthetic data/FN/";

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

					//HashMap<Integer, HashMap<String, Double>> offlineCCResults = OfflineAnalysisForStatePrediction.apply(context, net, variantLog, 0);

					String prefixVariant = variantName.substring(0, variantName.length()-2);
					//String suffixVariant = variantName.substring(variantName.length()-1, variantName.length());

					for(int i=0; i<featureSizes.length; i++) {
						int numOfFeatures = featureSizes[i];

						for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size
							int maxCasesToStore = maxCasesToStoreChoices[j];

							System.out.println("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore);

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
									

									ClassifiersContainer classifierStorage = new ClassifiersContainer();
									classifierStorage.storageFolder = outputFolderPath + "classifiers/";
									classifierStorage.endMarkerEnabled = endMarkerEnabled;

									ResultsCollection2 resultsCollection2 = applyGeneric(context, net, initialMarking, finalMarking, instanceLog, parameters, maxCasesToStore, classifierChoice, 
											evaluation, numOfFeatures, classifierStorage /*,parallelCases, fileName,offlineCCResults*/ ); 
									allInstancesResults.put(suffixInstance, resultsCollection2);
								}
							}
							//here we should aggregate the results of 10 variants and put it is another structure as one (f,n) column
							globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + ")", aggregateVariantsResults(allInstancesResults));
						}
					}
					//here we need to publish the results for each a complete noise value
					PublishResults.writeToFilesATPE(globalResults, prefixVariant, classifierChoice, outputFolderPath);
				} else {
					continue;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 applyGeneric(final UIPluginContext context,
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, 
			int maxCasesToStore, String classifierChoice, String evaluation, int numOfFeatures, ClassifiersContainer classifierStorage /*,int parallelCases, String fileName, HashMap<Integer, HashMap<String, Double>> offlineCCResults*/) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}

		Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
				.construct4(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
						IncrementalReplayer.Strategy.REVERT_BASED);
		return processXLog(context, log, net, initialMarking, finalMarking, replayer, numOfFeatures, maxCasesToStore, classifierChoice, evaluation, classifierStorage /*,parallelCases, fileName, offlineCCResults*/);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 processXLog(final UIPluginContext context,
			XLog log, Petrinet net, Marking iMarking, final Marking finalMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer, int numOfFeatures,
					int maxCasesToStore, String classifierChoice, String evaluation, ClassifiersContainer classifierStorage /*,int parallelCases, String fileName, HashMap<Integer, HashMap<String, Double>> offlineCCResults*/) throws IOException{

		ArrayList<String> caseStarterEvents = getCaseStarterEvents(net);


		if(evaluation.equals("10foldCV")) {

			int numOfFolds = 10;
			int bracket = (int) Math.ceil((double)log.size() / numOfFolds);
			int startIndex = -1;
			int endIndex = -1;
			int[] startIndexArray = new int[numOfFolds];
			int[] endIndexArray = new int[numOfFolds];

			//Do we need to randomize the traces in the log? If yes, then How?? :)

			for(int i=1; i<=numOfFolds; i++) {
				startIndex = endIndex+1;
				startIndexArray[i-1] = startIndex;
				if(i<numOfFolds) {
					endIndex = (bracket*i)-1;
				}else {
					endIndex = log.size()-1;
				}				
				endIndexArray[i-1] = endIndex;				
			}

			Map<Integer, Double> allFoldsATPE = new HashMap<>();


			for(int k=0; k<numOfFolds; k++) {

				replayer.getDataStore().clear();/////?????????????????????????????

				XLog trainLog = XLogHelper.generateNewXLog("traininglog");
				XLog testLog = XLogHelper.generateNewXLog("testlog");

				for(int x=0; x<numOfFolds; x++) {
					if(x==k) {
						for(int index = startIndexArray[x]; index<=endIndexArray[x]; index++) {
							testLog.add(log.get(index));
						}
						//System.out.println("Test log for fold " + k + " :" + startIndexArray[x] + "," + endIndexArray[x]);
					}else {
						for(int index = startIndexArray[x]; index<=endIndexArray[x]; index++) {
							trainLog.add(log.get(index));
						}
						//System.out.println("Training log for fold " + k + " :" + startIndexArray[x] + "," + endIndexArray[x]);
					}
				}

				//System.out.println(trainLog.size() + "," + testLog.size());

				WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
				Classifier classifier = wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifierStorage.endMarkerEnabled, null,
						wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking));

				//ArrayList<Triplet<String,String,Date>>	eventStream = ParallelCasesBasedLogToStreamConverter.logToStream(testLog, parallelCases, numOfFeatures, numOfFeatures+2);
				ArrayList<Triplet<String,String,Date>>	eventStream = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(testLog);

				final int runs = 50;
				Map<Integer, Double> ATPEFoldRuns = new HashMap<>();

				for(int runid=0; runid<=runs; runid++) {                //i<runs+1 because we need to discard the first run.

					System.out.println("\nRun No. " + (runid+1));
					System.out.println("Window, Time Elapsed in Millis, Observed Events,Avg. Time per Event");

					ArrayList<Triplet<String,String,Date>>	eventStreamCopy = new ArrayList<>();

					for (Triplet<String,String, Date> entry : eventStream) {  //creates a clone of the event log with distinct case ids to stress memo
						eventStreamCopy.add(new Triplet<String, String, Date>(entry.getValue0()+runid, entry.getValue1(), entry.getValue2()));
					}
					System.gc();
					Instant start = Instant.now();

					int observedEvents = 0;					

					for (Triplet<String,String, Date> entry : eventStreamCopy) {

						String caseId = entry.getValue0();
						String event = entry.getValue1();


						PartialAlignment<String, Transition, Marking> partialAlignment = null;

						if(replayer.getDataStore().containsKey(caseId)) {

							if(replayer.getDataStore().get(caseId).getState().getStateInModel()==null){

								if(replayer.getDataStore().get(caseId).size()<(numOfFeatures-1)) { //if the partial alignment size is less than numoffeatures-1 and last state marking == null then append a log-move with null marking to the current prefix alignment
									State parent = replayer.getDataStore().get(caseId).getState();							
									partialAlignment = synthesizePartialAlignment(parent, event);
									replayer.getDataStore().put(caseId, (A)partialAlignment);
								}else if(replayer.getDataStore().get(caseId).size()==(numOfFeatures-1)){
									//else if partial alignment size == numoffeatures-1 and last state marking == null then predict a marking and prepend this marking as a starting state to the current prefix alignment so that the 
									//computation of the prefix-alignment through shortest path is done from the predicted marking

									ArrayList<State> statesArray = new ArrayList<>();
									ArrayList<String> orphanEvents = new ArrayList<>();	
									orphanEvents.add(event);

									State state = replayer.getDataStore().get(caseId).getState();
									statesArray.add(PartialAlignment.State.Factory.construct(replayer.getFinalStateInModel(), 0, state.getParentState(), state.getParentMove())); //we manipulate the marking here to void Null error from Model-Semantics
									orphanEvents.add((String) state.getParentMove().getEventLabel());
									while (state.getParentState() != null) { 
										state = state.getParentState();
										orphanEvents.add((String) state.getParentMove().getEventLabel());
										statesArray.add(state);
									}

									Collections.reverse(orphanEvents);

									Instances testInstance = wekaDataSetsCreation.synthesizeTestSet(orphanEvents);
									Marking predictedMarking = null;
									try {
										double classValue = classifier.classifyInstance(testInstance.firstInstance());
										predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));

									//double residualCosts =  predict cost as well
									//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
									statesArray.add(dummyStartState);

									for(int index=statesArray.size()-2; index>=0;index--) {
										statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
												statesArray.get(index+1), statesArray.get(index).getParentMove()));
									}

									replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
									replayer.processEvent(caseId, event);						
								}

							}else {
								//the observed event is not orphan and therefore normally compute prefix alignment
								partialAlignment = replayer.processEvent(caseId, event);
							}						

						}else{  //case not present in the datastore

							if(replayer.getDataStore().size()>=maxCasesToStore) {
								String caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore());

								replayer.getDataStore().remove(caseToBeForgotten);  //remove a case on the basis of the forgetting criteria
							}						

							if(caseStarterEvents.contains(event)) { //the observed event is a case-starting event for a fresh case and therefore normally compute prefix alignment
								partialAlignment = replayer.processEvent(caseId, event);
							}else {

								if(numOfFeatures==1) {

									//ArrayList<State> statesArray = new ArrayList<>();
									ArrayList<String> orphanEvents = new ArrayList<>();	
									orphanEvents.add(event);

									Instances testInstance = wekaDataSetsCreation.synthesizeTestSet(orphanEvents);
									Marking predictedMarking = null;
									try {
										double classValue = classifier.classifyInstance(testInstance.firstInstance());
										predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));

									//double residualCosts =  predict cost as well
									//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));


									replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(dummyStartState)); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
									replayer.processEvent(caseId, event);		

								}else {
									//synthesize a partial alignment with a state which has log-move of the observed event, NULL reached marking, and NULL parent. STORE this prefix-alignment in the datastore.
									partialAlignment = synthesizePartialAlignment(null, event);
									replayer.getDataStore().put(caseId, (A)partialAlignment);
								}

							}
						}
						observedEvents++;

					}

					Instant end = Instant.now();  

					Duration timeElapsed = Duration.between(start, end);

					ATPEFoldRuns.put(runid, ((double)timeElapsed.toMillis()/(double)observedEvents));

					System.out.println(runid + "," + timeElapsed.toMillis() + "," + observedEvents + "," + ((double)timeElapsed.toMillis()/(double)observedEvents));						
				}



				double sumATPERuns = 0;

				for(int j=1; j<ATPEFoldRuns.size(); j++) {  //we discard the first run.
					sumATPERuns += ATPEFoldRuns.get(j);
				}

				allFoldsATPE.put(k, sumATPERuns/(runs-1));			


			}

			double sumATPEFolds = 0;

			for(int f=0; f<numOfFolds; f++) {
				sumATPEFolds += allFoldsATPE.get(f);
			}


			ResultsCollection2 resultsCollection2 = new ResultsCollection2();
			resultsCollection2.caseLimitSize = maxCasesToStore;
			resultsCollection2.featureSize = numOfFeatures;

			resultsCollection2.ATPE = sumATPEFolds/numOfFolds;			

			return resultsCollection2;

		}else if(evaluation.equals("70train-30test")){
			//ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);		
			return null;			
		}else if(evaluation.equals("offline")){
			return null;
		}else {
			System.out.println("Unknown Evaluation Choice :(");
			return null;
		}
	}



	////////////////these data structures for conformance statistics still needs to be decided.............



	//					if(alignmentsWindowsStates.containsKey(caseId)) {
	//						alignmentsWindowsStates.get(caseId).add(StatesCalculator.getNumberOfStates(partialAlignment));
	//					}else {
	//						ArrayList<Integer> tempStates = new ArrayList<>();
	//						tempStates.add(StatesCalculator.getNumberOfStates(partialAlignment));
	//						alignmentsWindowsStates.put(caseId, tempStates);
	//					}
	//
	//					if(alignmentsLife.containsKey(caseId)) {
	//						alignmentsLife.get(caseId).add(partialAlignment);
	//					}else {
	//						ArrayList<PartialAlignment> temp = new ArrayList<>();
	//						temp.add(partialAlignment);
	//						alignmentsLife.put(caseId, temp);
	//					}
	//
	//					java.util.Iterator<Triplet<Integer, String, Double>> iterator = universalCostRecords.iterator();			//recording fitness costs
	//					while(iterator.hasNext()) {
	//						Triplet<Integer, String, Double> temp = iterator.next();
	//						if(temp.getValue0()==(window+1) && temp.getValue1().equals(caseId)) {
	//							iterator.remove();
	//							break;
	//						}
	//					}			
	//					universalCostRecords.add(new Triplet<Integer, String, Double>(window+1, caseId, partialAlignment.getCost()));
	//
	//					//recording states			
	//					if(universalStateRecords.containsKey(window+1)) {
	//						universalStateRecords.get(window+1).add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore())); 
	//					}else {
	//						ArrayList<Integer> tempStates = new ArrayList<>();
	//						tempStates.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));
	//						universalStateRecords.put(window+1, tempStates);
	//					}
	//
	//					observedEvents++;	

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

	public static double sumListOfArrayLists(HashMap<String,ArrayList<Double>> map) {
		Double sum = 0.0;
		for(Entry<String,ArrayList<Double>> entry : map.entrySet()) {
			sum += sumArrayList(entry.getValue());
		}
		return sum;
	}

	public static double sumHashMap(HashMap<String,Double> map) {
		Double sum = 0.0;
		for(Entry<String,Double> entry : map.entrySet()) {
			sum += entry.getValue();
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

	private static ArrayList<String> getCaseStarterEvents(final Petrinet net){

		ArrayList<String> temp = new ArrayList<>();

		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
			if ((tran.getVisiblePredecessors()).isEmpty()){
				temp.add(tran.toString());
			}
		}
		return temp;
	}

	private static <L, T, S> PartialAlignment<L, T, S> synthesizePartialAlignment(State<S, L, T> parentState, String event){

		State<S, L, T> dummyState = PartialAlignment.State.Factory.construct(null, 0, parentState, Move.Factory.construct((L)event, null , 1.0));
		PartialAlignment<L, T, S> alignment = PartialAlignment.Factory.construct(dummyState);

		return alignment;
	}

	private static <L, T, S> void recordCaseCosts(int fold, String caseId, HashMap<Integer, LinkedHashMap<String,Double>> allFoldsResults, PartialAlignment<L, T, S> partialAlignment) {
		if(allFoldsResults.get(fold).containsKey(caseId)) {
			double oldCostValue = allFoldsResults.get(fold).get(caseId);
			allFoldsResults.get(fold).put(caseId, oldCostValue + partialAlignment.getCost());
		}else {					
			allFoldsResults.get(fold).put(caseId, partialAlignment.getCost());			
		}
	}

//	private static void publishResults(HashMap<String, ResultsCollection> allInstancesResults, String fileName, String classifierChoice, int featuresize, int maxCases) {
//
//		Double sumOfOnlineCosts = 0.0;
//		Double sumOfOfflineCosts = 0.0;
//		int misMatchingCases = 0;
//		int matchingCases = 0;
//		int sumOfForgottenPrematureCases = 0;
//		int sumOfEternalPrematureCases = 0;
//		int conformantAsConformant = 0;
//		int conformantAsNonConformant = 0;
//		int nonConformantAsConformant = 0;
//		int nonConformantAsNonConformantExactlyEstimated = 0;
//		int nonConformantAsNonConformantUnderEstimated = 0;
//		int nonConformantAsNonConformantOverEstimated = 0;
//
//		StringBuilder stringBuilderHeader = new StringBuilder();
//		stringBuilderHeader.append("Log, Classifier, Features, Max. Cases,, Online Costs, Offline Costs, Matching Cases, Mis-Matching Cases, For. Premature Cases, Eter. Premature Cases, Con. as Con., Con. as NCon., NCon. as Con., NC as NC & ES, NC as NC & OS, NC as NC & UE ");
//		StringBuilder stringBuilder = new StringBuilder();
//
//		for(Entry<String, ResultsCollection> entry : allInstancesResults.entrySet()) {
//			sumOfOnlineCosts += entry.getValue().sumOfOnlineCosts;
//			stringBuilder.append(entry.getValue().sumOfOnlineCosts + ",");
//
//			sumOfOfflineCosts += entry.getValue().sumOfOfflineCosts;
//			stringBuilder.append(entry.getValue().sumOfOfflineCosts + ",");
//
//			matchingCases += entry.getValue().matchingCases;
//			stringBuilder.append(entry.getValue().matchingCases + ",");
//
//			misMatchingCases += entry.getValue().misMatchingCases;
//			stringBuilder.append(entry.getValue().misMatchingCases + ",");
//
//			sumOfForgottenPrematureCases += entry.getValue().sumOfForgottenPrematureCases;
//			stringBuilder.append(entry.getValue().sumOfForgottenPrematureCases + ",");
//
//			sumOfEternalPrematureCases += entry.getValue().sumOfEternalPrematureCases;
//			stringBuilder.append(entry.getValue().sumOfEternalPrematureCases + ",");
//
//			conformantAsConformant += entry.getValue().conformantAsConformant;
//			stringBuilder.append(entry.getValue().conformantAsConformant + ",");
//
//			conformantAsNonConformant += entry.getValue().conformantAsNonConformant;
//			stringBuilder.append(entry.getValue().conformantAsNonConformant + ",");
//
//			nonConformantAsConformant += entry.getValue().nonConformantAsConformant;
//			stringBuilder.append(entry.getValue().nonConformantAsConformant + ",");
//
//			nonConformantAsNonConformantExactlyEstimated += entry.getValue().nonConformantAsNonConformantExactlyEstimated;
//			stringBuilder.append(entry.getValue().nonConformantAsNonConformantExactlyEstimated + ",");
//
//			nonConformantAsNonConformantOverEstimated += entry.getValue().nonConformantAsNonConformantOverEstimated;
//			stringBuilder.append(entry.getValue().nonConformantAsNonConformantOverEstimated);
//
//			nonConformantAsNonConformantUnderEstimated += entry.getValue().nonConformantAsNonConformantUnderEstimated;
//			stringBuilder.append(entry.getValue().nonConformantAsNonConformantUnderEstimated + ",");
//
//
//
//			stringBuilder.append(",,");
//
//		}
//
//
//		StringBuilder stringBuilder0 = new StringBuilder();
//		stringBuilder0.append(fileName + "," + classifierChoice + "," + featuresize + "," + maxCases + ",,");
//		stringBuilder0.append(Math.round(sumOfOnlineCosts/allInstancesResults.size())
//				+ "," + Math.round(sumOfOfflineCosts/allInstancesResults.size()) 
//				+ "," + (int)Math.round((double)matchingCases/allInstancesResults.size()) 
//				+ "," + Math.round((double)misMatchingCases/allInstancesResults.size())
//				+ "," + Math.round((double)sumOfForgottenPrematureCases/allInstancesResults.size()) 
//				+ "," +	Math.round((double)sumOfEternalPrematureCases/allInstancesResults.size())
//				+ "," + Math.round((double)conformantAsConformant/allInstancesResults.size())
//				+ "," + Math.round((double)conformantAsNonConformant/allInstancesResults.size()) 
//				+ "," + Math.round((double)nonConformantAsConformant/allInstancesResults.size()) 
//				+ "," + Math.round((double)nonConformantAsNonConformantExactlyEstimated/allInstancesResults.size()/allInstancesResults.size())
//				+ "," + Math.round((double)nonConformantAsNonConformantOverEstimated/allInstancesResults.size())
//				+ "," + Math.round((double)nonConformantAsNonConformantUnderEstimated/allInstancesResults.size()) + ",,");
//
//
//		/*System.out.println(fileName + "," + classifierChoice + "," + maxCasesToStore + "," + numOfFeatures + "," + (int)Math.round(sumOfOfflineCosts/numOfFolds) + "," + (int)Math.round(sumOfOnlineCosts/numOfFolds) + "," 
//				+ Math.round((double)matchingCases/(double)numOfFolds) + "," + Math.round((double)misMatchingCases/(double)numOfFolds) + "," + (int)Math.round((double)sumOfForgottenPrematureCases/(double)numOfFolds)
//				+ "," + (int)Math.round((double)sumOfEternalPrematureCases/(double)numOfFolds) + "," + (int)Math.round((double)conformantAsConformant/(double)numOfFolds) + "," 
//				+ (int)Math.round((double)conformantAsNonConformant/(double)numOfFolds) + "," + (int)Math.round((double)nonConformantAsConformant/(double)numOfFolds) + "," 
//				+ (int)Math.round((double)nonConformantAsNonConformantExactlyEstimated/(double)numOfFolds) + "," + (int)Math.round((double)nonConformantAsNonConformantUnderEstimated/(double)numOfFolds) + ","
//				+ (int)Math.round((double)nonConformantAsNonConformantOverEstimated/(double)numOfFolds));*/
//
//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/in OCC Random Forest/" +  fileName + ".csv";
//		File file = new File(outputFilePath);
//
//		BufferedWriter bf = null;
//
//		if(file.exists() && !file.isDirectory()){
//			try {
//				bf = new BufferedWriter(new FileWriter(file, true));
//				bf.newLine();
//				bf.write(stringBuilder0.toString());
//				bf.write(stringBuilder.toString());
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}else {
//			try {
//				bf = new BufferedWriter(new FileWriter(file));
//				bf.write(stringBuilderHeader.toString());
//				bf.newLine();
//				bf.write(stringBuilder0.toString());
//				bf.write(stringBuilder.toString());
//
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//
//		try {
//			//bf.newLine();
//			/*bf.write(fileName + "," + classifierChoice + "," + maxCasesToStore + "," + numOfFeatures + "," + (int)Math.round(sumOfOfflineCosts/numOfFolds) + "," + (int)Math.round(sumOfOnlineCosts/numOfFolds) + "," 
//					+ Math.round((double)matchingCases/(double)numOfFolds) + "," + Math.round((double)misMatchingCases/(double)numOfFolds) + "," + (int)Math.round((double)sumOfForgottenPrematureCases/(double)numOfFolds)
//					+ "," + (int)Math.round((double)sumOfEternalPrematureCases/(double)numOfFolds) + "," + (int)Math.round((double)conformantAsConformant/(double)numOfFolds) + "," 
//					+ (int)Math.round((double)conformantAsNonConformant/(double)numOfFolds) + "," + (int)Math.round((double)nonConformantAsConformant/(double)numOfFolds) + "," 
//					+ (int)Math.round((double)nonConformantAsNonConformantExactlyEstimated/(double)numOfFolds) + "," + (int)Math.round((double)nonConformantAsNonConformantUnderEstimated/(double)numOfFolds) + ","
//					+ (int)Math.round((double)nonConformantAsNonConformantOverEstimated/(double)numOfFolds));*/
//			//bf.write(stringBuilder.toString());
//			bf.flush();
//			bf.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	private static void publishResults(LinkedHashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice) {
//
//		//		StringBuilder stringBuilderHeader = new StringBuilder();
//		//		stringBuilderHeader.append("Case,");
//		//		
//		//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
//		//			stringBuilderHeader.append(entryOuter.getKey() + ",");
//		//		}		
//		//		
//		//		//stringBuilderHeader.append("For. Premature Cases, Eter. Premature Cases");
//		//		//stringBuilderHeader.append("\n");
//		//		
//		//		StringBuilder stringBuilderCosts = new StringBuilder();
//		//		StringBuilder stringBuilderCasesMeta = new StringBuilder();
//		//		
//		//		boolean first = true;
//
//		System.out.println("W and N combination, ATPE");
//
//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
//			System.out.println(entryOuter.getKey() + "," + entryOuter.getValue().ATPE);			
//		}	
//
//		//		System.out.println("Combination, Forgotten Premature, Forgotten Eternal");
//		//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
//		//			stringBuilderCasesMeta.append(entryOuter.getKey() + ",");
//		//			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
//		//			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfEternalPrematureCases + "\n");
//		//		}
//		//
//		//		System.out.println(stringBuilderCasesMeta.toString());
//		//		
//		//		
//		//		
//		//
//		//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/in OCC Random Forest/synthetic data/" +  classifierChoice + fileName + ".csv";
//		//		File file = new File(outputFilePath);
//		//
//		//		BufferedWriter bf = null;
//		//
//		//		if(file.exists() && !file.isDirectory()){
//		//			try {
//		//				bf = new BufferedWriter(new FileWriter(file, true));
//		//				bf.newLine();
//		//				bf.write(stringBuilderHeader.toString());
//		//				bf.write(stringBuilderCosts.toString());
//		//			} catch (IOException e) {
//		//				// TODO Auto-generated catch block
//		//				e.printStackTrace();
//		//			}
//		//		}else {
//		//			try {
//		//				bf = new BufferedWriter(new FileWriter(file));
//		//				bf.write(stringBuilderHeader.toString());
//		//				bf.newLine();
//		//				bf.write(stringBuilderCosts.toString());
//		//				
//		//			} catch (IOException e) {
//		//				// TODO Auto-generated catch block
//		//				e.printStackTrace();
//		//			}
//		//		}
//		//
//		//		try {			
//		//			bf.flush();
//		//			bf.close();
//		//		} catch (IOException e) {
//		//			// TODO Auto-generated catch block
//		//			e.printStackTrace();
//		//		}
//		//		
//		//		outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/in OCC Random Forest/synthetic data/" +  classifierChoice + fileName + "_meta.csv";
//		//		file = new File(outputFilePath);
//		//		BufferedWriter bf2 = null;
//		//		try {	
//		//			bf2 = new BufferedWriter(new FileWriter(file, true));
//		//			bf2.write(stringBuilderCasesMeta.toString());
//		//			bf2.flush();
//		//			bf2.close();
//		//		} catch (IOException e) {
//		//			// TODO Auto-generated catch block
//		//			e.printStackTrace();
//		//		}
//	}

	private static ResultsCollection2 aggregateVariantsResults(LinkedHashMap<String, ResultsCollection2> allInstancesResults) {

		ResultsCollection2 consolidatedResults = new ResultsCollection2();

		double sumATPE = 0;

		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
			sumATPE += entryOuter.getValue().ATPE;						
		}
		//		int avgSumOfForgottenPrematureCases=0;
		//		int avgOfEternalPrematureCases=0;
		//		
		//		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
		//			avgSumOfForgottenPrematureCases += entryOuter.getValue().sumOfForgottenPrematureCases;
		//			avgOfEternalPrematureCases += entryOuter.getValue().sumOfEternalPrematureCases;
		//		}

		//		consolidatedResults.sumOfEternalPrematureCases = Math.round(avgOfEternalPrematureCases/allInstancesResults.size());
		//		consolidatedResults.sumOfForgottenPrematureCases = Math.round(avgSumOfForgottenPrematureCases/allInstancesResults.size());
		//		consolidatedResults.caseLimitSize = caseLimitSize;
		//		consolidatedResults.featureSize = featureSize;	
		consolidatedResults.ATPE = sumATPE/allInstancesResults.size();

		return consolidatedResults;
	}


}
