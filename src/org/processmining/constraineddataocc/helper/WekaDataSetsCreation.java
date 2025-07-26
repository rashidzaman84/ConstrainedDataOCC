package org.processmining.constraineddataocc.helper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.parameters.IncrementalReplayerParametersImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.analysis.ShortestPathFactory;
import org.processmining.models.graphbased.directed.analysis.ShortestPathInfo;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.CoverabilityGraph;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;

import dk.klafbang.tools.Pair;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import weka.classifiers.Classifier;
import weka.classifiers.functions.SimpleLogistic;
import weka.classifiers.meta.MultiClassClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSink;


public class WekaDataSetsCreation implements Serializable {


	private Classifier classifier; 		
	private HashMap<String, Marking>classLabelsMapping;
	private Instances datasetMarking;
	

	//private Instances datasetCost;
	private Instances testsetMarking;
	//private Instances testsetCost;
	
	private HashMap<String, Marking> shortInstanceMarking;
		
	public WekaDataSetsCreation(String classifier) {

		this.classLabelsMapping = new HashMap<String, Marking>();
		this.shortInstanceMarking = new HashMap<>();

		switch(classifier){
			case "Random Forest":
				//RandomForest randomForest = new RandomForest();
				//randomForest.setSeed(789789);
				//this.classifier = randomForest;
				this.classifier = new RandomForest();
				break;
			case"MultiClass":
				this.classifier = new MultiClassClassifier();
				break;
			case"Simple Logistic":
				this.classifier = new SimpleLogistic();
				break;
			default:
				System.out.println("Not a valid classifier :(");

		}
	}
	
	public Instances getDatasetMarking() {
		return datasetMarking;
	}
	
	public Classifier getClassifier() {
		return classifier;
	}

	public void setClassifier(Classifier classifier) {
		this.classifier = classifier;
	}
	
	public Marking getMarkingShortInstance(String shortInstance) {
		if(shortInstanceMarking.containsKey(shortInstance)) {
			return shortInstanceMarking.get(shortInstance);
		}else {
			return null;
		}		
	}


	@SuppressWarnings("unused")
	private static void writeSetToFile(final StringBuilder dataset, final String option, final String type, final int numOfFeatures, final String fileName, final String directory) {

		String outputFilePath = directory + fileName + "_" + numOfFeatures + "_" + option +  ".arff";
		File file = new File(outputFilePath);	
		BufferedWriter bf = null;

		try {
			bf = new BufferedWriter(new FileWriter(file));
			bf.write(dataset.toString());
			bf.flush();
			bf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@SuppressWarnings("unchecked")
	public <A extends PartialAlignment<String, Transition, Marking>> Classifier trainClassifier(
			final PluginContext context, XLog log, Petrinet net, Marking iMarking,
			int numOfFeatures, Boolean endMarkerEnabled, ArrayList<String> markingClasses_, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
			IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer) throws IOException{


//		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
//		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = getReplayer(context, net);
		
		replayer.getDataStore().clear();

		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, new XEventNameClassifier());
		ArrayList<XEventClass> eventClasses = sortEventClassesByOccurrence(logInfo.getEventClasses());

		ArrayList<String> eventLabels = new ArrayList<>();
		for(XEventClass eventClass : eventClasses) {
			eventLabels.add(eventClass.getId());
		}
		
		Collections.sort(eventLabels);

		
		ArrayList<String> markingClasses = new ArrayList<>();	
		ArrayList<Instance> instances = new ArrayList<>();
		HashMap<Pair<String, Marking>, Integer> shortInstances = new HashMap<>();

		for(XTrace trace : log) {
						
			for(int x=0; x<trace.size()-numOfFeatures;x++) {					

				double[] valuesMarking = new double[numOfFeatures+1];
				//double[] valuesCost = new double[datasetMarking.numAttributes()];
				int index=0;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						String label = XConceptExtension.instance().extractName(trace.get(y));
						valuesMarking[index] = eventLabels.indexOf(label);
						//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(label);
						index++;
					}else {
						//							markingTrainingSetBody.append("?,");
						//							costTrainingSetBody.append("?,");
						System.out.println("I am having problem with number of attributes");
					}
				}
				
				for(int k=0; k<=x; k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==x) {
							String toString = null;
							Marking marking = partialAlignment.getState().getStateInModel(); 
							toString = "\"" + marking.toString() + "\"";
							
							classLabelsMapping.put(toString, marking);
							
							if(markingClasses.contains(toString)) {
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}else {
								markingClasses.add(toString);
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}

							
							//valuesCost[datasetMarking.numAttributes()-1] = partialAlignment.getCost();
						}
					}	 					
				}
				Instance instanceMarking = new DenseInstance(1.0, valuesMarking);
				//Instance instanceCost = new DenseInstance(1.0, valuesCost);
				instances.add(instanceMarking);
				//datasetCost.add(instanceCost);
				replayer.getDataStore().clear();
			}
						
		
		}
		
		if(endMarkerEnabled) {
			ArrayList<String> caseEndingEvents = getCaseEndingEvents(net);
			
			for(XTrace trace : log) {
				//here we make less than feature size instances
				int x;
				if(trace.size() > numOfFeatures) {
					x = trace.size()-numOfFeatures+1;
				}else if (trace.size() <= numOfFeatures && caseEndingEvents.contains(XConceptExtension.instance().extractName(trace.get(trace.size()-1)))) {
					x = 0;
				}else {
					continue;
				}
				//int x= trace.size() > numOfFeatures ? trace.size()-numOfFeatures+1 : 0;
				for(; x<trace.size(); x++) {
					String instance = "";
					Marking marking = new Marking();
					for(int y=x; y<trace.size() ; y++) {
						if(y<trace.size()-1) {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y)) + ",";
						}else {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y));
						}
						
					}
					
					for(int k=0; k<x; k++) {
						if(k<trace.size()) {
							String caseId = XConceptExtension.instance().extractName(trace);
							String event = XConceptExtension.instance().extractName(trace.get(k));
							PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
							if(k==x-1) {
								
								marking = partialAlignment.getState().getStateInModel();
							}
						}	 					
					}
					
					Pair<String, Marking> shortInstance = Pair.createPair(instance, marking);
					if(shortInstances.containsKey(shortInstance)) {
						shortInstances.put(shortInstance, shortInstances.get(shortInstance)+1);
					}else {
						shortInstances.put(shortInstance, 1);
					}
					replayer.getDataStore().clear();
				}
			}
			
			ArrayList<String> closedRelations = new ArrayList();
			
			//now we refine the short instances on the basis of frequency
			for(Entry<Pair<String, Marking>, Integer> recOuter : shortInstances.entrySet()) {
				
				if(closedRelations.contains(recOuter.getKey().getFirst())){
					continue;
				}
				
				for(Entry<Pair<String, Marking>, Integer> recInner : shortInstances.entrySet()) {
					if(recInner.getKey().getFirst().equals(recOuter.getKey().getFirst())) {
						if(recInner.getValue() > recOuter.getValue()){
							shortInstanceMarking.put(recInner.getKey().getFirst(), recInner.getKey().getSecond());
						}else {
							shortInstanceMarking.put(recOuter.getKey().getFirst(), recOuter.getKey().getSecond());
						}					
					}
				}
				closedRelations.add(recOuter.getKey().getFirst());
			}
			
			System.out.println(shortInstanceMarking);
		}
		
		//create datasets		
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();

		for(int i=1; i<=numOfFeatures; i++) {
			attributes.add(new Attribute("event"+i,eventLabels)); 			
		}

		ArrayList<Attribute> attributesMarking = new ArrayList<Attribute>();
		attributesMarking.addAll(attributes);
		attributesMarking.add(new Attribute("class", markingClasses));
		datasetMarking = new Instances("Train-dataset-marking", attributesMarking, 0);

		attributes.add(new Attribute("class"));

		//datasetCost = new Instances("Train-dataset-cost", attributes, 0);

		testsetMarking = new Instances("Test-dataset-marking", attributesMarking, 0);
		//testsetCost = new Instances("Test-dataset-cost", attributes, 0);
