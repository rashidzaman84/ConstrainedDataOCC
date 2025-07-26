package org.processmining.constraineddataocc.plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.helper.ClassifiersContainer;
import org.processmining.constraineddataocc.helper.EventsModifier;
import org.processmining.constraineddataocc.helper.ForgettingCases;
import org.processmining.constraineddataocc.helper.PublishStates_Temp;
import org.processmining.constraineddataocc.helper.ResultsCollection2;
import org.processmining.constraineddataocc.helper.StatesCalculator;
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
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import weka.core.Instances;

//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = "05_1 Compute Prefix Alignments Incrementally - With Marking Prediction - Train Test Split", parameterLabels = {"Model", "Event Data", "Training Event Data", "Test Event Data"  }, 
returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class },
help = "Conformance checking for unlimited cases but storing only a limited number of cases in memory.")

public class MLBasedBoundedTraces_TrainTestSplit_0CC {
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
		parameters.setLookBackWindow(Integer.MAX_VALUE);
		

		//-----------------------------------------------------parameters to set
		String logName = "BPIC12";
		Boolean endMarkerEnabled = true;
		String[] forgettingPolicies = {"shortest-non-conf", "longest non-conf","enriched"};
		String[] StoreTypes = {"HashMap", "LinkedHashmap"};
		String forgettingPolicy = forgettingPolicies[2];
		String StoreType = StoreTypes[0];
		
		int[] featureSizes = {1,2,3/*,4,5*/};
		int[] maxCasesToStoreChoices = {50,100,200,300,400,500,1000};
		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		String[] evaluationChoices = {"10fold", "70train-30test", "offline"};
		
		int trainingSetSize = 70;
		//int testSetSize = 30;
		String classifierChoice = classifierChoices[0];
		//String logVariant = logVariantChoices[0];
		String evaluation = evaluationChoices[1];	//For real-data String evaluation = "70train-30test";
		
		String outputFolderPath = "D:/Experiments/Results/FN/";
		

		int breakpoint = (int) Math.ceil(log.size() *((double)trainingSetSize/100));
		
		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

		XLog trainLog = XLogHelper.generateNewXLog("traininglog");
		XLog testLog = XLogHelper.generateNewXLog("testlog");

		for(int index=0; index<log.size(); index++) {
			if(index<breakpoint) {
				trainLog.add(log.get(index));				
			}else if (index>=breakpoint) {			
				testLog.add(log.get(index));				
			}
		}
		
		System.out.println("Training Log size: " + trainLog.size());
		System.out.println("Test Log size: " + testLog.size());
	
		for(int i=0; i<featureSizes.length; i++) {
			int numOfFeatures = featureSizes[i];
			
			for(int j=0; j<maxCasesToStoreChoices.length; j++) {
				int maxCasesToStore = maxCasesToStoreChoices[j];

				System.out.print("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore + "\t");
				
				ClassifiersContainer classifiersContainer = new ClassifiersContainer();
				classifiersContainer.storageFolder = outputFolderPath + "classifiers/";
				classifiersContainer.endMarkerEnabled = endMarkerEnabled;
				classifiersContainer.forgettingPolicy = forgettingPolicy;
				classifiersContainer.StoreType = StoreType;
				classifiersContainer.fold = 0;
				classifiersContainer.fileName = "BPIC";

				ResultsCollection2 resultsCollection2 = applyGeneric(context, net, initialMarking, finalMarking, trainLog, testLog, parameters, maxCasesToStore, classifierChoice, evaluation, numOfFeatures, classifiersContainer/*, parallelCases, fileName, offlineCCResults*/); 

				globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + ")", resultsCollection2);
			}
		}

		//PublishResults.writeToFilesCC(globalResults, logName, classifierChoice, outputFolderPath,"Eventual");
		
