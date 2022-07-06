package org.processmining.constraineddataocc.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

import org.apache.commons.io.FilenameUtils;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;
import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.helper.ClassifiersContainer;
import org.processmining.constraineddataocc.helper.ForgettingCases;
import org.processmining.constraineddataocc.helper.PublishResults;
import org.processmining.constraineddataocc.helper.ResultsCollection2;
import org.processmining.constraineddataocc.helper.StatesCalculator;
import org.processmining.constraineddataocc.helper.TimeStampsBasedLogToStreamConverter;
import org.processmining.constraineddataocc.helper.TimestampingEventData;
import org.processmining.constraineddataocc.helper.TimestampsSynchronization;
import org.processmining.constraineddataocc.helper.WekaDataSetsCreation;
import org.processmining.constraineddataocc.helper.XLogHelper;
import org.processmining.constraineddataocc.models.IncrementalReplayResult;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.models.PartialAlignment.State;
import org.processmining.constraineddataocc.parameters.IncrementalReplayerParametersImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.importing.PnmlImportUtils;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import weka.core.Instances;


//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = "05_2 Compute Prefix Alignments Incrementally With Marking Prediction - 10Fold CV", parameterLabels = {"Model", "Event Data", "Path to Model", "Path to Log", "Path to Marking" }, 
returnLabels = { "Petri net" }, returnTypes = { Petrinet.class },
help = "Conformance checking for unlimited cases but storing only a limited number of cases in memory.")