//		int sum = 0;
//		for(XTrace trace : log) {
//			sum += trace.size();
//		}
		//Instances datasetMarkingClone = new Instances(datasetMarking);
		//dummyTestSet(datasetMarking, testsetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/" , 0, "none");
		
		boolean weighted = false;
		if(weighted) {
			HashMap<String, Instance> mapping = new HashMap<>();
			HashMap<String, Integer> instanceCount = new HashMap<>();
			
			for(Instance instance : instances) {
				//System.out.println(instance.toString());
				if(instanceCount.containsKey(instance.toString())) {
					instanceCount.put(instance.toString(), instanceCount.get(instance.toString())+1);
				}else {
					instanceCount.put(instance.toString(), 1);
					mapping.put(instance.toString(), (Instance)instance.copy());
				}
			}
			ArrayList<Instance> weightedInstances = new ArrayList<>();
			//double totalWeight = 0;
			for(Entry<String, Integer> entry : instanceCount.entrySet()) {
				
				mapping.get(entry.getKey()).setWeight(entry.getValue());
				weightedInstances.add(mapping.get(entry.getKey()));
				//totalWeight += entry.getValue();
			}
			datasetMarking.addAll(weightedInstances);
			//System.out.println(totalWeight);
			
		}else {
			datasetMarking.addAll(instances);
		}
		
		datasetMarking.setClassIndex(datasetMarking.numAttributes()-1);
		
		try {
			classifier.buildClassifier(datasetMarking);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//dummyTestSet(datasetMarking, testsetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/" , 0, "none");
		//saveDataSet(datasetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/new/" , 0, "trainingset");
		//datasetMarking.removeAll(instances);
		return classifier;		
		
	}
	
	@SuppressWarnings("unchecked")
	public <A extends PartialAlignment<String, Transition, Marking>> Classifier trainClassifier2(
			final PluginContext context, XLog log, Petrinet net, Marking iMarking,
			int numOfFeatures, Boolean endMarkerEnabled, ArrayList<String> markingClasses_, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
			IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer) throws IOException{


//		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
//		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = getReplayer(context, net);
		
		replayer.getDataStore().clear();

		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, new XEventNameClassifier());
		ArrayList<XEventClass> eventClasses = sortEventClassesByOccurrence(logInfo.getEventClasses());

		ArrayList<String> eventLabels = new ArrayList<>();
		for(XEventClass eventClass : eventClasses) {
			eventLabels.add(eventClass.getId());
		}
		
		Collections.sort(eventLabels);

		
		ArrayList<String> markingClasses = new ArrayList<>();	
		ArrayList<Instance> instances = new ArrayList<>();
		HashMap<Pair<String, Marking>, Integer> shortInstances = new HashMap<>();

		for(XTrace trace : log) {
						
			for(int x=0; x<trace.size()-numOfFeatures;x++) {					

				double[] valuesMarking = new double[numOfFeatures+1];
				//double[] valuesCost = new double[datasetMarking.numAttributes()];
				int index=0;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						String label = XConceptExtension.instance().extractName(trace.get(y));
						valuesMarking[index] = eventLabels.indexOf(label);
						//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(label);
						index++;
					}else {
						//							markingTrainingSetBody.append("?,");
						//							costTrainingSetBody.append("?,");
						System.out.println("I am having problem with number of attributes");
					}
				}
				
				for(int k=0; k<=x; k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==x) {
							String toString = null;
							Marking marking = partialAlignment.getState().getStateInModel(); 
							toString = "\"" + marking.toString() + "\"";
							
							classLabelsMapping.put(toString, marking);
							
							if(markingClasses.contains(toString)) {
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}else {
								markingClasses.add(toString);
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}

							
							//valuesCost[datasetMarking.numAttributes()-1] = partialAlignment.getCost();
						}
					}	 					
				}
				Instance instanceMarking = new DenseInstance(1.0, valuesMarking);
				//Instance instanceCost = new DenseInstance(1.0, valuesCost);
				instances.add(instanceMarking);
				//datasetCost.add(instanceCost);
				replayer.getDataStore().clear();
			}
						
		
		}
		
		if(endMarkerEnabled) {
			ArrayList<String> caseEndingEvents = getCaseEndingEvents(net);
			
			for(XTrace trace : log) {
				//here we make less than feature size instances
				int x;
				if(trace.size() > numOfFeatures) {
					x = trace.size()-numOfFeatures+1;
				}else if (trace.size() <= numOfFeatures && caseEndingEvents.contains(XConceptExtension.instance().extractName(trace.get(trace.size()-1)))) {
					x = 0;
				}else {
					continue;
				}
				//int x= trace.size() > numOfFeatures ? trace.size()-numOfFeatures+1 : 0;
				for(; x<trace.size(); x++) {
					String instance = "";
					Marking marking = new Marking();
					for(int y=x; y<trace.size() ; y++) {
						if(y<trace.size()-1) {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y)) + ",";
						}else {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y));
						}
						
					}
					
					for(int k=0; k<x; k++) {
						if(k<trace.size()) {
							String caseId = XConceptExtension.instance().extractName(trace);
							String event = XConceptExtension.instance().extractName(trace.get(k));
							PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
							if(k==x-1) {
								
								marking = partialAlignment.getState().getStateInModel();
							}
						}	 					
					}
					
					Pair<String, Marking> shortInstance = Pair.createPair(instance, marking);
					if(shortInstances.containsKey(shortInstance)) {
						shortInstances.put(shortInstance, shortInstances.get(shortInstance)+1);
					}else {
						shortInstances.put(shortInstance, 1);
					}
					replayer.getDataStore().clear();
				}
			}
			
			ArrayList<String> closedRelations = new ArrayList();
			
			//now we refine the short instances on the basis of frequency
			for(Entry<Pair<String, Marking>, Integer> recOuter : shortInstances.entrySet()) {
				
				if(closedRelations.contains(recOuter.getKey().getFirst())){
					continue;
				}
				
				for(Entry<Pair<String, Marking>, Integer> recInner : shortInstances.entrySet()) {
					if(recInner.getKey().getFirst().equals(recOuter.getKey().getFirst())) {
						if(recInner.getValue() > recOuter.getValue()){
							shortInstanceMarking.put(recInner.getKey().getFirst(), recInner.getKey().getSecond());
						}else {
							shortInstanceMarking.put(recOuter.getKey().getFirst(), recOuter.getKey().getSecond());
						}					
					}
				}
				closedRelations.add(recOuter.getKey().getFirst());
			}
			
			System.out.println(shortInstanceMarking);
		}
		
		//create datasets		
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();

		for(int i=1; i<=numOfFeatures; i++) {
			attributes.add(new Attribute("event"+i,eventLabels)); 			
		}

		ArrayList<Attribute> attributesMarking = new ArrayList<Attribute>();
		attributesMarking.addAll(attributes);
		attributesMarking.add(new Attribute("class", markingClasses));
		datasetMarking = new Instances("Train-dataset-marking", attributesMarking, 0);

		attributes.add(new Attribute("class"));

		//datasetCost = new Instances("Train-dataset-cost", attributes, 0);

		testsetMarking = new Instances("Test-dataset-marking", attributesMarking, 0);
		//testsetCost = new Instances("Test-dataset-cost", attributes, 0);