		return null;
	}
	
	@UITopiaVariant(author = "R.Zaman", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {0,2,3})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final UIPluginContext context, final Petrinet net, XLog trainLog, XLog testLog) throws IOException {

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

		Boolean endMarkerEnabled = true;
		String[] forgettingPolicies = {"shortest-non-conf", "longest non-conf","enriched", "LRU"};
		String[] StoreTypes = {"HashMap", "LinkedHashmap"};
		String forgettingPolicy = forgettingPolicies[2];
		String StoreType = StoreTypes[0];
		
		int[] featureSizes = {1,2,3};
		int[] maxCasesToStoreChoices = {50,100,200,300,400,500,1000};
		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		String[] evaluationChoices = {"10fold", "70train-30test", "offline"};
		
		
		String classifierChoice = classifierChoices[0];
		//String logVariant = logVariantChoices[0];
		String evaluation = evaluationChoices[1];	//For real-data String evaluation = "70train-30test";
		
		//String outputFolderPath = "D:/Experiments/Results/FN/";
		String outputFolderPath ="D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results/FN/With End marker/";
		
		
		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

	
		
		System.out.println("Training Log size: " + trainLog.size());
		System.out.println("Test Log size: " + testLog.size());
	
		for(int i=0; i<featureSizes.length; i++) {
			int numOfFeatures = featureSizes[i];
			
			for(int j=0; j<maxCasesToStoreChoices.length; j++) {
				int maxCasesToStore = maxCasesToStoreChoices[j];

				System.out.print("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore + "\t");
				
				ClassifiersContainer classifiersContainer = new ClassifiersContainer();
				classifiersContainer.storageFolder = outputFolderPath + "classifiers/";
				classifiersContainer.endMarkerEnabled = endMarkerEnabled;
				classifiersContainer.forgettingPolicy = forgettingPolicy;
				classifiersContainer.StoreType = StoreType;

				ResultsCollection2 resultsCollection2 = applyGeneric(context, net, initialMarking, finalMarking, trainLog, testLog, parameters, maxCasesToStore, classifierChoice, evaluation, numOfFeatures, classifiersContainer/*, parallelCases, fileName, offlineCCResults*/); 

				globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + ")", resultsCollection2);
			}
		}

		//PublishResults.writeToFilesCC(globalResults, "BPIC12", classifierChoice, outputFolderPath);
		PublishStates_Temp.writeToFilesCC(globalResults, "BPIC12", "", outputFolderPath);


		return null;
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 applyGeneric(final UIPluginContext context,
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog trainLog, XLog testLog, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, 
			int maxCasesToStore, String classifierChoice, String evaluation, int featureSize, ClassifiersContainer classifiersContainer /*,int parallelCases, String fileName, HashMap<Integer, HashMap<String, Double>> offlineCCResults*/) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}

		//Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		
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
		return processXLog(context, trainLog, testLog, net, initialMarking, finalMarking, replayer, featureSize, maxCasesToStore, classifierChoice, evaluation, classifiersContainer /*,parallelCases, fileName, offlineCCResults*/);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> ResultsCollection2 processXLog(final UIPluginContext context,
			XLog trainLog, XLog testLog, Petrinet net, Marking iMarking, final Marking finalMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer, int numOfFeatures,
					int maxCasesToStore, String classifierChoice, String evaluation, ClassifiersContainer classifiersContainer /*,int parallelCases, String fileName, HashMap<Integer, HashMap<String, Double>> offlineCCResults*/) throws IOException{

		
		HashSet<String> caseStarterEvents = getCaseStarterEvents(net);
		HashSet<String> caseEndingEvents = getCaseEndingEvents(net);

		if(evaluation.equals("70train-30test")) {

			
			XLog enrichedTestLog = (XLog) testLog.clone();
			EventsModifier.addFeatures(enrichedTestLog);

			
			
			
			
			HashMap<String,Double> testsetCCResults = new HashMap<>();   //<fold(k), HashMap<caseId,Arraylist<double>>>
			ArrayList<Integer> statesResult = new ArrayList<>();

			WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
//			wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
//					wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking));
			wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
					wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking), "parent");
			Instances classifiedInstancesOCC = wekaDataSetsCreation.synthesizeTestSet();
			