public class MLBasedBoundedTraces_10FoldCV_0CC {
	
	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {0,1})

	public Petrinet apply(
			final PluginContext context, final Petrinet net, XLog log) throws Exception {
		String fileName = "BPIC12";
		
		
		String outputFolderPath = "D:/Experiments/Results/FN/";
		
		apply(context, net, log, null, fileName, "", outputFolderPath);
		return net;
	}
	
	
	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {0})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final PluginContext context, final Petrinet net/*, XLog log*/) throws IOException {
		

		String[] logTypes = {"a12", "a22", "a32"};
		String logType = logTypes[1];
		String logsInputFolderPath = "D:/Experiments/Event Logs/" + logType + "/";
		String outputFolderPath = "D:/Experiments/Results/FN/";
		File inputFolder = new File(logsInputFolderPath);

		for (File file : inputFolder.listFiles()) { 

			System.out.println(file.getName());

			String fileName = FilenameUtils.getBaseName(file.getName());
			String fileExtension = FilenameUtils.getExtension(file.getName());

			XLog log = null; 

			if(!fileExtension.equals("xes")) {
				//continue;
				System.out.println("error!! not an xes file");
				continue;
			}

			try {
				log = new XUniversalParser().parse(file).iterator().next();
			} catch (Exception e) {
				e.printStackTrace();
			}			
			
			apply(context, net, log, null, fileName, "", outputFolderPath);
		}		
		return null;
	}

	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {2,3,4})

	public Petrinet apply(
			final PluginContext context, String modelPath, String logPath, String markingPath) throws Exception {
		Petrinet net = context.tryToFindOrConstructFirstNamedObject(Petrinet.class, "Import Petri net from PNML file", null, null, modelPath);
		//		Petrinet net = constructNet(context, modelPath);
		XLog log = new XUniversalParser().parse(new File(logPath)).iterator().next();
		ArrayList<String> markingClasses = populateMarkingClasses(markingPath);
		String[] values = logPath.split("/");
		String fileName = values[values.length-1].substring(0, values[values.length-1].length()-4);
		
		String outputFolderPath = "D:/Experiments/Results/FN/";
		
		apply(context, net, log, markingClasses, fileName, modelPath, outputFolderPath);
		return net;
	}
	
	public Petrinet apply(
			final PluginContext context, String modelPath, String logPath, String markingPath, String outputFolderPath) throws Exception {
		Petrinet net = context.tryToFindOrConstructFirstNamedObject(Petrinet.class, "Import Petri net from PNML file", null, null, modelPath);
		//		Petrinet net = constructNet(context, modelPath);
		XLog log = new XUniversalParser().parse(new File(logPath)).iterator().next();
		ArrayList<String> markingClasses = populateMarkingClasses(markingPath);
		String[] values = logPath.split("/");
		String fileName = values[values.length-1].substring(0, values[values.length-1].length()-4);
		apply(context, net, log, markingClasses, fileName, modelPath, outputFolderPath);
		return net;
	}

	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = { })

	public Petrinet apply(final PluginContext context) throws Exception {
		String model = "D:/Experiments/Input Models/a22.pnml";
		String marking = "D:/Experiments/Input Models/a22.txt";
		
		String logsInputFolderPath = "D:/Experiments/Event Logs/a22/";

		File inputFolder = new File(logsInputFolderPath);
		
		String outputFolderPath = "D:/Experiments/Results/FN/";

		for (File file : inputFolder.listFiles()) { 

			System.out.println(file.getName());

			String fileName = FilenameUtils.getBaseName(file.getName());
			String fileExtension = FilenameUtils.getExtension(file.getName());

			if(!fileExtension.equals("xes") /*|| !(fileName.endsWith("n05_50_10") || fileName.endsWith("50_5") || fileName.endsWith("50_10"))*/) {
				//continue;
				System.out.println("error!! not an xes file");
				continue;
			}
			
			apply(context, model, logsInputFolderPath + "/" + file.getName(), marking, outputFolderPath);
			//count++;
		}
		
		return null;
		
	}

	public void apply(final PluginContext context, final Petrinet net, XLog log, ArrayList<String> markingClasses, String fileName, String modelPath, String outputFolderPath) throws IOException {

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
		parameters.setLookBackWindow(Integer.MIN_VALUE);

		//-----------------------------------------------------parameters to set

		int numOfFolds = 10;
		//Boolean timeStampLogs = false;
		Boolean endMarkerEnabled = true;
		String[] forgettingPolicies = {"shortest-non-conf", "longest non-conf","enriched"};
		String[] StoreTypes = {"HashMap", "LinkedHashmap"};
		String forgettingPolicy = forgettingPolicies[2];
		String StoreType = StoreTypes[0];

		int[] featureSizes = {1,2,3,4,5};
		int[] maxCasesToStoreChoices = {5,10,15,20,25,50/*,60,70,80*/};

		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		String[] evaluationChoices = {"10foldCV", "70train-30test", "offline"};

		String classifierChoice = classifierChoices[0];
		String evaluation = evaluationChoices[0];	//For synthetic data String evaluation = "10foldCV";


		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();


		for(int i=0; i<featureSizes.length; i++) {  //features size
			int numOfFeatures = featureSizes[i];

			
			int bracket = (int) Math.ceil((double)log.size() / numOfFolds);
			int startIndex = -1;
			int endIndex = -1;
			int[] startIndexArray = new int[numOfFolds];
			int[] endIndexArray = new int[numOfFolds];

			//Do we need to randomize the traces in the log? If yes, then How?? :)

			for(int l=1; l<=numOfFolds; l++) {
				startIndex = endIndex+1;
				startIndexArray[l-1] = startIndex;
				if(l<numOfFolds) {
					endIndex = (bracket*l)-1;
				}else {
					endIndex = log.size()-1;
				}				
				endIndexArray[l-1] = endIndex;				
			}

			for(int k=0; k<numOfFolds; k++) {

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
				
//				if(numOfFeatures == 1) {
//					System.out.println("check");
//				}

				WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
				wekaDataSetsCreation.trainClassifier(context, trainLog, net, initialMarking, finalMarking, numOfFeatures, endMarkerEnabled, markingClasses);

				for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size
					int maxCasesToStore = maxCasesToStoreChoices[j];

					System.out.println("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore + ", Fold: " + k);
					
					ClassifiersContainer classifiersContainer = new ClassifiersContainer();
					classifiersContainer.storageFolder = outputFolderPath + "classifiers/";
					classifiersContainer.foldsclassifiers.add(wekaDataSetsCreation);
					//classifiersContainer.classifiersRepository = classifiersRepository;
					classifiersContainer.endMarkerEnabled = endMarkerEnabled;
					classifiersContainer.forgettingPolicy = forgettingPolicy;
					classifiersContainer.StoreType = StoreType;
					classifiersContainer.fold = k;
					classifiersContainer.fileName =fileName;
					//classifiersContainer.classifiersRepositoryPath = classifiersRepositoryPath;
					System.out.println("parameters, nonConformantCases, cummulativeCosts");

					LinkedHashMap<Integer, ResultsCollection2> allFoldsResults = applyGeneric(context, net, initialMarking, finalMarking, testLog, parameters, maxCasesToStore, classifierChoice, evaluation, numOfFeatures, classifiersContainer); 
					globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + "/" + k + ")", allFoldsResults.get(0));
				}
				wekaDataSetsCreation = null;
				System.gc();
			}
		}

		//before publishing we need to aggregate the folds results for each f and n value
		LinkedHashMap<String, ResultsCollection2> consolidatedResults = new LinkedHashMap<>();
		consolidatedResults = aggregateFoldsResults(globalResults, featureSizes, maxCasesToStoreChoices, numOfFolds);
		PublishResults.writeToFilesCC_(consolidatedResults, fileName, classifierChoice, outputFolderPath);		
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> LinkedHashMap<Integer, ResultsCollection2> applyGeneric(final PluginContext context,
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog testLog, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, 
			int maxCasesToStore, String classifierChoice, String evaluation, int numOfFeatures, ClassifiersContainer classifiersContainer) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}

		Map<String, PartialAlignment<String, Transition, Marking>> store = null ;

		if(classifiersContainer.StoreType.equals("HashMap")) {
			store = new HashMap<>();
		}else if(classifiersContainer.StoreType.equals("LinkedHashmap")) {
			store = new LinkedHashMap<>();
		}else {
			System.out.println("Wrong store choice");
		}


		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
				.construct4(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
						IncrementalReplayer.Strategy.REVERT_BASED);
		return processXLog(context, testLog, net, initialMarking, finalMarking, replayer, numOfFeatures, maxCasesToStore, classifierChoice, evaluation, classifiersContainer);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> LinkedHashMap<Integer, ResultsCollection2>  processXLog(final PluginContext context,
			XLog testLog, Petrinet net, Marking iMarking, final Marking finalMarking, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer, int numOfFeatures,
					int maxCasesToStore, String classifierChoice, String evaluation, ClassifiersContainer classifiersContainer){


		HashSet<String> caseStarterEvents = getCaseStarterEvents(net);
		HashSet<String> caseEndingEvents = getCaseEndingEvents(net);

		if(evaluation.equals("10foldCV")) {


			//HERE WE DEFINE OUR DATA STRUCTURES FOR single FOLD
			LinkedHashMap<Integer, ResultsCollection2> allFoldsCCResults = new LinkedHashMap<>();
			ArrayList<Integer> foldStates = new ArrayList<>();
			//HashMap<Integer, Integer> forgottenPrematureCases = new HashMap<>();
			//HashMap<Integer, Integer> eternalPrematureCases = new HashMap<>();
			HashMap<String, ArrayList<PartialAlignment>> alignmentsRecord = new HashMap<>();
			//for(int k=0; k<numOfFolds; k++) {

			allFoldsCCResults.put(0, new ResultsCollection2());                                       // HashMap<Integer, HashMap<String,ArrayList<Double>>> 
			//allFoldsStates.put(k, new ArrayList<>());
			HashMap<String, ArrayList<PartialAlignment>> forgottenWithPredictedMarking = new LinkedHashMap<>();  //to keep track of the wrong prediction decisions
			ArrayList<String> casesWithPredictedMarking = new ArrayList<>();

			replayer.getDataStore().clear();

			//Classifier related stuff
			WekaDataSetsCreation wekaDataSetsCreation = classifiersContainer.foldsclassifiers.get(0);			

			Instances classifiedInstancesOCC = wekaDataSetsCreation.synthesizeTestSet();

			ArrayList<Triplet<String,String,Date>>	eventStream = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(testLog);

			HashMap<String, ArrayList<PartialAlignment>> prematureCasesDuringExecutionList = new HashMap<>();
			HashMap<String, ArrayList<PartialAlignment>> prematureCasesAfterExecutionList = new HashMap<>();
			int prematureCasesDuringExecution=0;   //cases which were forgotten before reaching the desired number of oprhan events
			int prematureCasesAfterExecution=0;     //cases which does not reach the desired number of oprhan events as the stream is finished

			for (Triplet<String,String, Date> entry : eventStream) {

				String caseId = entry.getValue0();
				String event = entry.getValue1();

				PartialAlignment<String, Transition, Marking> partialAlignment = null;

				if(replayer.getDataStore().containsKey(caseId)) {  //the case is existing in the memory

					if(replayer.getDataStore().get(caseId).getState().getStateInModel()==null){

						if(replayer.getDataStore().get(caseId).size()<(numOfFeatures-1)) { //if the partial alignment size is less than numoffeatures-1 and last state marking == null then append a log-move with null marking to the current prefix alignment
							//AND IF THE LAST EVENT HAS BEEN OBSERVED THEN
							if(caseEndingEvents.contains(event) && classifiersContainer.endMarkerEnabled) {
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

								String shortInstance = "";
								for(int l=0; l<orphanEvents.size(); l++) {
									if(l<orphanEvents.size()-1) {
										shortInstance = shortInstance + orphanEvents.get(l) + ",";
									}else {
										shortInstance = shortInstance + orphanEvents.get(l);
									}
								}

								Marking marking = wekaDataSetsCreation.getMarkingShortInstance(shortInstance);
								if(marking != null) {
									State dummyStartState =  PartialAlignment.State.Factory.construct(marking, 0, null, Move.Factory.construct(null, null , 0.0));

									//double residualCosts =  predict cost as well
									//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
									statesArray.add(dummyStartState);

									for(int index=statesArray.size()-2; index>=0;index--) {
										statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
												statesArray.get(index+1), statesArray.get(index).getParentMove()));
									}

									replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
									partialAlignment = replayer.processEvent(caseId, event);
								}else {
									String strategy = "forced";
									if(strategy.equals("forced")) {
										if(orphanEvents.size()<numOfFeatures) {
											int difference = numOfFeatures- orphanEvents.size();
											for (int i =0; i<difference; i++) {
												orphanEvents.add(0, "?");
											}
										}


										Instances testInstance = wekaDataSetsCreation.synthesizeTestSetWithMissingValues(orphanEvents);
										Marking predictedMarking = null;
										try {
											double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
											predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
											testInstance.get(0).setClassValue(classValue);
											classifiedInstancesOCC.add(testInstance.get(0));
											casesWithPredictedMarking.add(caseId);
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
										partialAlignment = replayer.processEvent(caseId, event);	
									}else {
										System.out.println("The Not enforced strategy");
										State parent = replayer.getDataStore().get(caseId).getState();							
										partialAlignment = synthesizePartialAlignment(parent, event);
										replayer.getDataStore().put(caseId, (A)partialAlignment);
									}									
								}

							}else {
								State parent = replayer.getDataStore().get(caseId).getState();							
								partialAlignment = synthesizePartialAlignment(parent, event);
								replayer.getDataStore().put(caseId, (A)partialAlignment);
							}
						
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
								double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
								predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
								testInstance.get(0).setClassValue(classValue);
								classifiedInstancesOCC.add(testInstance.get(0));
								casesWithPredictedMarking.add(caseId);
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
							partialAlignment = replayer.processEvent(caseId, event);	
//							if(partialAlignment.getCost()>3.0) {
//								System.out.println("non-conf");
//							}
						}

					}else {
						//the observed event is not orphan and therefore normally compute prefix alignment
						partialAlignment = replayer.processEvent(caseId, event);
					}						

				}else{  //case not present in the datastore

					if(replayer.getDataStore().size()>=maxCasesToStore) {  //memory-management
						String caseToBeForgotten = null;
						if(classifiersContainer.forgettingPolicy.equals("enriched")) {
							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), caseEndingEvents);
						}else {
							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
						}
						//String caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
						//System.out.print(caseToBeForgotten + " ");
						recordCaseCosts(0, caseToBeForgotten, allFoldsCCResults, replayer.getDataStore().get(caseToBeForgotten)/*, casesWithPredictedMarking*/);

						if(replayer.getDataStore().get(caseToBeForgotten).getState().getStateInModel()==null) {
							prematureCasesDuringExecution++;
							if(prematureCasesDuringExecutionList.containsKey(caseToBeForgotten)) {
								prematureCasesDuringExecutionList.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
							}else {
								ArrayList<PartialAlignment> temp = new ArrayList<>();
								temp.add(replayer.getDataStore().get(caseToBeForgotten));
								prematureCasesDuringExecutionList.put(caseToBeForgotten, temp);
							}
						}
						if(casesWithPredictedMarking.contains(caseToBeForgotten)) {
							if(forgottenWithPredictedMarking.containsKey(caseToBeForgotten)) {
								forgottenWithPredictedMarking.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
							}else {
								ArrayList<PartialAlignment> temp = new ArrayList<>();
								temp.add(replayer.getDataStore().get(caseToBeForgotten));
								forgottenWithPredictedMarking.put(caseToBeForgotten, temp);
							}
							casesWithPredictedMarking.remove(caseToBeForgotten);
						}

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
								double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
								predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
								testInstance.get(0).setClassValue(classValue);
								classifiedInstancesOCC.add(testInstance.get(0));
								casesWithPredictedMarking.add(caseId);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));

							//double residualCosts =  predict cost as well
							//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));


							replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(dummyStartState)); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
							partialAlignment = replayer.processEvent(caseId, event);	