//		int sum = 0;
//		for(XTrace trace : log) {
//			sum += trace.size();
//		}
		//Instances datasetMarkingClone = new Instances(datasetMarking);
		//dummyTestSet(datasetMarking, testsetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/" , 0, "none");
		
		boolean weighted = false;
		if(weighted) {
			HashMap<String, Instance> mapping = new HashMap<>();
			HashMap<String, Integer> instanceCount = new HashMap<>();
			
			for(Instance instance : instances) {
				//System.out.println(instance.toString());
				if(instanceCount.containsKey(instance.toString())) {
					instanceCount.put(instance.toString(), instanceCount.get(instance.toString())+1);
				}else {
					instanceCount.put(instance.toString(), 1);
					mapping.put(instance.toString(), (Instance)instance.copy());
				}
			}
			ArrayList<Instance> weightedInstances = new ArrayList<>();
			//double totalWeight = 0;
			for(Entry<String, Integer> entry : instanceCount.entrySet()) {
				
				mapping.get(entry.getKey()).setWeight(entry.getValue());
				weightedInstances.add(mapping.get(entry.getKey()));
				//totalWeight += entry.getValue();
			}
			datasetMarking.addAll(weightedInstances);
			//System.out.println(totalWeight);
			
		}else {
			datasetMarking.addAll(instances);
		}
		
		datasetMarking.setClassIndex(datasetMarking.numAttributes()-1);
		
		try {
			classifier.buildClassifier(datasetMarking);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//dummyTestSet(datasetMarking, testsetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/" , 0, "none");
		saveDataSet(datasetMarking, numOfFeatures, 1000000, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Information Systems/Class balancing/BPIC12 Training set/new/" , 0, "testset_populated");
		//datasetMarking.removeAll(instances);
		return classifier;		
		
	}
	
	@SuppressWarnings("unchecked")
	public <A extends PartialAlignment<String, Transition, Marking>> Classifier trainClassifier(
			final PluginContext context, XLog log, Petrinet net, Marking iMarking,
			int numOfFeatures, Boolean endMarkerEnabled, ArrayList<String> markingClasses_, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
			IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, String relation) throws IOException{



		if(relation.equals("parent")) {
			return trainClassifier(context, log, net, iMarking, numOfFeatures, endMarkerEnabled, markingClasses_, replayer);
		}else if (!relation.equals("reachable")) {
			System.out.println("Some unkonow marking selection");
			return null;
		}else {		
		
		replayer.getDataStore().clear();

		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, new XEventNameClassifier());
		ArrayList<XEventClass> eventClasses = sortEventClassesByOccurrence(logInfo.getEventClasses());

		ArrayList<String> eventLabels = new ArrayList<>();
		for(XEventClass eventClass : eventClasses) {
			eventLabels.add(eventClass.getId());
		}
		
		Collections.sort(eventLabels);

		
		ArrayList<String> markingClasses = new ArrayList<>();	
		ArrayList<Instance> instances = new ArrayList<>();
		HashMap<Pair<String, Marking>, Integer> shortInstances = new HashMap<>();

		for(XTrace trace : log) {
						
			for(int x=0; x<trace.size()-numOfFeatures;x++) {					

				double[] valuesMarking = new double[numOfFeatures+1];
				//double[] valuesCost = new double[datasetMarking.numAttributes()];
				int index=0;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						String label = XConceptExtension.instance().extractName(trace.get(y));
						valuesMarking[index] = eventLabels.indexOf(label);
						//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(label);
						index++;
					}else {
						//							markingTrainingSetBody.append("?,");
						//							costTrainingSetBody.append("?,");
						System.out.println("I am having problem with number of attributes");
					}
				}
				
				for(int k=0; k<=(numOfFeatures+x); k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==(numOfFeatures+x)) {
							String toString = null;
							Marking marking = partialAlignment.getState().getStateInModel(); 
							toString = "\"" + marking.toString() + "\"";
							
							classLabelsMapping.put(toString, marking);
							
							if(markingClasses.contains(toString)) {
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}else {
								markingClasses.add(toString);
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}

							
							//valuesCost[datasetMarking.numAttributes()-1] = partialAlignment.getCost();
						}
					}	 					
				}
				Instance instanceMarking = new DenseInstance(1.0, valuesMarking);
				//Instance instanceCost = new DenseInstance(1.0, valuesCost);
				instances.add(instanceMarking);
				//datasetCost.add(instanceCost);
				replayer.getDataStore().clear();
			}
						
		
		}
		
		if(endMarkerEnabled) {
			ArrayList<String> caseEndingEvents = getCaseEndingEvents(net);
			
			for(XTrace trace : log) {
				//here we make less than feature size instances
				int x;
				if(trace.size() > numOfFeatures) {
					x = trace.size()-numOfFeatures+1;
				}else if (trace.size() <= numOfFeatures && caseEndingEvents.contains(XConceptExtension.instance().extractName(trace.get(trace.size()-1)))) {
					x = 0;
				}else {
					continue;
				}
				//int x= trace.size() > numOfFeatures ? trace.size()-numOfFeatures+1 : 0;
				for(; x<trace.size(); x++) {
					String instance = "";
					Marking marking = new Marking();
					for(int y=x; y<trace.size() ; y++) {
						if(y<trace.size()-1) {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y)) + ",";
						}else {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y));
						}
						
					}
					
					for(int k=0; k<trace.size(); k++) {
						
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==trace.size()-1) {	
							marking = partialAlignment.getState().getStateInModel();
						}						 					
					}
					
					Pair<String, Marking> shortInstance = Pair.createPair(instance, marking);
					if(shortInstances.containsKey(shortInstance)) {
						shortInstances.put(shortInstance, shortInstances.get(shortInstance)+1);
					}else {
						shortInstances.put(shortInstance, 1);
					}
					replayer.getDataStore().clear();
				}
			}
			
			ArrayList<String> closedRelations = new ArrayList();
			
			//now we refine the short instances on the basis of frequency
			for(Entry<Pair<String, Marking>, Integer> recOuter : shortInstances.entrySet()) {
				
				if(closedRelations.contains(recOuter.getKey().getFirst())){
					continue;
				}
				
				for(Entry<Pair<String, Marking>, Integer> recInner : shortInstances.entrySet()) {
					if(recInner.getKey().getFirst().equals(recOuter.getKey().getFirst())) {
						if(recInner.getValue() > recOuter.getValue()){
							shortInstanceMarking.put(recInner.getKey().getFirst(), recInner.getKey().getSecond());
						}else {
							shortInstanceMarking.put(recOuter.getKey().getFirst(), recOuter.getKey().getSecond());
						}					
					}
				}
				closedRelations.add(recOuter.getKey().getFirst());
			}
			
			System.out.println(shortInstanceMarking);
		}
		
		//create datasets		
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();

		for(int i=1; i<=numOfFeatures; i++) {
			attributes.add(new Attribute("event"+i,eventLabels)); 			
		}

		ArrayList<Attribute> attributesMarking = new ArrayList<Attribute>();
		attributesMarking.addAll(attributes);
		attributesMarking.add(new Attribute("class", markingClasses));
		datasetMarking = new Instances("Train-dataset-marking", attributesMarking, 0);

		attributes.add(new Attribute("class"));

		//datasetCost = new Instances("Train-dataset-cost", attributes, 0);

		testsetMarking = new Instances("Test-dataset-marking", attributesMarking, 0);
		//testsetCost = new Instances("Test-dataset-cost", attributes, 0);