//			WekaDataSetsCreation wekaDataSetsCreation2 = new WekaDataSetsCreation(classifierChoice);
//			wekaDataSetsCreation.trainClassifier2(context, testLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
//					wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking), "parent");
			
			

			//ArrayList<Triplet<String,String,Date>>	eventStream = ParallelCasesBasedLogToStreamConverter.logToStream(testLog, parallelCases, numOfFeatures, numOfFeatures+2);
			ArrayList<XEvent>	eventStream = TimeStampsBasedLogToStreamConverter.sortEventsByDate(enrichedTestLog);

			HashMap<String, ArrayList<PartialAlignment>> prematureCasesDuringExecutionList = new HashMap<>();
			HashMap<String, ArrayList<PartialAlignment>> prematureCasesAfterExecutionList = new HashMap<>();
			int prematureCasesDuringExecution=0;   //cases which were forgotten before reaching the desired number of oprhan events
			int prematureCasesAfterExecution=0;     //cases which does not reach the desired number of oprhan events as the stream is finished
			
			HashMap<String, ArrayList<PartialAlignment>> forgottenWithPredictedMarking = new LinkedHashMap<>();
			ArrayList<String> casesWithPredictedMarking = new ArrayList<>();
			
			HashMap<String, ArrayList<PartialAlignment>> alignmentsRecord = new HashMap<>();
			
			for (XEvent entry : eventStream) {

				String caseId = entry.getAttributes().get("caseid").toString();
				String event = XConceptExtension.instance().extractName(entry);
				//String caseId = entry.getValue0();
				//String event = entry.getValue1();
				
				String orphan="";
									
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
						}

					}else {
						//the observed event is not orphan and therefore normally compute prefix alignment
						partialAlignment = replayer.processEvent(caseId, event);
					}						

				}else{  //case not present in the datastore

					if(replayer.getDataStore().size()>=maxCasesToStore) {  //memory-management
						//String caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
						String caseToBeForgotten = null;
						if(classifiersContainer.forgettingPolicy.equals("enriched")) {
							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), caseEndingEvents);
						}else if(classifiersContainer.forgettingPolicy.equals("LRU")){
							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), "LRU");
						}else {
							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
						}
						recordCaseCosts(/*k*/numOfFeatures, caseToBeForgotten, testsetCCResults, replayer.getDataStore().get(caseToBeForgotten),"Eventual");

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
						
						if(alignmentsRecord.containsKey(caseToBeForgotten)){
							alignmentsRecord.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
						}else {
							ArrayList<PartialAlignment> temp = new ArrayList<>();
							temp.add(replayer.getDataStore().get(caseToBeForgotten));
							alignmentsRecord.put(caseToBeForgotten, temp);
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
							orphan = "Yes";

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
								orphan = "Yes";
							}else {
								//synthesize a partial alignment with a state which has log-move of the observed event, NULL reached marking, and NULL parent. STORE this prefix-alignment in the datastore.
								partialAlignment = synthesizePartialAlignment(null, event);
								replayer.getDataStore().put(caseId, (A)partialAlignment);
								orphan = "Yes";
							}

						}

					}
				}
				
				EventsModifier.updateEventStats(entry, replayer.getDataStore().get(caseId).getCost(), orphan);
				orphan ="";
				//On every prefix-alignment computation, we record the number of states
				statesResult.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));
				
				if(classifiersContainer.StoreType.equals("LinkedHashmap")) {
					//replayer.getDataStore().remove(caseId);
					replayer.getDataStore().put(caseId, replayer.getDataStore().remove(caseId));					
				}

			}
			

			for(Entry<String, A> entry: replayer.getDataStore().entrySet()) {  //record costs for cases which survive in the memory after the stream ends
				recordCaseCosts(/*k*/numOfFeatures, entry.getKey(), testsetCCResults, entry.getValue(), "Eventual");
//				if( entry.getValue().getState().getStateInModel()==null) {
//					prematureCasesAfterExecution++;
//				}
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
				if(alignmentsRecord.containsKey(entry.getKey())){
					alignmentsRecord.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
				}else {
					ArrayList<PartialAlignment> temp = new ArrayList<>();
					temp.add(replayer.getDataStore().get(entry.getKey()));
					alignmentsRecord.put(entry.getKey(), temp);
				}
			}
			
			//writeRecordsToFile(alignmentsRecord, numOfFeatures, maxCasesToStore, 0, "BPIC12");
			
			System.out.println("forgottenPrematureCases:" + prematureCasesDuringExecution + ", eternalPrematureCases: " + prematureCasesAfterExecution) ;

			ResultsCollection2 resultsCollection2 = new ResultsCollection2();
			resultsCollection2.caseLimitSize = maxCasesToStore;
			resultsCollection2.featureSize = numOfFeatures;
			resultsCollection2.sumOfEternalPrematureCases = prematureCasesAfterExecution;
			resultsCollection2.sumOfForgottenPrematureCases = prematureCasesDuringExecution;
			resultsCollection2.costRecords.putAll(testsetCCResults);
			resultsCollection2.maxStates = Collections.max(statesResult);
			resultsCollection2.foldStates.addAll(statesResult);
			
			//resultsCollection2.updatedLog = EventsModifier.updateLog(eventStream, enrichedTestLog);
			
			//SAVE THE TRAINING AND TEST SETS

			wekaDataSetsCreation.saveDataSet(wekaDataSetsCreation.getDatasetMarking(), numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, 0, "TrainingSet");
			wekaDataSetsCreation.saveDataSet(classifiedInstancesOCC, numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, 0, "TestSet");

//			System.out.println(prematureCasesDuringExecutionList.size());
//			System.out.println(prematureCasesAfterExecutionList.size());
			writeRecordsToFile(forgottenWithPredictedMarking, numOfFeatures, maxCasesToStore, classifiersContainer, 0);

			return resultsCollection2;
		}else if(evaluation.equals("10fold")){
			//ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);		
			return null;			
		}else if(evaluation.equals("offline")){
			return null;
		}else {
			System.out.println("Unknown Evaluation Choice :(");
			return null;
		}
	}
	