//							if(partialAlignment.getCost()>0.0) {
//								System.out.println("non-conf");
//							}

						}else {

							if(caseEndingEvents.contains(event) && classifiersContainer.endMarkerEnabled) {
								ArrayList<State> statesArray = new ArrayList<>();
								Marking marking = wekaDataSetsCreation.getMarkingShortInstance(event);
								State dummyStartState =  PartialAlignment.State.Factory.construct(marking, 0, null, Move.Factory.construct(null, null , 0.0));

								//double residualCosts =  predict cost as well
								//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
								statesArray.add(dummyStartState);

								for(int index=statesArray.size()-2; index>=0;index--) {
									statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
											statesArray.get(index+1), statesArray.get(index).getParentMove()));
								}

								replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
								partialAlignment = replayer.processEvent(caseId, event);
							}else {
								//synthesize a partial alignment with a state which has log-move of the observed event, NULL reached marking, and NULL parent. STORE this prefix-alignment in the datastore.
								partialAlignment = synthesizePartialAlignment(null, event);
								replayer.getDataStore().put(caseId, (A)partialAlignment);
							}

						}

					}
				}
				//On every prefix-alignment computation, we record the number of states
				foldStates.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));

			}

			//recording statistics for a single fold

			for(Entry<String, A> entry: replayer.getDataStore().entrySet()) {  //record costs for cases which survive in the memory after the stream ends
				recordCaseCosts(0, entry.getKey(), allFoldsCCResults, entry.getValue()/*, casesWithPredictedMarking*/);
				if( entry.getValue().getState().getStateInModel()==null) {
					prematureCasesAfterExecution++;
					if(prematureCasesAfterExecutionList.containsKey(entry.getKey())) {
						prematureCasesAfterExecutionList.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
					}else {
						ArrayList<PartialAlignment> temp = new ArrayList<>();
						temp.add(replayer.getDataStore().get(entry.getKey()));
						prematureCasesAfterExecutionList.put(entry.getKey(), temp);
					}
				}

				if(casesWithPredictedMarking.contains(entry.getKey())) {
					if(forgottenWithPredictedMarking.containsKey(entry.getKey())) {
						forgottenWithPredictedMarking.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
					}else {
						ArrayList<PartialAlignment> temp = new ArrayList<>();
						temp.add(replayer.getDataStore().get(entry.getKey()));
						forgottenWithPredictedMarking.put(entry.getKey(), temp);
					}
					casesWithPredictedMarking.remove(entry.getKey());
				}	
				
			}
			

		
			allFoldsCCResults.get(0).sumOfForgottenPrematureCases = prematureCasesDuringExecution;
			allFoldsCCResults.get(0).sumOfEternalPrematureCases = prematureCasesAfterExecution;
			allFoldsCCResults.get(0).maxStates = Collections.max(foldStates);
			
			return allFoldsCCResults;

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


	private static HashSet<String> getCaseStarterEvents(final Petrinet net){

		HashSet<String> temp = new HashSet<>();

		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
			if ((tran.getVisiblePredecessors()).isEmpty()){
				temp.add(tran.toString());
			}
		}
		return temp;
	}

	private static HashSet<String> getCaseEndingEvents(final Petrinet net){

		HashSet<String> temp = new HashSet<>();

		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
			if ((tran.getVisibleSuccessors()).isEmpty()){
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

	private static <L, T, S> void recordCaseCosts(int fold, String caseId, HashMap<Integer, ResultsCollection2> allFoldsResults, 
			PartialAlignment<L, T, S> partialAlignment/*, HashMap<String, ArrayList<PartialAlignment>> casesWithPredictedMarking*/) {

		if(allFoldsResults.get(fold).costRecords.containsKey(caseId)) {
			double oldCostValue = allFoldsResults.get(fold).costRecords.get(caseId);
			allFoldsResults.get(fold).costRecords.put(caseId, oldCostValue + partialAlignment.getCost());
		}else {					
			allFoldsResults.get(fold).costRecords.put(caseId, partialAlignment.getCost());			
		}

	}


	private static LinkedHashMap<String, ResultsCollection2> aggregateFoldsResults(LinkedHashMap<String, ResultsCollection2> allFoldsResults, int[] featureSizes, int[] maxCasesToStoreChoices, int numFolds){

		LinkedHashMap<String, ResultsCollection2> consolidatedResults = new LinkedHashMap<>();

		for(int i=0; i<featureSizes.length; i++) {
			for(int j=0; j<maxCasesToStoreChoices.length;j++) {
				
				ResultsCollection2 resultsCollection2 = new ResultsCollection2();   //this object consolidates the results of 10 folds for each feature and case limit combination
				resultsCollection2.foldsResults = new ArrayList<>();
				int maxStates =0;
				int sumPreForgettings = 0;
				int sumEternalForgettings = 0;
				
				for(int fold=0; fold<numFolds; fold++) {
					for(Entry<String, ResultsCollection2> results: allFoldsResults.entrySet()) {
						String[] values = ((String) results.getKey().subSequence(1, results.getKey().length()-1)).split("/");
						if(Integer.parseInt(values[0]) == featureSizes[i] && Integer.parseInt(values[1]) == maxCasesToStoreChoices[j]
								&& Integer.parseInt(values[2]) == fold) {
							for(Entry<String, Double> entry: results.getValue().costRecords.entrySet()) {
								resultsCollection2.foldsResults.add(new Triplet<Integer, String, Double>(fold, entry.getKey(), entry.getValue()));
							}
							
							if(results.getValue().maxStates > maxStates) {
								maxStates = results.getValue().maxStates;
							}
							sumPreForgettings += results.getValue().sumOfForgottenPrematureCases;
							sumEternalForgettings += results.getValue().sumOfEternalPrematureCases;
							break;
						}
					}
				}
				resultsCollection2.maxStates = maxStates;
				resultsCollection2.sumOfForgottenPrematureCases = sumPreForgettings/numFolds;
				resultsCollection2.sumOfEternalPrematureCases = sumEternalForgettings/numFolds;
				consolidatedResults.put("(" + featureSizes[i] + "/" + maxCasesToStoreChoices[j] + ")", resultsCollection2);
			}
		}	

		return consolidatedResults;		
	}

	private static ResultsCollection2 aggregateFoldsResults(LinkedHashMap<Integer, ResultsCollection2> allFoldsResults){

		ArrayList<Triplet<Integer, String, Double>> consolidatedResults = new ArrayList<>();

		for(Entry<Integer, ResultsCollection2> entry: allFoldsResults.entrySet()) {
			for(Entry<String, Double> innerEntry : entry.getValue().costRecords.entrySet()) {
				Triplet<Integer, String, Double> rec = new Triplet<Integer, String, Double>(entry.getKey(), innerEntry.getKey(), innerEntry.getValue());
				consolidatedResults.add(rec);
			}

		}



		int sumForgottenPrematureCases=0;
		int sumEternalPrematureCases=0;
		int maxStates = 0;
		//
		for(int f=0; f<allFoldsResults.size(); f++) {

			sumForgottenPrematureCases += allFoldsResults.get(f).sumOfForgottenPrematureCases;
			sumEternalPrematureCases += allFoldsResults.get(f).sumOfEternalPrematureCases;
			if(allFoldsResults.get(f).maxStates > maxStates) {
				maxStates = allFoldsResults.get(f).maxStates;
			}
			//			if(tempMax > maxStates) {
			//				maxStates = tempMax;
			//			}
		}

		ResultsCollection2 resultsCollection2 = new ResultsCollection2();
		resultsCollection2.caseLimitSize = allFoldsResults.get(0).caseLimitSize;
		resultsCollection2.featureSize = allFoldsResults.get(0).featureSize;
		resultsCollection2.sumOfForgottenPrematureCases = sumForgottenPrematureCases/allFoldsResults.size();
		resultsCollection2.sumOfEternalPrematureCases = sumEternalPrematureCases/allFoldsResults.size();	
		resultsCollection2.foldsResults = new ArrayList<Triplet<Integer, String, Double>>();		
		resultsCollection2.foldsResults.addAll(consolidatedResults);
		resultsCollection2.maxStates = maxStates;
		return resultsCollection2;		
	}

	
	public static void populateClassifiersRepository(final PluginContext context, final Petrinet net,final XLog log, Marking initialMarking, Marking finalMarking,
			String classifierChoice, int featureSize, ArrayList<WekaDataSetsCreation> classifiersRepository, Boolean endMarkerEnabled, ArrayList<String> markingClasses) throws IOException{

		int numOfFolds = 10;
		int bracket = (int) Math.ceil((double)log.size() / numOfFolds);
		int startIndex = -1;
		int endIndex = -1;
		int[] startIndexArray = new int[numOfFolds];
		int[] endIndexArray = new int[numOfFolds];

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

		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = WekaDataSetsCreation.getReplayer(context, net, initialMarking, finalMarking);


		for(int k=0; k<numOfFolds; k++) {				

			//replayer.getDataStore().clear();
			XLog trainLog = XLogHelper.generateNewXLog("traininglog");
			//XLog testLog = XLogHelper.generateNewXLog("testlog");

			for(int x=0; x<numOfFolds; x++) {
				if(x != k) {
					for(int index = startIndexArray[x]; index<=endIndexArray[x]; index++) {
						trainLog.add(log.get(index));
					}
					//System.out.println("Training log for fold " + k + " :" + startIndexArray[x] + "," + endIndexArray[x]);
				}
			}

			WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
			try {
				wekaDataSetsCreation.trainClassifier(context, trainLog, net, initialMarking, featureSize, endMarkerEnabled, markingClasses, replayer/*featureSizes[featureSize]*/);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//classifiersRepository.get(featureSizes[featureSize]).add(wekaDataSetsCreation);
			classifiersRepository.add(wekaDataSetsCreation);
		}
		//}		

	}

	public static HashMap<Integer, WekaDataSetsCreation> populateClassifiersRepository(final PluginContext context, final Petrinet net,final XLog log, Marking initialMarking, Marking finalMarking,
			String classifierChoice, int featureSize, Boolean endMarkerEnabled, ArrayList<String> markingClasses, String path) throws IOException{

		int numOfFolds = 10;
		int bracket = (int) Math.ceil((double)log.size() / numOfFolds);
		int startIndex = -1;
		int endIndex = -1;
		int[] startIndexArray = new int[numOfFolds];
		int[] endIndexArray = new int[numOfFolds];

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

		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = WekaDataSetsCreation.getReplayer(context, net, initialMarking, finalMarking);

		HashMap<Integer, WekaDataSetsCreation> classifiersRepository = new HashMap<>();

		//for(int featureSize = 0; featureSize<featureSizes.length; featureSize++) {

		//classifiersRepository.put(featureSizes[featureSize], new ArrayList<WekaDataSetsCreation>());

		for(int k=0; k<numOfFolds; k++) {				

			//replayer.getDataStore().clear();
			XLog trainLog = XLogHelper.generateNewXLog("traininglog");
			//XLog testLog = XLogHelper.generateNewXLog("testlog");

			for(int x=0; x<numOfFolds; x++) {
				if(x != k) {
					for(int index = startIndexArray[x]; index<=endIndexArray[x]; index++) {
						trainLog.add(log.get(index));
					}
					//System.out.println("Training log for fold " + k + " :" + startIndexArray[x] + "," + endIndexArray[x]);
				}
			}

			WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
			try {
				wekaDataSetsCreation.trainClassifier(context, trainLog, net, initialMarking, featureSize, endMarkerEnabled, markingClasses, replayer/*featureSizes[featureSize]*/);
				wekaDataSetsCreation.serializeClassifier(path, k);
				classifiersRepository.put(k, wekaDataSetsCreation);
			
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	
		return classifiersRepository;

	}

	private void TimeStampLogs(File inputFolder, String inputFolderPath, int[] mus, int[] lambdas) {
		for (File variant : inputFolder.listFiles()) {  //variant refers to a distinct zero noise value

			if (variant.isDirectory() /*|| !variant.getName().startsWith("a42f0n00")*/) {
				continue;
			}

			System.out.print("Variant:" + variant.getName());

			String variantName = FilenameUtils.getBaseName(variant.getName());
			String variantExtension = FilenameUtils.getExtension(variant.getName());
			String prefixVariant = variantName.substring(0, variantName.length()-3);

			if(!variantExtension.equals("xes") || !variantName.endsWith("00")) {

				System.out.println(variant.getName() + "\t error!! either not an xes file or not a variant file");
				continue;
			}

			XLog variantLog = null; 
			//String variantLogFile = inputFolder + "/" + variant.getName();
			try {
				variantLog = new XUniversalParser().parse(variant).iterator().next();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			XLog variantLogTimed = null; 
			//timestamp the variant here
			for(int i=0; i<mus.length; i++) {
				TimestampingEventData timestampingEventData = new TimestampingEventData(mus[i], lambdas[i]);
				variantLogTimed = timestampingEventData.timeStamping(variantLog);
				timestampingEventData.SerializeLog(variantLogTimed, inputFolderPath + "/timed logs/" + variantName + "_" + mus[i] +"_" + lambdas[i] + ".xes");

				//we can time the instances here

				for (File instanceOfVariant : inputFolder.listFiles()) { 
					String instanceName = FilenameUtils.getBaseName(instanceOfVariant.getName());
					String instanceExtension = FilenameUtils.getExtension(instanceOfVariant.getName());
					String prefixInstance = instanceName.substring(0, instanceName.length()-3);
					//String suffixInstance = instanceName.substring(instanceName.length()-1, instanceName.length());

					if(prefixInstance.equals(prefixVariant) && !instanceName.endsWith("00")) {
						XLog instanceLog = null; 
						System.out.println("\t Instance:" + instanceOfVariant.getName());

						if(!instanceExtension.equals("xes")) {
							//continue;
							System.out.println("error!! not an xes file");
							continue;
						}

						//String instanceLogFile = inputFolder + "/" + instanceOfVariant.getName();

						try {
							instanceLog = new XUniversalParser().parse(instanceOfVariant).iterator().next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						XLog instanceLogTimed=null;
						try {
							instanceLogTimed = TimestampsSynchronization.synchronizeLogs(variantLogTimed, instanceLog);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						timestampingEventData.SerializeLog(instanceLogTimed, inputFolderPath + "/timed logs/" + instanceName + "_" + mus[i] +"_" + lambdas[i] + ".xes");

					}

				}
			}
		}
	}


	private ArrayList<String> populateMarkingClasses(String path) throws IOException{
		BufferedReader br = null;
		ArrayList<String> temp = new ArrayList<>();
		try {
			br = new BufferedReader(new FileReader(path));
			String line = null;
			while ((line = br.readLine()) != null) {

				temp.add(line);
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Collections.sort(temp);
		return temp;
	}

	public static Petrinet constructNet(PluginContext context, String netFile) throws FileNotFoundException, Exception {
		PnmlImportUtils utils = new PnmlImportUtils();
		Pnml pnml = utils.importPnmlFromStream(context, new FileInputStream(netFile), netFile, 0L);
		Petrinet net = PetrinetFactory.newPetrinet(pnml.getLabel());
		utils.connectNet(context, pnml, net);
		for (Place place : net.getPlaces()) {
			System.out.println("Place " + place.getLabel());
		}
		return net;
		
	}

}