//		int sum = 0;
//		for(XTrace trace : log) {
//			sum += trace.size();
//		}
		//Instances datasetMarkingClone = new Instances(datasetMarking);
		boolean weighted = true;
		if(weighted) {
			HashMap<String, Instance> mapping = new HashMap<>();
			HashMap<String, Integer> instanceCount = new HashMap<>();
			
			for(Instance instance : instances) {
				//System.out.println(instance.toString());
				if(instanceCount.containsKey(instance.toString())) {
					instanceCount.put(instance.toString(), instanceCount.get(instance.toString())+1);
				}else {
					instanceCount.put(instance.toString(), 1);
					mapping.put(instance.toString(), (Instance)instance.copy());
				}
			}
			ArrayList<Instance> weightedInstances = new ArrayList<>();
			//double totalWeight = 0;
			for(Entry<String, Integer> entry : instanceCount.entrySet()) {
				
				mapping.get(entry.getKey()).setWeight(entry.getValue());
				weightedInstances.add(mapping.get(entry.getKey()));
				//totalWeight += entry.getValue();
			}
			datasetMarking.addAll(weightedInstances);
			//System.out.println(totalWeight);
			
		}else {
			datasetMarking.addAll(instances);
		}
		
		datasetMarking.setClassIndex(datasetMarking.numAttributes()-1);
		
		try {
			classifier.buildClassifier(datasetMarking);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//datasetMarking.removeAll(instances);
		return classifier;		
		}
		
		
	}
	
	@SuppressWarnings("unchecked")
	public <A extends PartialAlignment<String, Transition, Marking>> Classifier trainClassifier2(
			final PluginContext context, XLog log, Petrinet net, Marking iMarking,
			int numOfFeatures, Boolean endMarkerEnabled, ArrayList<String> markingClasses_, 
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
			IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, String relation) throws IOException{



		if(relation.equals("parent")) {
			return trainClassifier2(context, log, net, iMarking, numOfFeatures, endMarkerEnabled, markingClasses_, replayer);
		}else if (!relation.equals("reachable")) {
			System.out.println("Some unknown marking selection");
			return null;
		}else {		
		
		replayer.getDataStore().clear();

		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, new XEventNameClassifier());
		ArrayList<XEventClass> eventClasses = sortEventClassesByOccurrence(logInfo.getEventClasses());

		ArrayList<String> eventLabels = new ArrayList<>();
		for(XEventClass eventClass : eventClasses) {
			eventLabels.add(eventClass.getId());
		}
		
		Collections.sort(eventLabels);

		
		ArrayList<String> markingClasses = new ArrayList<>();	
		ArrayList<Instance> instances = new ArrayList<>();
		HashMap<Pair<String, Marking>, Integer> shortInstances = new HashMap<>();

		for(XTrace trace : log) {
						
			for(int x=0; x<trace.size()-numOfFeatures;x++) {					

				double[] valuesMarking = new double[numOfFeatures+1];
				//double[] valuesCost = new double[datasetMarking.numAttributes()];
				int index=0;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						String label = XConceptExtension.instance().extractName(trace.get(y));
						valuesMarking[index] = eventLabels.indexOf(label);
						//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(label);
						index++;
					}else {
						//							markingTrainingSetBody.append("?,");
						//							costTrainingSetBody.append("?,");
						System.out.println("I am having problem with number of attributes");
					}
				}
				
				for(int k=0; k<=(numOfFeatures+x); k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==(numOfFeatures+x)) {
							String toString = null;
							Marking marking = partialAlignment.getState().getStateInModel(); 
							toString = "\"" + marking.toString() + "\"";
							
							classLabelsMapping.put(toString, marking);
							
							if(markingClasses.contains(toString)) {
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}else {
								markingClasses.add(toString);
								valuesMarking[numOfFeatures] = markingClasses.indexOf(toString);
							}

							
							//valuesCost[datasetMarking.numAttributes()-1] = partialAlignment.getCost();
						}
					}	 					
				}
				Instance instanceMarking = new DenseInstance(1.0, valuesMarking);
				//Instance instanceCost = new DenseInstance(1.0, valuesCost);
				instances.add(instanceMarking);
				//datasetCost.add(instanceCost);
				replayer.getDataStore().clear();
			}
						
		
		}
		
		if(endMarkerEnabled) {
			ArrayList<String> caseEndingEvents = getCaseEndingEvents(net);
			
			for(XTrace trace : log) {
				//here we make less than feature size instances
				int x;
				if(trace.size() > numOfFeatures) {
					x = trace.size()-numOfFeatures+1;
				}else if (trace.size() <= numOfFeatures && caseEndingEvents.contains(XConceptExtension.instance().extractName(trace.get(trace.size()-1)))) {
					x = 0;
				}else {
					continue;
				}
				//int x= trace.size() > numOfFeatures ? trace.size()-numOfFeatures+1 : 0;
				for(; x<trace.size(); x++) {
					String instance = "";
					Marking marking = new Marking();
					for(int y=x; y<trace.size() ; y++) {
						if(y<trace.size()-1) {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y)) + ",";
						}else {
							instance = instance + XConceptExtension.instance().extractName(trace.get(y));
						}
						
					}
					
					for(int k=0; k<trace.size(); k++) {
						
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);
						if(k==trace.size()-1) {	
							marking = partialAlignment.getState().getStateInModel();
						}						 					
					}
					
					Pair<String, Marking> shortInstance = Pair.createPair(instance, marking);
					if(shortInstances.containsKey(shortInstance)) {
						shortInstances.put(shortInstance, shortInstances.get(shortInstance)+1);
					}else {
						shortInstances.put(shortInstance, 1);
					}
					replayer.getDataStore().clear();
				}
			}
			
			ArrayList<String> closedRelations = new ArrayList();
			
			//now we refine the short instances on the basis of frequency
			for(Entry<Pair<String, Marking>, Integer> recOuter : shortInstances.entrySet()) {
				
				if(closedRelations.contains(recOuter.getKey().getFirst())){
					continue;
				}
				
				for(Entry<Pair<String, Marking>, Integer> recInner : shortInstances.entrySet()) {
					if(recInner.getKey().getFirst().equals(recOuter.getKey().getFirst())) {
						if(recInner.getValue() > recOuter.getValue()){
							shortInstanceMarking.put(recInner.getKey().getFirst(), recInner.getKey().getSecond());
						}else {
							shortInstanceMarking.put(recOuter.getKey().getFirst(), recOuter.getKey().getSecond());
						}					
					}
				}
				closedRelations.add(recOuter.getKey().getFirst());
			}
			
			System.out.println(shortInstanceMarking);
		}
		
		//create datasets		
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();

		for(int i=1; i<=numOfFeatures; i++) {
			attributes.add(new Attribute("event"+i,eventLabels)); 			
		}

		ArrayList<Attribute> attributesMarking = new ArrayList<Attribute>();
		attributesMarking.addAll(attributes);
		attributesMarking.add(new Attribute("class", markingClasses));
		datasetMarking = new Instances("Train-dataset-marking", attributesMarking, 0);

		attributes.add(new Attribute("class"));

		//datasetCost = new Instances("Train-dataset-cost", attributes, 0);

		testsetMarking = new Instances("Test-dataset-marking", attributesMarking, 0);
		//testsetCost = new Instances("Test-dataset-cost", attributes, 0);
