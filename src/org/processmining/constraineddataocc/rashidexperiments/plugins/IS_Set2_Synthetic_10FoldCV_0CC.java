package org.processmining.constraineddataocc.rashidexperiments.plugins;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.importing.PnmlImportUtils;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;


//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = "CC2", parameterLabels = {"Model", "Event Data", "Path to Model", "Path to Log", "Path to Marking" }, 
returnLabels = { "Petri net" }, returnTypes = { Petrinet.class },
help = "Bounded TRACES, dynamic WINDOWS, COST plus EVENT CHAR. ANALYSIS.")

public class IS_Set2_Synthetic_10FoldCV_0CC {
	
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "CC2", requiredParameterLabels = {2, 3, 4})

	public Petrinet apply(
			final PluginContext context, String modelPath, String logPath, String markingPath) throws Exception {
		Petrinet net = context.tryToFindOrConstructFirstNamedObject(Petrinet.class, "Import Petri net from PNML file", null, null, modelPath);
//		Petrinet net = constructNet(context, modelPath);
		XLog log = new XUniversalParser().parse(new File(logPath)).iterator().next();
		ArrayList<String> markingClasses = populateMarkingClasses(markingPath);
		String[] values = logPath.split("/");
		String fileName = values[values.length-1].substring(0, values[values.length-1].length()-4);
		apply(context, net, log, markingClasses, fileName, modelPath);
		return net;
	}

	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "CC2", requiredParameterLabels = { })

	public Petrinet apply(final PluginContext context) throws Exception {
		String model = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/experiments/inputModels/a32.pnml";
		String log = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/experiments/inputLogs/a32f0n00_1_1000.xes";
		String marking = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/experiments/inputModels/a32.txt";
		return apply(context, model, log, marking);
	}
	
	public void apply(final PluginContext context, final Petrinet net, XLog log, ArrayList<String> markingClasses, String fileName, String modelPath) throws IOException {
		
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
		
		Boolean timeStampLogs = false;
		Boolean endMarkerEnabled = true;
		String[] forgettingPolicies = {"shortest-non-conf", "longest non-conf"};
		String[] StoreTypes = {"HashMap", "LinkedHashmap"};
		String forgettingPolicy = forgettingPolicies[0];
		String StoreType = StoreTypes[0];

		int[] featureSizes = {1,2,3,4,5,10};
		int[] maxCasesToStoreChoices = {5,10,15,20,25,50};
//		int[] mus = {1, 1000, 25, 50, 50};
//		int[] lambdas = {1000, 1, 50, 50, 25};
		
		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		//String[] logVariantChoices = {"a42f0timed"/*, "a22f1timed", "a22f5timed", "a22f9timed"*/};
		//String[] logVariantChoices = {"a12f0timed", /*"a12f1timed", "a12f5timed", "a12f9timed"*/};
		String[] evaluationChoices = {"10foldCV", "70train-30test", "offline"};

		String classifierChoice = classifierChoices[0];
		String evaluation = evaluationChoices[0];	//For synthetic data String evaluation = "10foldCV";
//		
//		String logsInputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/" + logType + "/";
//		String modelsInputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Process Models Repository/";
		
		
		String outputFolderPath;
		if(endMarkerEnabled) {
			outputFolderPath = "F:/experiments/results/";
			//outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results Repository/FN/2 HashMap Shortest-first With End marker/";
		}else {
			//outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results Repository/experiments/results/";
			outputFolderPath = "F:/experiments/results/";
		}
		
		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();
		
		
		for(int i=0; i<featureSizes.length; i++) {  //features size
			int numOfFeatures = featureSizes[i];
			
			//lets train a classifier per fold and apply it for that fold of each case limit
			
//			//String classifiersRepositoryPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/experiments/inputModels";
			String classifiersRepositoryPath = "F:/experiments/inputModels/classifiers/";
			
			int numOfFolds = 10;
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
				
			}
			
			//String classifiersRepositoryPath = "F:/experiments/inputModels/classifiers/";
			
			HashMap<Integer, WekaDataSetsCreation> classifiersRepository =  
			populateClassifiersRepository(context, net, log, initialMarking, finalMarking, classifierChoices[0], featureSizes[i], endMarkerEnabled, markingClasses, classifiersRepositoryPath
					);	
			System.gc();


			for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size
				int maxCasesToStore = maxCasesToStoreChoices[j];

				System.out.println("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore);

				ClassifiersContainer classifiersContainer = new ClassifiersContainer();
				classifiersContainer.storageFolder = outputFolderPath + "classifiers/";
				//classifiersContainer.foldsclassifiers.addAll(classifiersRepository);
				classifiersContainer.classifiersRepository = classifiersRepository;
				classifiersContainer.endMarkerEnabled = endMarkerEnabled;
				classifiersContainer.forgettingPolicy = forgettingPolicy;
				classifiersContainer.StoreType = StoreType;
				classifiersContainer.classifiersRepositoryPath = classifiersRepositoryPath;
				System.out.println("parameters, nonConformantCases, cummulativeCosts");
				
				LinkedHashMap<Integer, ResultsCollection2> allFoldsResults = applyGeneric(context, net, initialMarking, finalMarking, log, parameters, maxCasesToStore, classifierChoice, evaluation, numOfFeatures, classifiersContainer); 
				//here we treat the results of 10 folds 
				globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + ")", aggregateFoldsResults(allFoldsResults));
			}
		}
		PublishResults.writeToFilesCC_(globalResults, fileName, classifierChoice, outputFolderPath);
		
	}
	
	
	
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "0 Experiment 10FoldCV Compute Prefix Alignments Incrementally - With Marking Prediction", requiredParameterLabels = { 0/*, 1*/})

	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
			final PluginContext context, final Petrinet net/*, XLog log*/) throws IOException {

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

		Boolean timeStampLogs = false;
		Boolean endMarkerEnabled = true;
		String logType = "a22";
		String[] forgettingPolicies = {"shortest-non-conf", "longest non-conf"};
		String[] StoreTypes = {"HashMap", "LinkedHashmap"};
		String forgettingPolicy = forgettingPolicies[1];
		String StoreType = StoreTypes[0];

		int[] featureSizes = {1,2,3,4,5,10};
		int[] maxCasesToStoreChoices = {5,10,15,20,25,50};
		int[] mus = {1, 1000, 25, 50, 50};
		int[] lambdas = {1000, 1, 50, 50, 25};

		String[] classifierChoices = {"Random Forest", "MultiClass", "Simple Logistic"};
		//String[] logVariantChoices = {"a42f0timed"/*, "a22f1timed", "a22f5timed", "a22f9timed"*/};
		//String[] logVariantChoices = {"a12f0timed", /*"a12f1timed", "a12f5timed", "a12f9timed"*/};
		String[] evaluationChoices = {"10foldCV", "70train-30test", "offline"};

		String classifierChoice = classifierChoices[0];
		String evaluation = evaluationChoices[0];	//For synthetic data String evaluation = "10foldCV";
		
		String logsInputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/" + logType + "/";
		String modelsInputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Process Models Repository/";
		ArrayList<String> markingClasses = populateMarkingClasses(modelsInputFolderPath, logType);
		
		String outputFolderPath;
		if(endMarkerEnabled) {
			outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results Repository/FN/4 HashMap Longest-first with end marker/";
		}else {
			outputFolderPath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Results Repository/FN/LinkedHashmap FLN policy without end marker/";
		}
		
		File inputFolder = new File(logsInputFolderPath);
		


		
		if(timeStampLogs) { //first we do timestamping the logs
			
			TimeStampLogs(inputFolder, logsInputFolderPath, mus, lambdas);			
		}	

		//TIMESTAMPING completed. Now we do CC of all the generated logs with respect to different feature and case limits
	
		LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

		File newInputFolder = new File(inputFolder + "/timed logs/");

		for (File file : newInputFolder.listFiles()) { 

			System.out.println(file.getName());

			String fileName = FilenameUtils.getBaseName(file.getName());
			String fileExtension = FilenameUtils.getExtension(file.getName());

			XLog log = null; 

			if(!fileExtension.equals("xes")) {
				//continue;
				System.out.println("error!! not an xes file");
				continue;
			}

			//String LogFile = newInputFolder + "/" + file.getName();

			try {
				log = new XUniversalParser().parse(file).iterator().next();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
			
			//lets pre-train the classifiers here
		
			for(int i=0; i<featureSizes.length; i++) {  //features size
				int numOfFeatures = featureSizes[i];
				
				//lets pre-train all 10 classifiers for the 10 folds for each feature size
				
				ArrayList<WekaDataSetsCreation> classifiersRepository = new ArrayList<>(); 
				//populateClassifiersRepository(context, net, log, initialMarking, classifierChoices[0], featureSizes[i], classifiersRepository, endMarkerEnabled, markingClasses);	


				for(int j=0; j<maxCasesToStoreChoices.length; j++) {      //n size
					int maxCasesToStore = maxCasesToStoreChoices[j];

					System.out.println("\t Feature Size: " + numOfFeatures + ", Max. Cases: " + maxCasesToStore);

					ClassifiersContainer classifiersContainer = new ClassifiersContainer();
					classifiersContainer.storageFolder = outputFolderPath + "classifiers/";
					classifiersContainer.foldsclassifiers.addAll(classifiersRepository);
					classifiersContainer.endMarkerEnabled = endMarkerEnabled;
					classifiersContainer.forgettingPolicy = forgettingPolicy;
					classifiersContainer.StoreType = StoreType;
					System.out.println("parameters, nonConformantCases, cummulativeCosts");
					
					LinkedHashMap<Integer, ResultsCollection2> allFoldsResults = applyGeneric(context, net, initialMarking, finalMarking, log, parameters, maxCasesToStore, classifierChoice, evaluation, numOfFeatures, classifiersContainer); 
					//here we treat the results of 10 folds 
					globalResults.put("(" + numOfFeatures + "/" + maxCasesToStore + ")", aggregateFoldsResults(allFoldsResults));
				}
			}
			PublishResults.writeToFilesCC_(globalResults, fileName, classifierChoice, outputFolderPath);
		}		
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> LinkedHashMap<Integer, ResultsCollection2> applyGeneric(final PluginContext context,
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters, 
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
		return processXLog(context, log, net, initialMarking, finalMarking, replayer, numOfFeatures, maxCasesToStore, classifierChoice, evaluation, classifiersContainer);

	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> LinkedHashMap<Integer, ResultsCollection2>  processXLog(final PluginContext context,
			XLog log, Petrinet net, Marking iMarking, final Marking finalMarking, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer, int numOfFeatures,
					int maxCasesToStore, String classifierChoice, String evaluation, ClassifiersContainer classifiersContainer){


		//XLog log = TimeStampsBasedLogToStreamConverter.sortEventLogByCaseArrivalTime(log_);
		
		

		HashSet<String> caseStarterEvents = getCaseStarterEvents(net);
		HashSet<String> caseEndingEvents = getCaseEndingEvents(net);

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

			//HERE WE DEFINE OUR DATA STRUCTURES FOR 10 FOLDS
			LinkedHashMap<Integer, ResultsCollection2> allFoldsCCResults = new LinkedHashMap<>();   //<fold(k), HashMap<caseId,Arraylist<double>>>
			HashMap<Integer, ArrayList<Integer>> allFoldsStates = new LinkedHashMap<>();
			HashMap<Integer, Integer> forgottenPrematureCases = new HashMap<>();
			HashMap<Integer, Integer> eternalPrematureCases = new HashMap<>();

			for(int k=0; k<numOfFolds; k++) {

				allFoldsCCResults.put(k, new ResultsCollection2());                                       // HashMap<Integer, HashMap<String,ArrayList<Double>>> 
				allFoldsStates.put(k, new ArrayList<>());
				HashMap<String, ArrayList<PartialAlignment>> forgottenWithPredictedMarking = new LinkedHashMap<>();  //to keep track of the wrong prediction decisions
				ArrayList<String> casesWithPredictedMarking = new ArrayList<>();

				replayer.getDataStore().clear();
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

				//Classifier related stuff
				WekaDataSetsCreation wekaDataSetsCreation = null;
				try {
					wekaDataSetsCreation = DeserializeWekaObject(classifiersContainer.classifiersRepositoryPath, classifiersContainer.classifiersRepository.get(k), k);
				} catch (ClassNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
//				if(classifiersContainer.classifiersRepository.containsKey(k)) {
//					wekaDataSetsCreation = classifiersContainer.classifiersRepository.get(k);
//				}else {
//					wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);
//					try {
//						wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
//								wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking));
//						classifiersContainer.classifiersRepository.put(k, wekaDataSetsCreation);
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
				
				
				
				//WekaDataSetsCreation wekaDataSetsCreation = classifiersContainer.foldsclassifiers.get(k);
				
				/*WekaDataSetsCreation wekaDataSetsCreation = new WekaDataSetsCreation(classifierChoice);				
				try {
					Classifier classifier = wekaDataSetsCreation.trainClassifier(context, trainLog, net, iMarking, numOfFeatures, classifiersContainer.endMarkerEnabled, null,
							wekaDataSetsCreation.getReplayer(context, net, iMarking,finalMarking));
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}*/
				//wekaDataSetsCreation.serializeClassifier(numOfFeatures, maxCasesToStore, classifierStorage, k);
				Instances classifiedInstancesOCC = wekaDataSetsCreation.synthesizeTestSet();

				//ArrayList<Triplet<String,String,Date>>	eventStream = ParallelCasesBasedLogToStreamConverter.logToStream(testLog, parallelCases, numOfFeatures, numOfFeatures+2);
				ArrayList<Triplet<String,String,Date>>	eventStream = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(testLog);

				HashMap<String, ArrayList<PartialAlignment>> prematureCasesDuringExecutionList = new HashMap<>();
				HashMap<String, ArrayList<PartialAlignment>> prematureCasesAfterExecutionList = new HashMap<>();
				int prematureCasesDuringExecution=0;   //cases which were forgotten before reaching the desired number of oprhan events
				int prematureCasesAfterExecution=0;     //cases which does not reach the desired number of oprhan events as the stream is finished

				for (Triplet<String,String, Date> entry : eventStream) {

					String caseId = entry.getValue0();
					String event = entry.getValue1();

					//					if(caseId.equals("813")){
					//						System.out.println("catch it");
					//					}

					PartialAlignment<String, Transition, Marking> partialAlignment = null;

					if(replayer.getDataStore().containsKey(caseId)) {

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
									}
									
								}else {
									State parent = replayer.getDataStore().get(caseId).getState();							
									partialAlignment = synthesizePartialAlignment(parent, event);
									replayer.getDataStore().put(caseId, (A)partialAlignment);
								}
								////////
//								State parent = replayer.getDataStore().get(caseId).getState();							
//								partialAlignment = synthesizePartialAlignment(parent, event);
//								replayer.getDataStore().put(caseId, (A)partialAlignment);
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

						if(replayer.getDataStore().size()>=maxCasesToStore) {
							String caseToBeForgotten = ForgettingCases.selectCaseToBeForgotten(replayer.getDataStore(), classifiersContainer.forgettingPolicy);
							System.out.println(caseToBeForgotten);
							recordCaseCosts(k, caseToBeForgotten, allFoldsCCResults, replayer.getDataStore().get(caseToBeForgotten)/*, casesWithPredictedMarking*/);
							
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
					allFoldsStates.get(k).add(StatesCalculator.getNumberOfStatesInMemory(replayer.getDataStore()));

				}

				//recording statistics for a single fold

				for(Entry<String, A> entry: replayer.getDataStore().entrySet()) {  //record costs for cases which survive in the memory after the stream ends
					recordCaseCosts(k, entry.getKey(), allFoldsCCResults, entry.getValue()/*, casesWithPredictedMarking*/);
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
				
				forgottenPrematureCases.put(k, prematureCasesDuringExecution);
				eternalPrematureCases.put(k, prematureCasesAfterExecution);
				//System.out.println("prematureCasesDuringExecution: " + prematureCasesDuringExecution + ",prematureCasesAfterExecution: " + prematureCasesAfterExecution);

//				//SAVE THE TRAINING AND TEST SETS
//
//				wekaDataSetsCreation.saveDataSet(wekaDataSetsCreation.getDatasetMarking(), numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, k, "TrainingSet");
//				wekaDataSetsCreation.saveDataSet(classifiedInstancesOCC, numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, k, "TestSet");
//
//				//				System.out.println(prematureCasesDuringExecutionList.size());
//				//				System.out.println(prematureCasesAfterExecutionList.size());
//				writeRecordsToFile(forgottenWithPredictedMarking, numOfFeatures, maxCasesToStore, classifiersContainer.storageFolder, k);
				wekaDataSetsCreation.setClassifier(null);
			}

			//------------------------------statistics for all the 10 folds of an instance of a variant

//			ResultsCollection2 resultsCollection2 = new ResultsCollection2();
//			resultsCollection2.caseLimitSize = maxCasesToStore;
//			resultsCollection2.featureSize = numOfFeatures;
//
//			int sumForgottenPrematureCases=0;
//			int sumEternalPrematureCases=0;
//			int maxStates = 0;
//
			for(int f=0; f<numOfFolds; f++) {
//				resultsCollection2.costRecords.putAll(allFoldsResults.get(f));
				allFoldsCCResults.get(f).sumOfForgottenPrematureCases = forgottenPrematureCases.get(f);
				allFoldsCCResults.get(f).sumOfEternalPrematureCases = eternalPrematureCases.get(f);
				allFoldsCCResults.get(f).maxStates = Collections.max(allFoldsStates.get(f));
//				if(tempMax > maxStates) {
//					maxStates = tempMax;
//				}
			}
//
//
//			resultsCollection2.maxStates = maxStates;
//			resultsCollection2.sumOfForgottenPrematureCases = sumForgottenPrematureCases;
//			resultsCollection2.sumOfEternalPrematureCases = sumEternalPrematureCases;			
//
//			return resultsCollection2;
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

		//		if(partialAlignment.getState().getStateInModel()==null) {
		//			if(casesWithPredictedMarking.containsKey(caseId)) {
		//				casesWithPredictedMarking.get(caseId).add(partialAlignment);
		//			}else {
		//				ArrayList<PartialAlignment> temp = new ArrayList<>();
		//				temp.add(partialAlignment);
		//				casesWithPredictedMarking.put(caseId, temp);
		//			}
		//		}
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
	//		StringBuilder stringBuilderHeader = new StringBuilder();
	//		stringBuilderHeader.append("Case,");
	//
	//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
	//			stringBuilderHeader.append(entryOuter.getKey() + ",");
	//		}		
	//
	//		//stringBuilderHeader.append("For. Premature Cases, Eter. Premature Cases");
	//		//stringBuilderHeader.append("\n");
	//
	//		StringBuilder stringBuilderCosts = new StringBuilder();
	//		StringBuilder stringBuilderCasesMeta = new StringBuilder();
	//
	//		boolean first = true;
	//
	//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
	//			if(first) {
	//				for(Entry<String, Double> entryInner : entryOuter.getValue().costRecords.entrySet()) {
	//					String caseId = entryInner.getKey();
	//					stringBuilderCosts.append(caseId + ",");				
	//
	//					for(Entry<String, ResultsCollection2> records : globalResults.entrySet()) {
	//						stringBuilderCosts.append(records.getValue().costRecords.get(caseId) + ",");						
	//					}
	//					//stringBuilderCosts.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
	//					//stringBuilderCosts.append(entryOuter.getValue().sumOfEternalPrematureCases);
	//					stringBuilderCosts.append("\n");
	//				}
	//				first=false;				
	//			}			
	//		}	
	//
	//		System.out.println("Combination, Forgotten Premature, Forgotten Eternal");
	//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
	//			stringBuilderCasesMeta.append(entryOuter.getKey() + ",");
	//			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
	//			stringBuilderCasesMeta.append(entryOuter.getValue().sumOfEternalPrematureCases + "\n");
	//		}
	//
	//		System.out.println(stringBuilderCasesMeta.toString());
	//
	//
	//
	//
	//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/in OCC Random Forest/synthetic data/" +  classifierChoice + fileName + ".csv";
	//		File file = new File(outputFilePath);
	//
	//		BufferedWriter bf = null;
	//
	//		if(file.exists() && !file.isDirectory()){
	//			try {
	//				bf = new BufferedWriter(new FileWriter(file, true));
	//				bf.newLine();
	//				bf.write(stringBuilderHeader.toString());
	//				bf.write(stringBuilderCosts.toString());
	//			} catch (IOException e) {
	//				// TODO Auto-generated catch block
	//				e.printStackTrace();
	//			}
	//		}else {
	//			try {
	//				bf = new BufferedWriter(new FileWriter(file));
	//				bf.write(stringBuilderHeader.toString());
	//				bf.newLine();
	//				bf.write(stringBuilderCosts.toString());
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
	//		outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/in OCC Random Forest/synthetic data/" +  classifierChoice + fileName + "_meta.csv";
	//		file = new File(outputFilePath);
	//		BufferedWriter bf2 = null;
	//		try {	
	//			bf2 = new BufferedWriter(new FileWriter(file, true));
	//			bf2.write(stringBuilderCasesMeta.toString());
	//			bf2.flush();
	//			bf2.close();
	//		} catch (IOException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		}
	//
	//		System.out.println("W and N combination, max. states");
	//
	//		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
	//			System.out.println(entryOuter.getKey() + "," + entryOuter.getValue().maxStates);
	//		}
	//	}

	private static ResultsCollection2 aggregateVariantsResults(LinkedHashMap<String, ResultsCollection2> allInstancesResults) {

		ResultsCollection2 consolidatedResults = new ResultsCollection2();

		boolean first = true;
		int caseLimitSize = 0;
		int featureSize = 0;

		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
			if(first) {
				for(Entry<String, Double> entryInner : entryOuter.getValue().costRecords.entrySet()) {
					String caseId = entryInner.getKey();
					Double avgCost = 0.0;					

					for(Entry<String, ResultsCollection2> records : allInstancesResults.entrySet()) {
						avgCost += records.getValue().costRecords.get(caseId);						
					}

					consolidatedResults.costRecords.put(caseId, (double)Math.round(avgCost/allInstancesResults.size()));
				}
				caseLimitSize = entryOuter.getValue().caseLimitSize;
				featureSize = entryOuter.getValue().featureSize;
				first=false;				
			}			
		}
		int avgSumOfForgottenPrematureCases=0;
		int avgOfEternalPrematureCases=0;

		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
			avgSumOfForgottenPrematureCases += entryOuter.getValue().sumOfForgottenPrematureCases;
			avgOfEternalPrematureCases += entryOuter.getValue().sumOfEternalPrematureCases;
		}

		consolidatedResults.sumOfEternalPrematureCases = Math.round(avgOfEternalPrematureCases/allInstancesResults.size());
		consolidatedResults.sumOfForgottenPrematureCases = Math.round(avgSumOfForgottenPrematureCases/allInstancesResults.size());
		consolidatedResults.caseLimitSize = caseLimitSize;
		consolidatedResults.featureSize = featureSize;	

		int maxStatesInAllInstances = 0;

		for(Entry<String, ResultsCollection2> entryOuter : allInstancesResults.entrySet()) {
			//			if(entryOuter.getValue().maxStates > maxStatesInAllInstances) {	
			//				maxStatesInAllInstances = entryOuter.getValue().maxStates;
			//			}
			maxStatesInAllInstances += entryOuter.getValue().maxStates;
		}

		consolidatedResults.maxStates = maxStatesInAllInstances/allInstancesResults.size();

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

	private static void writeRecordsToFile(HashMap<String, ArrayList<PartialAlignment>> casesWithPredictedMarking, int w, int n, String storageFolder, int fold) {

		String outputFilePath = storageFolder + "/" + w + "_" + n + "_" + fold + ".txt";
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
//					FileOutputStream file = new FileOutputStream(new File(path + "/classifiers/" + k + ".txt"));
//					ObjectOutputStream out = new ObjectOutputStream(file);
//					out.writeObject(wekaDataSetsCreation);
//					 out.close();
//			         file.close();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//classifiersRepository.get(featureSizes[featureSize]).add(wekaDataSetsCreation);
				//classifiersRepository.add(wekaDataSetsCreation);
			}
		//}		
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
	
	private ArrayList<String> populateMarkingClasses(String path, String process) throws IOException{
		BufferedReader br = null;
		ArrayList<String> temp = new ArrayList<>();
		try {
			br = new BufferedReader(new FileReader(path + "/" + process + ".txt"));
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
/*		
		PNMLSerializer PNML = new PNMLSerializer();
		NetSystem sys = PNML.parse(netFile);

		//System.err.println(sys.getMarkedPlaces());

		//		int pi, ti;
		//		pi = ti = 1;
		//		for (org.jbpt.petri.Place p : sys.getPlaces())
		//			p.setName("p" + pi++);
		//		for (org.jbpt.petri.Transition t : sys.getTransitions())
		//				t.setName("t" + ti++);

		Petrinet net = PetrinetFactory.newPetrinet(netFile);
		

		
			

		// places
		Map<org.jbpt.petri.Place, Place> p2p = new HashMap<org.jbpt.petri.Place, Place>();
		for (org.jbpt.petri.Place p : sys.getPlaces()) {
			Place pp = net.addPlace(p.toString()); 
			p2p.put(p, pp); 
			System.out.println(p.getName().toString());
		}

		// transitions
		Map<org.jbpt.petri.Transition, Transition> t2t = new HashMap<org.jbpt.petri.Transition, Transition>();
		for (org.jbpt.petri.Transition t : sys.getTransitions()) {
			Transition tt = net.addTransition(t.getLabel()); 
			if (t.isSilent() || t.getLabel().startsWith("tau")) {
				tt.setInvisible(true);
			}
			t2t.put(t, tt);
		}

		// flow
		for (Flow f : sys.getFlow()) {
			if (f.getSource() instanceof org.jbpt.petri.Place) {
				net.addArc(p2p.get(f.getSource()), t2t.get(f.getTarget()));
			} else {
				net.addArc(t2t.get(f.getSource()), p2p.get(f.getTarget()));
			}
		}

		// add unique start node
		if (sys.getSourceNodes().isEmpty()) {
			Place i = net.addPlace("START_P");
			Transition t = net.addTransition("");
			t.setInvisible(true);
			net.addArc(i, t);

			for (org.jbpt.petri.Place p : sys.getMarkedPlaces()) {
				net.addArc(t, p2p.get(p));
			}

		}

		return net;
*/	
	}
	
	private static WekaDataSetsCreation DeserializeWekaObject(String path, WekaDataSetsCreation wekaDataSetsCreation, int fold) throws IOException, ClassNotFoundException {
		
		//WekaDataSetsCreation wekaDataSetsCreation = null;
		
//		FileInputStream file;
//		try {
//			file = new FileInputStream(new File(path + "/classifiers/" + k + ".txt"));
//			ObjectInputStream in = new ObjectInputStream(file);
//			wekaDataSetsCreation = (WekaDataSetsCreation) in.readObject();
//			in.close();
//			file.close();
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		Classifier cls= null;
		try {
			cls = (Classifier) SerializationHelper.read(path + "/classifiers/" + fold + ".model");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		wekaDataSetsCreation.setClassifier(cls);
		
		return wekaDataSetsCreation;        
         
	}

}