//public static XLog updateLog(ArrayList<XEvent> eventStream, XLog enrichedTestLog) {
//		
//		//XLog updatedLog = (XLog) testLog.clone();
//		for(XEvent event : eventStream) {
//			enrichedTestLog.get( Integer.parseInt(event.getAttributes().get("caseIndex").toString())).set(Integer.parseInt(event.getAttributes().get("eventIndex").toString()), event);
//		}
//		
//		//System.out.println(testLog.size());
//		//System.out.println(updatedLog.size());
//		for(int i =0; i<enrichedTestLog.size(); i++) {
//			for (int k=0; k<enrichedTestLog.get(i).size(); k++) {
//				String event1 = XConceptExtension.instance().extractName(enrichedTestLog.get(i).get(k));
//				String event2 = XConceptExtension.instance().extractName(enrichedTestLog.get(i).get(k));
//				if(event1.equals(event2)) {
//					continue;
//				}else {
//					System.out.println("Two events are different");
//				}
//				}
//		}
//		
//		return enrichedTestLog;		
//		
//	}


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

	private static <L, T, S> void recordCaseCosts(int fold, String caseId, HashMap<String,Double> testsetResults, PartialAlignment<L, T, S> partialAlignment) {
		if(testsetResults.containsKey(caseId)) {
			double oldCostValue = testsetResults.get(caseId);
			testsetResults.put(caseId, oldCostValue + partialAlignment.getCost());
		}else {					
			testsetResults.put(caseId, partialAlignment.getCost());			
		}
	}
	
	private static <L, T, S> void recordCaseCosts(int fold, String caseId, HashMap<String,Double> testsetResults, PartialAlignment<L, T, S> partialAlignment, String option) {
		if(option.equals("Eventual")) {
//			if(testsetResults.containsKey(caseId) && testsetResults.get(caseId) > 0) {
//				System.out.println("here");
//			}
			testsetResults.put(caseId, partialAlignment.getCost());	
		}
	}
	
	private static void writeRecordsToFile(HashMap<String, ArrayList<PartialAlignment>> casesWithPredictedMarking, int w, int n, ClassifiersContainer classifierStorage, int fold) {

		String outputFilePath = classifierStorage.storageFolder + "/" + w + "_" + n + "_" + fold + ".txt";
		File file = new File(outputFilePath);	
		BufferedWriter bf = null;
		boolean first = true;
		try {
			bf = new BufferedWriter(new FileWriter(file));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		for(Entry<String, ArrayList<PartialAlignment>> rec : casesWithPredictedMarking.entrySet()) {
			//System.out.println(rec);
			try {

				bf.write(rec.getKey() + "," + rec.getValue() + "\n");

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

		double cummulativeCosts = 0.0;
		int nonConformantCases = 0;

		for(Entry<String, ArrayList<PartialAlignment>> rec : casesWithPredictedMarking.entrySet()) {

			double cost = 0.0;
			for(PartialAlignment partialAlignment : rec.getValue()){
				cost += partialAlignment.getCost();
//				if(partialAlignment.getCost()>2.0) {
//					System.out.println("ALERT!!!! " + rec.getKey() + ": " + rec.getValue());
//				}
			}

			if(cost>0.0) {
				cummulativeCosts += cost;
				nonConformantCases++;
				//System.out.println(rec.getKey() + ": " + rec.getValue());
			}
		}

		System.out.println("(" + w + "/" + n + "/" + fold + ")," + nonConformantCases + "," + cummulativeCosts);

	}

}


//			//HERE WE NEED TO DEFINE OUR DATA STRUCTURES FOR 10 FOLDS and later average them 
//			HashMap<String,Double> testsetCCResults = new HashMap<>();   //<fold(k), HashMap<caseId,Arraylist<double>>>
//			ArrayList<Integer> statesResult = new ArrayList<>();
//
//			WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
////			wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
////					wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking));
//			wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
//					wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking), "parent");
//			Instances classifiedInstancesOCC = wekaDataSetsCreation.synthesizeTestSet();
//
//			//ArrayList<Triplet<String,String,Date>>	eventStream = ParallelCasesBasedLogToStreamConverter.logToStream(testLog, parallelCases, numOfFeatures, numOfFeatures+2);
//			ArrayList<Triplet<String,String,Date>>	eventStream = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(testLog);
//
//			HashMap<String, ArrayList<PartialAlignment>> prematureCasesDuringExecutionList = new HashMap<>();
//			HashMap<String, ArrayList<PartialAlignment>> prematureCasesAfterExecutionList = new HashMap<>();
//			int prematureCasesDuringExecution=0;   //cases which were forgotten before reaching the desired number of oprhan events
//			int prematureCasesAfterExecution=0;     //cases which does not reach the desired number of oprhan events as the stream is finished
//			
//			HashMap<String, ArrayList<PartialAlignment>> forgottenWithPredictedMarking = new LinkedHashMap<>();
//			ArrayList<String> casesWithPredictedMarking = new ArrayList<>();
//			
//			HashMap<String, ArrayList<PartialAlignment>> alignmentsRecord = new HashMap<>();
//			
//			for (Triplet<String,String, Date> entry : eventStream) {
//
//				String caseId = entry.getValue0();
//				String event = entry.getValue1();
//									
//				PartialAlignment<String, Transition, Marking> partialAlignment = null;
//
//				if(replayer.getDataStore().containsKey(caseId)) {  //the case is existing in the memory
//
//					if(replayer.getDataStore().get(caseId).getState().getStateInModel()==null){
//
//						if(replayer.getDataStore().get(caseId).size()<(numOfFeatures-1)) { //if the partial alignment size is less than numoffeatures-1 and last state marking == null then append a log-move with null marking to the current prefix alignment
//							//AND IF THE LAST EVENT HAS BEEN OBSERVED THEN
//							if(caseEndingEvents.contains(event) && classifiersContainer.endMarkerEnabled) {
//								ArrayList<State> statesArray = new ArrayList<>();
//								ArrayList<String> orphanEvents = new ArrayList<>();	
//								orphanEvents.add(event);
//
//								State state = replayer.getDataStore().get(caseId).getState();
//								statesArray.add(PartialAlignment.State.Factory.construct(replayer.getFinalStateInModel(), 0, state.getParentState(), state.getParentMove())); //we manipulate the marking here to void Null error from Model-Semantics
//								orphanEvents.add((String) state.getParentMove().getEventLabel());
//								while (state.getParentState() != null) { 
//									state = state.getParentState();
//									orphanEvents.add((String) state.getParentMove().getEventLabel());
//									statesArray.add(state);
//								}
//
//								Collections.reverse(orphanEvents);
//
//								String shortInstance = "";
//								for(int l=0; l<orphanEvents.size(); l++) {
//									if(l<orphanEvents.size()-1) {
//										shortInstance = shortInstance + orphanEvents.get(l) + ",";
//									}else {
//										shortInstance = shortInstance + orphanEvents.get(l);
//									}
//								}
//
//								Marking marking = wekaDataSetsCreation.getMarkingShortInstance(shortInstance);
//								if(marking != null) {
//									State dummyStartState =  PartialAlignment.State.Factory.construct(marking, 0, null, Move.Factory.construct(null, null , 0.0));
//
//									//double residualCosts =  predict cost as well
//									//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
//									statesArray.add(dummyStartState);
//
//									for(int index=statesArray.size()-2; index>=0;index--) {
//										statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
//												statesArray.get(index+1), statesArray.get(index).getParentMove()));
//									}
//
//									replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
//									partialAlignment = replayer.processEvent(caseId, event);
//								}else {
//									String strategy = "forced";
//									if(strategy.equals("forced")) {
//										if(orphanEvents.size()<numOfFeatures) {
//											int difference = numOfFeatures- orphanEvents.size();
//											for (int i =0; i<difference; i++) {
//												orphanEvents.add(0, "?");
//											}
//										}
//
//
//										Instances testInstance = wekaDataSetsCreation.synthesizeTestSetWithMissingValues(orphanEvents);
//										Marking predictedMarking = null;
//										try {
//											double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
//											predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
//											testInstance.get(0).setClassValue(classValue);
//											classifiedInstancesOCC.add(testInstance.get(0));
//											casesWithPredictedMarking.add(caseId);
//										} catch (Exception e) {
//											// TODO Auto-generated catch block
//											e.printStackTrace();
//										}
//
//										State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));
//
//										//double residualCosts =  predict cost as well
//										//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
//										statesArray.add(dummyStartState);
//
//										for(int index=statesArray.size()-2; index>=0;index--) {
//											statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
//													statesArray.get(index+1), statesArray.get(index).getParentMove()));
//										}
//
//										replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
//										partialAlignment = replayer.processEvent(caseId, event);	
//									}else {
//										State parent = replayer.getDataStore().get(caseId).getState();							
//										partialAlignment = synthesizePartialAlignment(parent, event);
//										replayer.getDataStore().put(caseId, (A)partialAlignment);
//									}									
//								}
//
//							}else {
//								State parent = replayer.getDataStore().get(caseId).getState();							
//								partialAlignment = synthesizePartialAlignment(parent, event);
//								replayer.getDataStore().put(caseId, (A)partialAlignment);
//							}
//							
//						}else if(replayer.getDataStore().get(caseId).size()==(numOfFeatures-1)){
//							//else if partial alignment size == numoffeatures-1 and last state marking == null then predict a marking and prepend this marking as a starting state to the current prefix alignment so that the 
//							//computation of the prefix-alignment through shortest path is done from the predicted marking
//
//							ArrayList<State> statesArray = new ArrayList<>();
//							ArrayList<String> orphanEvents = new ArrayList<>();	
//							orphanEvents.add(event);
//
//							State state = replayer.getDataStore().get(caseId).getState();
//							statesArray.add(PartialAlignment.State.Factory.construct(replayer.getFinalStateInModel(), 0, state.getParentState(), state.getParentMove())); //we manipulate the marking here to void Null error from Model-Semantics
//							orphanEvents.add((String) state.getParentMove().getEventLabel());
//							while (state.getParentState() != null) { 
//								state = state.getParentState();
//								orphanEvents.add((String) state.getParentMove().getEventLabel());
//								statesArray.add(state);
//							}
//
//							Collections.reverse(orphanEvents);
//
//							Instances testInstance = wekaDataSetsCreation.synthesizeTestSet(orphanEvents);
//							Marking predictedMarking = null;
//							try {
//								double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
//								predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
//								testInstance.get(0).setClassValue(classValue);
//								classifiedInstancesOCC.add(testInstance.get(0));
//								casesWithPredictedMarking.add(caseId);
//							} catch (Exception e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//
//							State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));
//
//							//double residualCosts =  predict cost as well
//							//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
//							statesArray.add(dummyStartState);
//
//							for(int index=statesArray.size()-2; index>=0;index--) {
//								statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
//										statesArray.get(index+1), statesArray.get(index).getParentMove()));
//							}
//
//							replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
//							partialAlignment = replayer.processEvent(caseId, event);						
//						}
//
//					}else {
//						//the observed event is not orphan and therefore normally compute prefix alignment
//						partialAlignment = replayer.processEvent(caseId, event);
//					}						
//
//				}else{  //case not present in the datastore
//
//					if(replayer.getDataStore().size()>=maxCasesToStore) {  //memory-management
//						//String caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
//						String caseToBeForgotten = null;
//						if(classifiersContainer.forgettingPolicy.equals("enriched")) {
//							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), caseEndingEvents);
//						}else {
//							caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
//						}
//						recordCaseCosts(/*k*/numOfFeatures, caseToBeForgotten, testsetCCResults, replayer.getDataStore().get(caseToBeForgotten));
//
//						if(replayer.getDataStore().get(caseToBeForgotten).getState().getStateInModel()==null) {
//							prematureCasesDuringExecution++;
//							if(prematureCasesDuringExecutionList.containsKey(caseToBeForgotten)) {
//								prematureCasesDuringExecutionList.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
//							}else {
//								ArrayList<PartialAlignment> temp = new ArrayList<>();
//								temp.add(replayer.getDataStore().get(caseToBeForgotten));
//								prematureCasesDuringExecutionList.put(caseToBeForgotten, temp);
//							}
//						}
//						if(casesWithPredictedMarking.contains(caseToBeForgotten)) {
//							if(forgottenWithPredictedMarking.containsKey(caseToBeForgotten)) {
//								forgottenWithPredictedMarking.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
//							}else {
//								ArrayList<PartialAlignment> temp = new ArrayList<>();
//								temp.add(replayer.getDataStore().get(caseToBeForgotten));
//								forgottenWithPredictedMarking.put(caseToBeForgotten, temp);
//							}
//							casesWithPredictedMarking.remove(caseToBeForgotten);
//						}
//						
//						if(alignmentsRecord.containsKey(caseToBeForgotten)){
//							alignmentsRecord.get(caseToBeForgotten).add(replayer.getDataStore().get(caseToBeForgotten));
//						}else {
//							ArrayList<PartialAlignment> temp = new ArrayList<>();
//							temp.add(replayer.getDataStore().get(caseToBeForgotten));
//							alignmentsRecord.put(caseToBeForgotten, temp);
//						}
//						
//						replayer.getDataStore().remove(caseToBeForgotten);  //remove a case on the basis of the forgetting criteria
//					}						
//
//					if(caseStarterEvents.contains(event)) { //the observed event is a case-starting event for a fresh case and therefore normally compute prefix alignment
//						partialAlignment = replayer.processEvent(caseId, event);
//					}else {
//
//						if(numOfFeatures==1) {
//
//							//ArrayList<State> statesArray = new ArrayList<>();
//							ArrayList<String> orphanEvents = new ArrayList<>();	
//							orphanEvents.add(event);
//
//							Instances testInstance = wekaDataSetsCreation.synthesizeTestSet(orphanEvents);
//							Marking predictedMarking = null;
//							try {
//								double classValue = wekaDataSetsCreation.getClassifier().classifyInstance(testInstance.firstInstance());
//								predictedMarking = wekaDataSetsCreation.getMarking(testInstance.classAttribute().value((int) classValue));
//								testInstance.get(0).setClassValue(classValue);
//								classifiedInstancesOCC.add(testInstance.get(0));
//								casesWithPredictedMarking.add(caseId);
//							} catch (Exception e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//
//							State dummyStartState =  PartialAlignment.State.Factory.construct(predictedMarking, 0, null, Move.Factory.construct(null, null , 0.0));
//
//							//double residualCosts =  predict cost as well
//							//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
//
//
//							replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(dummyStartState)); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
//							partialAlignment = replayer.processEvent(caseId, event);		
//
//						}else {
//
//							if(caseEndingEvents.contains(event) && classifiersContainer.endMarkerEnabled) {
//								ArrayList<State> statesArray = new ArrayList<>();
//								Marking marking = wekaDataSetsCreation.getMarkingShortInstance(event);
//								State dummyStartState =  PartialAlignment.State.Factory.construct(marking, 0, null, Move.Factory.construct(null, null , 0.0));
//
//								//double residualCosts =  predict cost as well
//								//state = PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts));
//								statesArray.add(dummyStartState);
//
//								for(int index=statesArray.size()-2; index>=0;index--) {
//									statesArray.set(index, PartialAlignment.State.Factory.construct(statesArray.get(index).getStateInModel(), statesArray.get(index).getNumLabelsExplained() , 
//											statesArray.get(index+1), statesArray.get(index).getParentMove()));
//								}
//
//								replayer.getDataStore().put(caseId, (A) PartialAlignment.Factory.construct(statesArray.get(0))); //through this and the following step we enable computing real prefix-alignment for the numoffeatures orphan events
//								partialAlignment = replayer.processEvent(caseId, event);
//							}else {
//								//synthesize a partial alignment with a state which has log-move of the observed event, NULL reached marking, and NULL parent. STORE this prefix-alignment in the datastore.
//								partialAlignment = synthesizePartialAlignment(null, event);
//								replayer.getDataStore().put(caseId, (A)partialAlignment);
//							}
//
//						}
//
//					}
//				}
//				//On every prefix-alignment computation, we record the number of states
//				statesResult.add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));
//
//			}
//			
//
//			for(Entry<String, A> entry: replayer.getDataStore().entrySet()) {  //record costs for cases which survive in the memory after the stream ends
//				recordCaseCosts(/*k*/numOfFeatures, entry.getKey(), testsetCCResults, entry.getValue());
////				if( entry.getValue().getState().getStateInModel()==null) {
////					prematureCasesAfterExecution++;
////				}
//				if( entry.getValue().getState().getStateInModel()==null) {
//					prematureCasesAfterExecution++;
//					if(prematureCasesAfterExecutionList.containsKey(entry.getKey())) {
//						prematureCasesAfterExecutionList.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
//					}else {
//						ArrayList<PartialAlignment> temp = new ArrayList<>();
//						temp.add(replayer.getDataStore().get(entry.getKey()));
//						prematureCasesAfterExecutionList.put(entry.getKey(), temp);
//					}
//				}
//				if(casesWithPredictedMarking.contains(entry.getKey())) {
//					if(forgottenWithPredictedMarking.containsKey(entry.getKey())) {
//						forgottenWithPredictedMarking.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
//					}else {
//						ArrayList<PartialAlignment> temp = new ArrayList<>();
//						temp.add(replayer.getDataStore().get(entry.getKey()));
//						forgottenWithPredictedMarking.put(entry.getKey(), temp);
//					}
//					casesWithPredictedMarking.remove(entry.getKey());
//				}
//				if(alignmentsRecord.containsKey(entry.getKey())){
//					alignmentsRecord.get(entry.getKey()).add(replayer.getDataStore().get(entry.getKey()));
//				}else {
//					ArrayList<PartialAlignment> temp = new ArrayList<>();
//					temp.add(replayer.getDataStore().get(entry.getKey()));
//					alignmentsRecord.put(entry.getKey(), temp);
//				}
//			}
//			
//			//writeRecordsToFile(alignmentsRecord, numOfFeatures, maxCasesToStore, 0, "BPIC12");
//			
//			System.out.println("forgottenPrematureCases:" + prematureCasesDuringExecution + ", eternalPrematureCases: " + prematureCasesAfterExecution) ;
//
//			ResultsCollection2 resultsCollection2 = new ResultsCollection2();
//			resultsCollection2.caseLimitSize = maxCasesToStore;
//			resultsCollection2.featureSize = numOfFeatures;
//			resultsCollection2.sumOfEternalPrematureCases = prematureCasesAfterExecution;
//			resultsCollection2.sumOfForgottenPrematureCases = prematureCasesDuringExecution;
//			resultsCollection2.costRecords.putAll(testsetCCResults);
//			resultsCollection2.maxStates = Collections.max(statesResult);
//			
//			//SAVE THE TRAINING AND TEST SETS
//
//			wekaDataSetsCreation.saveDataSet(wekaDataSetsCreation.getDatasetMarking(), numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, 0, "TrainingSet");
//			wekaDataSetsCreation.saveDataSet(classifiedInstancesOCC, numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, 0, "TestSet");
//
////			System.out.println(prematureCasesDuringExecutionList.size());
////			System.out.println(prematureCasesAfterExecutionList.size());
//			writeRecordsToFile(forgottenWithPredictedMarking, numOfFeatures, maxCasesToStore, classifiersContainer, 0);
//
//			return resultsCollection2;
//		}else if(evaluation.equals("10fold")){
//			//ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);		
//			return null;			
//		}else if(evaluation.equals("offline")){
//			return null;
//		}else {
//			System.out.println("Unknown Evaluation Choice :(");
//			return null;
//		}
//	}
//
//
//	private static void setupLabelMap(final Petrinet net, Map<Transition, String> modelElementsToLabelMap, Map<String, Collection<Transition>> labelsToModelElementsMap) {
//		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition t : net.getTransitions()) {
//			if (!t.isInvisible()) {
//				String label = t.getLabel();
//				modelElementsToLabelMap.put(t, label);
//				if (!labelsToModelElementsMap.containsKey(label)) {
//					Collection collection = new ArrayList<org.processmining.models.graphbased.directed.petrinet.elements.Transition>();
//					collection.add(t);
//					labelsToModelElementsMap.put(label, collection);
//					//labelsToModelElementsMap.put(label, Collections.singleton(t));
//				} else {
//					labelsToModelElementsMap.get(label).add(t);
//				}
//			}
//		}				
//	}	
//
//	//TODO: needs a parameter object
//	private static void setupModelMoveCosts(final Petrinet net, TObjectDoubleMap<Transition> modelMoveCosts, TObjectDoubleMap<String> labelMoveCosts,
//			Map<String, Collection<Transition>> labelsToModelElementsMap) {
//		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition t : net.getTransitions()) {
//			if (t.isInvisible() /*|| (t.getLabel().equals("A_FINALIZED"))*/) {
//				modelMoveCosts.put(t, (short) 0);
//				//labelMoveCosts.put(t.getLabel(), (short) 0);
//			} else {
//				modelMoveCosts.put(t, (short) 1);
//				//labelMoveCosts.put(t.getLabel(), (short) 1);
//				//labelMoveCosts.put("A_FINALIZED", (short) 1);
//			}
//		}
//
//		for(String label : labelsToModelElementsMap.keySet()) {
//			labelMoveCosts.put(label, (short) 1);
//		}
//	}
//
//
//	public static Marking getFinalMarking(PetrinetGraph net) {
//		Marking finalMarking = new Marking();
//
//		for (Place p : net.getPlaces()) {
//			if (net.getOutEdges(p).isEmpty())
//				finalMarking.add(p);
//		}
//
//		return finalMarking;
//	}
//
//	public static Marking getInitialMarking(PetrinetGraph net) {
//		Marking initMarking = new Marking();
//
//		for (Place p : net.getPlaces()) {
//			if (net.getInEdges(p).isEmpty())
//				initMarking.add(p);
//		}
//
//		return initMarking;
//	}
//
//	private static HashSet<String> getCaseStarterEvents(final Petrinet net){
//
//		HashSet<String> temp = new HashSet<>();
//
//		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
//			if ((tran.getVisiblePredecessors()).isEmpty()){
//				temp.add(tran.toString());
//			}
//		}
//		return temp;
//	}
//	
//	private static HashSet<String> getCaseEndingEvents(final Petrinet net){
//
//		HashSet<String> temp = new HashSet<>();
//
//		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
//			if ((tran.getVisibleSuccessors()).isEmpty()){
//				temp.add(tran.toString());
//			}
//		}
//		return temp;
//	}
//
//	private static <L, T, S> PartialAlignment<L, T, S> synthesizePartialAlignment(State<S, L, T> parentState, String event){
//
//		State<S, L, T> dummyState = PartialAlignment.State.Factory.construct(null, 0, parentState, Move.Factory.construct((L)event, null , 1.0));
//		PartialAlignment<L, T, S> alignment = PartialAlignment.Factory.construct(dummyState);
//
//		return alignment;
//	}
//
//	private static <L, T, S> void recordCaseCosts(int fold, String caseId, HashMap<String,Double> testsetResults, PartialAlignment<L, T, S> partialAlignment) {
//		if(testsetResults.containsKey(caseId)) {
//			double oldCostValue = testsetResults.get(caseId);
//			testsetResults.put(caseId, oldCostValue + partialAlignment.getCost());
//		}else {					
//			testsetResults.put(caseId, partialAlignment.getCost());			
//		}
//	}
//	
//	private static void writeRecordsToFile(HashMap<String, ArrayList<PartialAlignment>> casesWithPredictedMarking, int w, int n, ClassifiersContainer classifierStorage, int fold) {
//
//		String outputFilePath = classifierStorage.storageFolder + "/" + w + "_" + n + "_" + fold + ".txt";
//		File file = new File(outputFilePath);	
//		BufferedWriter bf = null;
//		boolean first = true;
//		try {
//			bf = new BufferedWriter(new FileWriter(file));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		for(Entry<String, ArrayList<PartialAlignment>> rec : casesWithPredictedMarking.entrySet()) {
//			//System.out.println(rec);
//			try {
//
//				bf.write(rec.getKey() + "," + rec.getValue() + "\n");
//
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//
//		try {
//			bf.flush();
//			bf.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		double cummulativeCosts = 0.0;
//		int nonConformantCases = 0;
//
//		for(Entry<String, ArrayList<PartialAlignment>> rec : casesWithPredictedMarking.entrySet()) {
//
//			double cost = 0.0;
//			for(PartialAlignment partialAlignment : rec.getValue()){
//				cost += partialAlignment.getCost();
////				if(partialAlignment.getCost()>2.0) {
////					System.out.println("ALERT!!!! " + rec.getKey() + ": " + rec.getValue());
////				}
//			}
//
//			if(cost>0.0) {
//				cummulativeCosts += cost;
//				nonConformantCases++;
//				//System.out.println(rec.getKey() + ": " + rec.getValue());
//			}
//		}
//
//		System.out.println("(" + w + "/" + n + "/" + fold + ")," + nonConformantCases + "," + cummulativeCosts);
//
//	}
//
//}