//		int sum = 0;
//		for(XTrace trace : log) {
//			sum += trace.size();
//		}
		//Instances datasetMarkingClone = new Instances(datasetMarking);
		boolean weighted = true;
		if(weighted) {
			HashMap<String, Instance> mapping = new HashMap<>();
			HashMap<String, Integer> instanceCount = new HashMap<>();
			
			for(Instance instance : instances) {
				//System.out.println(instance.toString());
				if(instanceCount.containsKey(instance.toString())) {
					instanceCount.put(instance.toString(), instanceCount.get(instance.toString())+1);
				}else {
					instanceCount.put(instance.toString(), 1);
					mapping.put(instance.toString(), (Instance)instance.copy());
				}
			}
			ArrayList<Instance> weightedInstances = new ArrayList<>();
			//double totalWeight = 0;
			for(Entry<String, Integer> entry : instanceCount.entrySet()) {
				
				mapping.get(entry.getKey()).setWeight(entry.getValue());
				weightedInstances.add(mapping.get(entry.getKey()));
				//totalWeight += entry.getValue();
			}
			datasetMarking.addAll(weightedInstances);
			//System.out.println(totalWeight);
			
		}else {
			datasetMarking.addAll(instances);
		}
		
		datasetMarking.setClassIndex(datasetMarking.numAttributes()-1);
		
		try {
			classifier.buildClassifier(datasetMarking);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//datasetMarking.removeAll(instances);
		return classifier;		
		}
		
		
	}
	
	public <A extends PartialAlignment<String, Transition, Marking>> Classifier trainClassifier(
			final PluginContext context, XLog log, Petrinet net, Marking initialMarking, Marking finalMarking,
			int numOfFeatures, Boolean endMarkerEnabled, ArrayList<String> markingClasses_) throws IOException{


		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = getReplayer(context, net, initialMarking, finalMarking);
		return trainClassifier(context, log, net, initialMarking, numOfFeatures, endMarkerEnabled, markingClasses_, replayer);
		
		
	}
	

	
	private static ArrayList<String> getCaseEndingEvents(final Petrinet net){

		ArrayList<String> temp = new ArrayList<>();

		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition tran: net.getTransitions()) {
			if ((tran.getVisibleSuccessors()).isEmpty()){
				temp.add(tran.toString());
			}
		}
		return temp;
	}
	
		
	
	public void serializeClassifier(int numOfFeatures, int maxCasesToStore, ClassifiersContainer classifierStorage, int fold){
	
		Instances header = new Instances(datasetMarking, 0);
		
		try {
			SerializationHelper.writeAll(classifierStorage.storageFolder + "/" + numOfFeatures + "_" + maxCasesToStore + "_" + fold + ".model", new Object[]{classifier, header});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void serializeClassifier(String path, int fold){
		
		//Instances header = new Instances(datasetMarking, 0);
		
		try {
			//SerializationHelper.writeAll(path + "/classifiers/" + fold + ".model", new Object[]{classifier, header});
			SerializationHelper.write(path + "/classifiers/" + fold + ".model", classifier);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		classifier = null;
	}
	
	public void saveDataSet(Instances dataset, int numOfFeatures, int maxCasesToStore, String storageFolder , int fold, String type) {
		try {
			DataSink.write(storageFolder + "/" + numOfFeatures + "_" + maxCasesToStore + "_" + fold + "_" + type + ".arff", dataset);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>,
	IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> getReplayer(final PluginContext context, final Petrinet net, final Marking initialMarking, final Marking finalMarking) throws IOException{
		Map<Transition, String> modelElementsToLabelMap = new HashMap<>();
		Map<String, Collection<Transition>> labelsToModelElementsMap = new HashMap<>();
		TObjectDoubleMap<Transition> modelMoveCosts = new TObjectDoubleHashMap<>();
		TObjectDoubleMap<String> labelMoveCosts = new TObjectDoubleHashMap<>();

		//Marking initialMarking = getInitialMarking(net);
		//Marking finalMarking = getFinalMarking(net);

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

		return applyGeneric(context, net, initialMarking, finalMarking, parameters);

	}
	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment<String, Transition, Marking>> IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, 
	IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> applyGeneric(final PluginContext context,
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final XLog log,*/ final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters) throws IOException {
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
		return replayer;
	}

	



	private static ArrayList<XEventClass> sortEventClassesByOccurrence(XEventClasses eventClasses) {
		ArrayList<XEventClass> sortedEvents = new ArrayList<XEventClass>();
		for (XEventClass event : eventClasses.getClasses()) {
			boolean inserted = false;
			XEventClass current = null;
			for (int i = 0; i < sortedEvents.size(); i++) {
				current = sortedEvents.get(i);
				if (current.size() <= event.size()) {
					// insert at correct position and set marker
					sortedEvents.add(i, event);
					inserted = true;
					break;
				}
			}
			if (inserted == false) {
				// append to end of list
				sortedEvents.add(event);
			}
		}
		return sortedEvents;
	}

	private ArrayList<String> populateMarkingClasses(final UIPluginContext context,final Petrinet model, final Marking initialMarking) {

		ArrayList<String> markingList = new ArrayList<>();

		CoverabilityGraph coverabilityGraphUnfolded2 = null;
		try {
			Coverability cg = new Coverability();				
			coverabilityGraphUnfolded2 = cg.petriNetToCoverabilityGraph(context, model, getInitialMarking(model));			
		} catch (ConnectionCannotBeObtained e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	

		ShortestPathInfo<State, org.processmining.models.graphbased.directed.transitionsystem.Transition> shortestPathCalculatorUnfolded = ShortestPathFactory.calculateAllShortestDistanceDijkstra(coverabilityGraphUnfolded2);
		//shortestPathCalculatorUnfoldedCopy = ShortestPathFactory.calculateAllShortestDistanceDijkstra(coverabilityGraphUnfolded);
		// populate min/max relations BEFORE from unfolded model
		State startState = null;

		for (Object s : coverabilityGraphUnfolded2.getStates()) {
			if (coverabilityGraphUnfolded2.getInEdges(coverabilityGraphUnfolded2.getNode(s)).isEmpty()) {
				startState = coverabilityGraphUnfolded2.getNode(s);
				break;
			}
		}

		HashSet<Marking> markings = new HashSet<>();

		for (State s : coverabilityGraphUnfolded2.getNodes()) {
			//System.out.println("Target State is : " + s.toString());
			Marking  test = (Marking) s.getIdentifier();
			markings.add(test);						
		}		

		for(Marking mar : markings) {			
			markingList.add("\"" + mar.toString() + "\"");
			//markingList.add(mar.toString());	
			classLabelsMapping.put("\"" + mar.toString() + "\"", mar);
		}
		return markingList;
	}

	public Instances synthesizeTestSet(ArrayList<String> attribs) {

		Instances tempTestsetMarking = new Instances(testsetMarking);
		//Instances tempTestsetCost = new Instances(testsetCost);

		double[] valuesMarking = new double[datasetMarking.numAttributes()];
		//double[] valuesCost = new double[datasetMarking.numAttributes()];
		int index=0;
		for(String attributeValue : attribs) {

			valuesMarking[index] = datasetMarking.attribute(0).indexOfValue(attributeValue);
			//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(attributeValue);
			index++;
		}
		
		Instance instanceMarking = new DenseInstance(1.0, valuesMarking);		            
		//Instance instanceCost = new DenseInstance(1.0, valuesCost);
		
		tempTestsetMarking.add(instanceMarking);
		//tempTestsetCost.add(instanceCost);


		tempTestsetMarking.setClassIndex(tempTestsetMarking.numAttributes()-1);
		//tempTestsetCost.setClassIndex(tempTestsetCost.numAttributes()-1);

		tempTestsetMarking.get(0).setClassMissing();
		
		return tempTestsetMarking;
	}
	
	public Instances synthesizeTestSetWithMissingValues(ArrayList<String> attribs) {

		Instances tempTestsetMarking = new Instances(testsetMarking);
		//Instances tempTestsetCost = new Instances(testsetCost);

		double[] valuesMarking = new double[datasetMarking.numAttributes()];
		//double[] valuesCost = new double[datasetMarking.numAttributes()];
		int index=0;
		for(String attributeValue : attribs) {

			valuesMarking[index] = datasetMarking.attribute(0).indexOfValue(attributeValue);
			//valuesCost[index] = datasetMarking.attribute(0).indexOfValue(attributeValue);
			index++;
		}

		//valuesMarking[datasetMarking.numAttributes()-1] = "?";
		//valuesCost[datasetMarking.numAttributes()-1] = partialAlignment.getCost();
	
		Instance instanceMarking = new DenseInstance(1.0, valuesMarking);
	         
		//Instance instanceCost = new DenseInstance(1.0, valuesCost);
		
		
		
		int count = 0;
		for(String attributeValue : attribs) {
			if (attributeValue.equals("?")) {
				instanceMarking.setMissing(count);
				//instanceCost.setMissing(count);
			}
			count++;
		}

		tempTestsetMarking.add(instanceMarking);
		//tempTestsetCost.add(instanceCost);


		tempTestsetMarking.setClassIndex(tempTestsetMarking.numAttributes()-1);
		//tempTestsetCost.setClassIndex(tempTestsetCost.numAttributes()-1);

		tempTestsetMarking.get(0).setClassMissing();
		

		return tempTestsetMarking;
	}
	
	public Instances synthesizeTestSet() {
		Instances tempTestsetMarking = new Instances(testsetMarking);
		tempTestsetMarking.setClassIndex(tempTestsetMarking.numAttributes()-1);
		return tempTestsetMarking;
	}

	public Marking getMarking(String classValue) {
		return classLabelsMapping.get(classValue);
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
	
	public void dummyTestSet(Instances dataset, Instances testset, int numOfFeatures, int maxCasesToStore, String storageFolder , int fold, String type) {
		
		
		HashSet<Instance> temp = new HashSet<>();
		ArrayList<String> entries = new ArrayList<>();
		
		for(Instance inst : dataset) {
			inst.setClassMissing();
			if(entries.contains(inst.toString())) {
				continue;
			}else {
				entries.add(inst.toString());
				temp.add(inst);
			}
			
		
		}
		
		testset.addAll(temp);
		testset.setClassIndex(testset.numAttributes()-1);
		
		//saveDataSet(testset, numOfFeatures, 1000000, storageFolder , 0, "test");
		for(Instance testInst : testset) {
			double classValue;
			try {
				classValue = classifier.classifyInstance(testInst);
				Marking predictedMarking = getMarking(testInst.classAttribute().value((int) classValue));
				testInst.setClassValue(classValue);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		System.out.println(testset);
	}
}