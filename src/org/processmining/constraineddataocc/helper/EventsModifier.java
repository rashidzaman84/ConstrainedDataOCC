package org.processmining.constraineddataocc.helper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.extension.XExtensionManager;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XOrganizationalExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryBufferedImpl;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.models.IncrementalReplayResult;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.parameters.IncrementalReplayerParametersImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.lpm.util.BringLifecycleToEventName;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.Move;
import org.processmining.plugins.log.exporting.ExportLogXes;

import gnu.trove.map.TObjectDoubleMap;

//@Plugin(name = "Compute Prefix Alignments Incrementally - With Bounded States and Windows", parameterLabels = {"Model", "Event Data" }, returnLabels = { "Replay Result" }, returnTypes = { IncrementalReplayResult.class })
@Plugin(name = "00000 Modify Event Lables", parameterLabels = { }, 
returnLabels = {"Event Data"  }, returnTypes = { XLog.class }, 
help = "with context information as features.")

public class EventsModifier {
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "Compute Prefix Alignments Incrementally", requiredParameterLabels = {})
	
	public XLog apply(final UIPluginContext context) throws IOException {
		
		String[] variant = {"a12", "a22", "a32", "a42"};
		
		int variantInFocus = -1;
		
		File folder = new File("D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/aXfYnZ");	

		for (File file : folder.listFiles()) {
			if (!file.isDirectory()) {
				System.out.println(file.getName());
				BringLifecycleToEventName bLifecycleToEventName = new BringLifecycleToEventName();
				XLog log = null;
				String logFile = folder + "/" + file.getName();
				try {
					log = new XUniversalParser().parse(new File(logFile)).iterator().next();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				XLog modifiedLog =  bLifecycleToEventName.run(context, log);
				
				if(file.getName().startsWith("a12")) {
					variantInFocus = 0;
				}else if (file.getName().startsWith("a22")) {
					variantInFocus = 1;
				}else if (file.getName().startsWith("a32")) {
					variantInFocus = 2;
				}else if (file.getName().startsWith("a42")) {
					variantInFocus = 3;
				}
				
				File toSave = new File("D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/" + variant[variantInFocus] + "/" + file.getName());
				
				ExportLogXes.export(modifiedLog, toSave);
				
				//writeResultsToFile(modifiedLog, file.getName(), variant[variantInFocus]);
			}
		}
		return null;
	}
	
	@UITopiaVariant(author = "S.J. van Zelst", email = "s.j.v.zelst@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(variantLabel = "Compute Prefix Alignments Incrementally", requiredParameterLabels = {0})
	
	public static XLog addFeatures(XLog log) {
		for(int k=0; k<log.size(); k++) {
			for(int i=0; i<log.get(k).size(); i++) {
				HashMap<String, String> features = new HashMap<>();
				if(i==0) {
					features.put("position", "first");
				}else if(i==log.get(k).size()-1) {
					features.put("position", "last");
				}else {
					features.put("position", "intermediate");
				}
				//features.put("caseIndex", Integer.toString(k));
				features.put("eventIndex", Integer.toString(i));
				//features.put("orphan", "unknown");
				features.put("caseid", XConceptExtension.instance().extractName(log.get(k)));
				addFeatures(features, log.get(k).get(i));
			}
		}
		return log;
	}
	
	public static XEvent addFeatures(HashMap<String, String> features, XEvent event) {
		XFactory xesFactory = new XFactoryBufferedImpl();
		XExtensionManager xesExtensionManager = XExtensionManager.instance();
		for(Entry<String, String> entry : features.entrySet()) {
			event.getAttributes().put(entry.getKey(), xesFactory.createAttributeLiteral(
					entry.getKey(),
					entry.getValue(),
					xesExtensionManager.getByName(entry.getKey())));
		}
		
		return event;
	}
	
	public static XEvent updateEventStats(XEvent event, Double cost, String orphan) {
		XFactory xesFactory = new XFactoryBufferedImpl();
		XExtensionManager xesExtensionManager = XExtensionManager.instance();
		event.getAttributes().put("fcost", xesFactory.createAttributeLiteral(
				"fcost",
				cost.toString(),
				xesExtensionManager.getByName("fcost")));
		event.getAttributes().put("orphan", xesFactory.createAttributeLiteral(
				"orphan",
				orphan,
				xesExtensionManager.getByName("orphan")));
		
		return event;
	}
	
	public static XLog updateLog(ArrayList<XEvent> eventStream, XLog enrichedTestLog) {
	
	//XLog updatedLog = (XLog) testLog.clone();
	for(XEvent event : eventStream) {
		String caseIdentifier = event.getAttributes().get("caseid").toString();
		//Integer.parseInt(event.getAttributes().get("caseIndex").toString())
		for(XTrace trace : enrichedTestLog) {
			if(XConceptExtension.instance().extractName(trace).equals(caseIdentifier)) {
				trace.set(Integer.parseInt(event.getAttributes().get("eventIndex").toString()), event);
				break;
			}
		}
		//enrichedTestLog.get(Integer.parseInt(event.getAttributes().get("caseIndex").toString())).set(Integer.parseInt(event.getAttributes().get("eventIndex").toString()), event);
		
	}
	
	//System.out.println(testLog.size());
	//System.out.println(updatedLog.size());
	for(int i =0; i<enrichedTestLog.size(); i++) {
		for (int k=0; k<enrichedTestLog.get(i).size(); k++) {
			String event1 = XConceptExtension.instance().extractName(enrichedTestLog.get(i).get(k));
			String event2 = XConceptExtension.instance().extractName(enrichedTestLog.get(i).get(k));
			if(event1.equals(event2)) {
				continue;
			}else {
				System.out.println("Two events are different");
			}
			}
	}
	
	return enrichedTestLog;		
	
}
	
//	private static void writeResultsToFile(XLog log, String fileName, String variant) {
//
//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/" + variant + "/" + fileName ;
//		File file = new File(outputFilePath);	
//		BufferedWriter bf = null;
//
//		try {
//			bf = new BufferedWriter(new FileWriter(file));
//			bf.write(log.toString());
//			bf.flush();
//			bf.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		Writer fileWriter = new FileWriter(file);
//		CsvWriter csvWriter = new CsvWriter(fileWriter, ',');
//		int n = 1;
//		for (XLog log: list) {
//			String fileName = file.getName();
//			File dir = file.getParentFile();
//			String prefix = fileName.substring(0, fileName.indexOf("."));
//			File netFile = File.createTempFile(prefix + "." + n + ".", "." + logSerializer.getSuffices()[0], dir);
//			csvWriter.write(netFile.getName());
//			csvWriter.endRecord();
//			System.out.println("Exporting Accepting Petri Net to " + netFile.getName());
//			FileOutputStream out = new FileOutputStream(netFile);
//			new XesXmlSerializer().serialize(log, out);
//			out.close();
//			n++;
//		}
//		csvWriter.close();
//	}
	
	////////////
//	package org.processmining.lpm.util;
//
//	import org.deckfour.xes.model.XAttributeMap;
//	import org.deckfour.xes.model.XEvent;
//	import org.deckfour.xes.model.XLog;
//	import org.deckfour.xes.model.XTrace;
//	import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
//	import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
//	import org.processmining.framework.plugin.PluginContext;
//	import org.processmining.framework.plugin.annotations.Plugin;
//	import org.processmining.framework.plugin.annotations.PluginVariant;
//
//	@Plugin(
//			name = "Bring Lifecycle to Event Name", 
//			parameterLabels = {"Input log"}, 
//		    returnLabels = {"Output log"}, 
//		    returnTypes = { XLog.class }
//			)
//	public class BringLifecycleToEventName {
//		@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
//		@PluginVariant(variantLabel = "Bring Lifecycle to Event Name", requiredParameterLabels = {0})
//		public XLog run(PluginContext context, XLog log) {
//			XLog logClone = (XLog) log.clone();
//			for(XTrace trace : logClone){
//				for(XEvent event : trace){
//					XAttributeMap xam = event.getAttributes();
//					String newName = "";
//					if(xam.containsKey("concept:name"))
//						newName = newName + xam.get("concept:name").toString();
//					if(xam.containsKey("lifecycle:transition"))
//						newName = newName + "+" + xam.get("lifecycle:transition").toString();
//					xam.put("concept:name", new XAttributeLiteralImpl("concept:name", newName));
//					xam.put("lifecycle:transition", new XAttributeLiteralImpl("lifecycle:transition", "complete"));
//				}
//			}
//			return logClone;
//		}
//	}
	///////

	private static void writeSetToFile(StringBuilder dataset, String option, String type, int numOfFeatures) {

		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/" + numOfFeatures + "_" + option + "_" + type +  ".arff";
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
	public static <A extends PartialAlignment<String, Transition, Marking>> void applyGeneric(
			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters) throws IOException {
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
		for (Transition t : net.getTransitions()) {
			if (!t.isInvisible()) {
				labelsInPN.put(t, t.getLabel());
			}
		}
		if (parameters.isExperiment()) {
			;
		} else {
			Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
					.construct(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
							IncrementalReplayer.Strategy.REVERT_BASED);
			processXLog(log, net, initialMarking, replayer);
		}
	}


	@SuppressWarnings("unchecked")
	private static <A extends PartialAlignment<String, Transition, Marking>> IncrementalReplayResult<String, String, Transition, Marking, A> processXLog(
			XLog log, Petrinet net, Marking iMarking,
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer) throws IOException{

//		IncrementalReplayResult<String, String, Transition, Marking, A> pluginResult = IncrementalReplayResult.Factory
//				.construct(IncrementalReplayResult.Impl.HASH_MAP);	

		///////
		XFactory xesFactory = new XFactoryBufferedImpl();
		XLog trainingLog = xesFactory.createLog();
		for(int k=0; k<9000; k++) {
			trainingLog.add(log.get(k));
		}

		XLog testLog = xesFactory.createLog();
		for(int l=9187; l<13087; l++) {
			testLog.add(log.get(l));
		}

		System.out.println(trainingLog.size() + "," + testLog.size());

		int numOfFeatures = 3; //k

		StringBuilder markingTrainingSetBody = new StringBuilder();
		StringBuilder costTrainingSetBody = new StringBuilder();
		StringBuilder markingTrainingSetHeader = new StringBuilder();
		StringBuilder costTrainingSetHeader = new StringBuilder();
		markingTrainingSetHeader.append("@relation marking-prediction");
		markingTrainingSetHeader.append("\n");
		markingTrainingSetHeader.append("\n");	
		costTrainingSetHeader.append("@relation cost-prediction");
		costTrainingSetHeader.append("\n");
		costTrainingSetHeader.append("\n");

		TreeSet<String> classes = new TreeSet<>();
		TreeSet<String> resources = new TreeSet<>();
		HashMap<Integer, HashSet<String>> nominalValuesTraining = new HashMap<>();

		for(XTrace trace : trainingLog) {
			for(int x=0; x<trace.size()-1;x++) {				
				//ArrayList<String> temp = new ArrayList<>();
				int featureNumber = 1;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						XEvent xEvent = trace.get(y);						
						Date date = XTimeExtension.instance().extractTimestamp(xEvent);
						String dayLabel = new SimpleDateFormat("EEEE").format(date);
						String quarterlabel = getQuarter(date);
						String resource = XOrganizationalExtension.instance().extractResource(xEvent);
						resources.add(resource);
						markingTrainingSetBody.append(XConceptExtension.instance().extractName(xEvent) + "," + dayLabel + "," + quarterlabel + "," + resource + ",");						
						costTrainingSetBody.append(XConceptExtension.instance().extractName(xEvent) + "," + dayLabel + "," + quarterlabel + "," + resource + ",");
						if(nominalValuesTraining.containsKey(featureNumber)) {
							nominalValuesTraining.get(featureNumber).add(XConceptExtension.instance().extractName(xEvent));
						}else {
							HashSet<String> temp = new HashSet<>();
							temp.add(XConceptExtension.instance().extractName(xEvent));
							nominalValuesTraining.put(featureNumber, temp);
						}
						featureNumber++;
					}else {
						markingTrainingSetBody.append("?,?,?,?,");
						costTrainingSetBody.append("?,?,?,?,");
					}
				}

				for(int k=0; k<=x; k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event
						if(k==x) {
							String toString = null;
//							for(Place place : partialAlignment.getState().getStateInModel().baseSet()) {
//								toString = toString==null? place.toString() : toString + ":" + place.toString();
//							}
							toString = "\"" + partialAlignment.getState().getStateInModel().toString() + "\"";
							//String toString = partialAlignment.getState().getStateInModel().baseSet().toString();
							//classes.add(partialAlignment.getState().getStateInModel().toString());			
							//trainingSet.append(partialAlignment.getState().getStateInModel().toString() + "\n");
							classes.add(toString);
							markingTrainingSetBody.append(toString + "\n");
							costTrainingSetBody.append(partialAlignment.getCost() + "\n");
						}
					}	 					
				}	
				replayer.getDataStore().clear();
			}
		}

//		for(int i=1; i<=numOfFeatures; i++) {
//			markingTrainingSetHeader.append("@attribute e" + i + " { ");
//			boolean first = true;
//			for(String entry: nominalValuesTraining.get(i)) {
//				if(first) {
//					markingTrainingSetHeader.append(entry);
//					first=false;
//				}else {
//					markingTrainingSetHeader.append( "," + entry);
//				}
//			}
//			markingTrainingSetHeader.append(" }");
//			markingTrainingSetHeader.append("\n");
//		}
//
//		markingTrainingSetHeader.append("@attribute class { " );
//		boolean second = true;
//		for(String entry: classes) {
//			if(second) {
//				markingTrainingSetHeader.append(entry);
//				second=false;
//			}else {
//				markingTrainingSetHeader.append( "," + entry);
//			}
//		}
//		markingTrainingSetHeader.append(" }");
//		markingTrainingSetHeader.append("\n");
//		markingTrainingSetHeader.append("\n");
//		markingTrainingSetHeader.append("@data");
//		markingTrainingSetHeader.append("\n");
//		markingTrainingSetHeader.append("\n");	
//
//		markingTrainingSetHeader.append(markingTrainingSetBody);
//
//		System.out.println(markingTrainingSetHeader);
//		
//		//--------
//		for(int i=1; i<=numOfFeatures; i++) {
//			costTrainingSetHeader.append("@attribute e" + i + " { ");
//			boolean third = true;
//			for(String entry: nominalValuesTraining.get(i)) {
//				if(third) {
//					costTrainingSetHeader.append(entry);
//					third=false;
//				}else {
//					costTrainingSetHeader.append( "," + entry);
//				}
//			}
//			costTrainingSetHeader.append(" }");
//			costTrainingSetHeader.append("\n");
//		}
//
//		costTrainingSetHeader.append("@attribute class real" );
//		/*boolean first = true;
//		for(Double entry: classes) {
//			if(first) {
//				trainingSetHeader.append(entry);
//				first=false;
//			}else {
//				trainingSetHeader.append( "," + entry);
//			}
//		}
//		trainingSetHeader.append(" }");*/
//		costTrainingSetHeader.append("\n");
//		costTrainingSetHeader.append("\n");
//		costTrainingSetHeader.append("@data");
//		costTrainingSetHeader.append("\n");
//		costTrainingSetHeader.append("\n");	
//
//		costTrainingSetHeader.append(costTrainingSetBody);
//		
//		//------
//
//		writeSetToFile(markingTrainingSetHeader,"marking","training", numOfFeatures);
//		writeSetToFile(costTrainingSetHeader,"cost","training", numOfFeatures);
		
		/////////TEST SET////////////////////////

		//classes.clear();
		HashMap<Integer, HashSet<String>> nominalValuesTest = new HashMap<>();
		//nominalValuesTraining.clear();

		StringBuilder markingTestSetBody = new StringBuilder();
		StringBuilder costTestSetBody = new StringBuilder();
		StringBuilder markingTestSetHeader = new StringBuilder();
		StringBuilder costTestSetHeader = new StringBuilder();
		markingTestSetHeader.append("@relation prefix-prediction");
		markingTestSetHeader.append("\n");
		markingTestSetHeader.append("\n");

		/*for(int i=0; i<numOfFeatures; i++) {
				testSetHeader.append("@attribute e" + (i+1) + " string");
				testSetHeader.append("\n");
			}*/



		for(XTrace trace : testLog) {
			for(int x=0; x<trace.size()-1;x++) {
				int featureNumber = 1;
				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
					if(y<trace.size()) {
						XEvent xEvent = trace.get(y);
						Date date = XTimeExtension.instance().extractTimestamp(xEvent);
						String dayLabel = new SimpleDateFormat("EEEE").format(date);
						String quarterlabel = getQuarter(date);
						String resource = XOrganizationalExtension.instance().extractResource(xEvent);
						resources.add(resource);
						markingTestSetBody.append(XConceptExtension.instance().extractName(xEvent) + "," + dayLabel + "," + quarterlabel + "," + resource + ",");					
						costTestSetBody.append(XConceptExtension.instance().extractName(xEvent) + "," + dayLabel + "," + quarterlabel + "," + resource + ",");
						if(nominalValuesTest.containsKey(featureNumber)) {
							nominalValuesTest.get(featureNumber).add(XConceptExtension.instance().extractName(xEvent));
						}else {
							HashSet<String> temp = new HashSet<>();
							temp.add(XConceptExtension.instance().extractName(xEvent));
							nominalValuesTest.put(featureNumber, temp);
						}
						featureNumber++;
					}else {
						markingTestSetBody.append("?,?,?,?,");
						costTestSetBody.append("?,?,?,?,");
					}
				}				
				for(int k=0; k<=x; k++) {
					if(k<trace.size()) {
						String caseId = XConceptExtension.instance().extractName(trace);
						String event = XConceptExtension.instance().extractName(trace.get(k));
						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event
						if(k==x) {
							String toString = null;
//							for(Place place : partialAlignment.getState().getStateInModel().baseSet()) {
//								toString = toString==null? place.toString() : toString + ":" + place.toString();
//							}
							toString = "\"" + partialAlignment.getState().getStateInModel().toString() + "\"";
							//String toString = partialAlignment.getState().getStateInModel().baseSet().toString();
							//classes.add(partialAlignment.getState().getStateInModel().toString());			
							//testSet.append(partialAlignment.getState().getStateInModel().toString() + "\n");
							classes.add(toString);
							markingTestSetBody.append(toString + "\n");
							costTestSetBody.append(partialAlignment.getCost() + "\n");
						}
					}	 					
				}	
				replayer.getDataStore().clear();
			}			
		}
		
		////////////////////////////////////////////////////////rendering datasets
		
		for(int i=1; i<=numOfFeatures; i++) {
			markingTrainingSetHeader.append("@attribute e" + i + " { ");
			boolean first = true;
			for(String entry: nominalValuesTraining.get(i)) {
				if(first) {
					markingTrainingSetHeader.append(entry);
					first=false;
				}else {
					markingTrainingSetHeader.append( "," + entry);
				}
			}
			markingTrainingSetHeader.append(" }");
			markingTrainingSetHeader.append("\n");
			markingTrainingSetHeader.append("@attribute dayofweek" + i + " { Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday }\n");
			markingTrainingSetHeader.append("@attribute partofday" + i + " { morning,afternoon,evening,night }\n");			
			//markingTrainingSetHeader.append("@attribute resource" + i + " string\n");
			
			markingTrainingSetHeader.append("@attribute resource" + i + " { " ); 
			boolean another = true;
			for(String entry: resources) {
				if(another) {
					markingTrainingSetHeader.append(entry);
					another=false;
				}else {
					markingTrainingSetHeader.append( "," + entry);
				}
			}
			markingTrainingSetHeader.append(" }\n");
		}

		markingTrainingSetHeader.append("@attribute class { " );
		boolean second = true;
		for(String entry: classes) {
			if(second) {
				markingTrainingSetHeader.append(entry);
				second=false;
			}else {
				markingTrainingSetHeader.append( "," + entry);
			}
		}
		markingTrainingSetHeader.append(" }");
		markingTrainingSetHeader.append("\n");
		markingTrainingSetHeader.append("\n");
		markingTrainingSetHeader.append("@data");
		markingTrainingSetHeader.append("\n");
		markingTrainingSetHeader.append("\n");	

		markingTrainingSetHeader.append(markingTrainingSetBody);

		System.out.println(markingTrainingSetHeader);
		
		//--------
		for(int i=1; i<=numOfFeatures; i++) {
			costTrainingSetHeader.append("@attribute e" + i + " { ");
			boolean third = true;
			for(String entry: nominalValuesTraining.get(i)) {
				if(third) {
					costTrainingSetHeader.append(entry);
					third=false;
				}else {
					costTrainingSetHeader.append( "," + entry);
				}
			}
			costTrainingSetHeader.append(" }");
			costTrainingSetHeader.append("\n");
			costTrainingSetHeader.append("@attribute dayofweek" + i + " { Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday }\n");	
			costTrainingSetHeader.append("@attribute partofday" + i + " { morning,afternoon,evening,night }\n");			
			//costTrainingSetHeader.append("@attribute resource" + i + " string\n");
			
			costTrainingSetHeader.append("@attribute resource" + i + " { " ); 
			boolean another = true;
			for(String entry: resources) {
				if(another) {
					costTrainingSetHeader.append(entry);
					another=false;
				}else {
					costTrainingSetHeader.append( "," + entry);
				}
			}
			costTrainingSetHeader.append(" }\n");
		}

		costTrainingSetHeader.append("@attribute class real" );
		/*boolean first = true;
		for(Double entry: classes) {
			if(first) {
				trainingSetHeader.append(entry);
				first=false;
			}else {
				trainingSetHeader.append( "," + entry);
			}
		}
		trainingSetHeader.append(" }");*/
		costTrainingSetHeader.append("\n");
		costTrainingSetHeader.append("\n");
		costTrainingSetHeader.append("@data");
		costTrainingSetHeader.append("\n");
		costTrainingSetHeader.append("\n");	

		costTrainingSetHeader.append(costTrainingSetBody);
		
		//------

		writeSetToFile(markingTrainingSetHeader,"enhancedmarking","training", numOfFeatures);
		writeSetToFile(costTrainingSetHeader,"enhancedcost","training", numOfFeatures);

		for(int i=1; i<=numOfFeatures; i++) {
			markingTestSetHeader.append("@attribute e" + i + " { ");
			boolean fourth = true;
			for(String entry: nominalValuesTest.get(i)) {
				if(fourth) {
					markingTestSetHeader.append(entry);
					fourth=false;
				}else {
					markingTestSetHeader.append( "," + entry);
				}
			}
			markingTestSetHeader.append(" }");
			markingTestSetHeader.append("\n");
			markingTestSetHeader.append("@attribute dayofweek" + i + " { Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday }\n");	
			markingTestSetHeader.append("@attribute partofday" + i + " { morning,afternoon,evening,night }\n");			
			//markingTestSetHeader.append("@attribute resource" + i + " string\n");
			
			markingTestSetHeader.append("@attribute resource" + i + " { " ); 
			boolean another = true;
			for(String entry: resources) {
				if(another) {
					markingTestSetHeader.append(entry);
					another=false;
				}else {
					markingTestSetHeader.append( "," + entry);
				}
			}
			markingTestSetHeader.append(" }\n");
		}




		markingTestSetHeader.append("@attribute class { " );
		boolean fifth = true;
		for(String entry: classes) {
			if(fifth) {
				markingTestSetHeader.append(entry);
				fifth=false;
			}else {
				markingTestSetHeader.append( "," + entry);
			}
		}
		markingTestSetHeader.append(" }");
		markingTestSetHeader.append("\n");
		markingTestSetHeader.append("\n");
		markingTestSetHeader.append("@data");
		markingTestSetHeader.append("\n");
		markingTestSetHeader.append("\n");	

		markingTestSetHeader.append(markingTestSetBody);
		
		//------
		for(int i=1; i<=numOfFeatures; i++) {
			costTestSetHeader.append("@attribute e" + i + " { ");
			boolean sixth = true;
			for(String entry: nominalValuesTest.get(i)) {
				if(sixth) {
					costTestSetHeader.append(entry);
					sixth=false;
				}else {
					costTestSetHeader.append( "," + entry);
				}
			}
			costTestSetHeader.append(" }");
			costTestSetHeader.append("\n");
			costTestSetHeader.append("@attribute dayofweek" + i + " { Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday }\n");
			costTestSetHeader.append("@attribute partofday" + i + " { morning,afternoon,evening,night }\n");			
			//costTestSetHeader.append("@attribute resource" + i + " string\n");
			
			costTestSetHeader.append("@attribute resource" + i + " { " ); 
			boolean another = true;
			for(String entry: resources) {
				if(another) {
					costTestSetHeader.append(entry);
					another=false;
				}else {
					costTestSetHeader.append( "," + entry);
				}
			}
			costTestSetHeader.append(" }\n");
		}




		costTestSetHeader.append("@attribute class real" );
		/*first = true;
		for(Double entry: classes) {
			if(first) {
				testSetHeader.append(entry);
				first=false;
			}else {
				testSetHeader.append( "," + entry);
			}
		}
		testSetHeader.append(" }");*/
		costTestSetHeader.append("\n");
		costTestSetHeader.append("\n");
		costTestSetHeader.append("@data");
		costTestSetHeader.append("\n");
		costTestSetHeader.append("\n");	

		costTestSetHeader.append(costTestSetBody);
		//-----

		System.out.println(markingTestSetHeader);
		writeSetToFile(markingTestSetHeader,"enhancedmarking","test", numOfFeatures);
		writeSetToFile(costTestSetHeader,"enhancedcost","test", numOfFeatures);

		/*XTrace t= log.get(0);
			String caseId = XConceptExtension.instance().extractName(t);
			System.out.println(caseId + " : ");
			for (XEvent event : t) {
				System.out.print(XConceptExtension.instance().extractName(event) + ", ");
			}
			//String newEventName = XConceptExtension.instance().extractName(t.get(0));
		 */

		return null;
		////

		//		ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);			
		//
		//
		//		Map<Integer, ArrayList<Double>> elapsedTime = new HashMap<>();
		//
		//		final int runs = 50;
		//		final double noOfWindows = 10d;
		//
		//		for(int i=0; i<=runs; i++) {                //i<runs+1 because we need to discard the first run.
		//
		//			System.out.println("\nRun No. " + (i+1));
		//			System.out.println("Window, Time Elapsed in Millis, Observed Events,Avg. Time per Event");
		//
		//			String caseId;
		//			String event;
		//			//Date eventTimeStamp;
		//			int observedEvents = 0;
		//
		//			int eventsWindowSize = (int) Math.ceil(eventLogSortedByDate.size()/noOfWindows);
		//			int remainder = eventLogSortedByDate.size()%eventsWindowSize;
		//			int window = 0;
		//			//replayer.getDataStore().clear();
		//
		//			ArrayList<Triplet<String,String,Date>>	eventLogSortedByDateCopy = new ArrayList<>();	
		//
		//
		//			for (Triplet<String,String, Date> entry : eventLogSortedByDate) {
		//				eventLogSortedByDateCopy.add(new Triplet<String, String, Date>(entry.getValue0()+i, entry.getValue1(), entry.getValue2()));
		//			}
		//
		//			Instant start = Instant.now();
		//
		//			for (Triplet<String,String, Date> entry : eventLogSortedByDateCopy) {
		//
		//				caseId = entry.getValue0();
		//				event = entry.getValue1();
		//
		//				/*if(!(activeCases.contains(caseId))) {
		//					activeCases.add(caseId);
		//					if(activeCases.size() > maxCasesToStore) {
		//						activeCases.remove(replayer.forgetCase());
		//					}
		//				}*/				
		//
		//				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event				
		//
		//				observedEvents++;
		//				if(observedEvents==eventsWindowSize || (window == noOfWindows-1 && observedEvents == remainder)){ 
		//
		//					//watch.stop();
		//
		//					Instant end = Instant.now();  
		//
		//					Duration timeElapsed = Duration.between(start, end);
		//					//System.out.println(timeElapsed.toMillis());
		//
		//					window++; 
		//
		//					if(elapsedTime.containsKey(i)) {
		//						elapsedTime.get(i).add((double)timeElapsed.toMillis()/(double)observedEvents);
		//					}else {
		//						ArrayList<Double> temp = new ArrayList<>();
		//						temp.add((double)timeElapsed.toMillis()/(double)observedEvents);
		//						elapsedTime.put(i, temp);
		//					}
		//
		//					System.out.println(window + "," + timeElapsed.toMillis() + "," + observedEvents + "," + ((double)timeElapsed.toMillis()/(double)observedEvents));
		//
		//					observedEvents = 0;	
		//					//System.out.println(replayer.getDataStore().size());
		//
		//					start = Instant.now();	
		//
		//				}
		//
		//			}		
		//
		//		}		
		//
		//		System.out.println("----------------------------------------------------Time History-------");
		//
		//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/test/time.txt";
		//		File file = new File(outputFilePath);	
		//		BufferedWriter bf = null;
		//		bf = new BufferedWriter(new FileWriter(file));
		//		//System.out.println(elapsedTime);
		//
		//		System.out.println("\nAverage for " + runs + " Runs");
		//		System.out.println("Window, Avg. Time per Event");
		//
		//
		//		for(int i=0; i<noOfWindows; i++) {
		//			double sum = 0.0;
		//			for(int j=1; j<=runs; j++) {
		//				sum += elapsedTime.get(j).get(i);
		//			}
		//			System.out.println(i+1 + "," + (double)sum/(double)runs );				
		//		}
		//
		//		//			System.out.println();
		//		//			if(first) {
		//		//				first = false;
		//		//			}else {
		//		//				bf.newLine();
		//		//			}
		//		//			System.out.print(entry.getKey() + ",");
		//		//			bf.write(entry.getKey() + ",");
		//		//			for(Double value: entry.getValue()) {
		//		//				System.out.print(value + ",");
		//		//				bf.write(value + ",");
		//		//			}
		//		//		}
		//		bf.flush();
		//		bf.close();
		//
		//		//System.out.println(eventLogSortedByDate);
		//
		//
		//		return pluginResult;
	}
	
	private static String getQuarter(Date date) {
		Calendar c = Calendar.getInstance();
	    c.setTime(date);
	    int hours = c.get(Calendar.HOUR_OF_DAY);
	    int min = c.get(Calendar.MINUTE);

	    if(hours>=6 && hours<12){
	        return "morning";
	    }else if(hours>=12 && hours<16){
	    	return "afternoon";
	    }else if(hours>=16 && hours<21){
	    	return "evening";
	    }else if((hours>=21 && hours<=24) || (hours>=0 && hours<6)){
	    	return "night";
	    }else {
	    	return "invalid time";
	    }
	}
	
	/**
	 * This method returns the value of the attribute <tt>concept:name</tt> for
	 * the given attributable element
	 * 
	 * @param element the element to analyze
	 * @return the value of the <tt>concept:name</tt> attribute
	 */
	public static String getName(XAttributable element) {
		XAttributeLiteral name = (XAttributeLiteral) element.getAttributes().get(XConceptExtension.KEY_NAME);
		return name.getValue();
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

}


//	public IncrementalReplayResult<String, String, Transition, Marking, ? extends PartialAlignment<String, Transition, Marking>> apply(
//			final UIPluginContext context, final Petrinet net, XLog log) throws IOException {
//
//		Map<Transition, String> modelElementsToLabelMap = new HashMap<>();
//		Map<String, Collection<Transition>> labelsToModelElementsMap = new HashMap<>();
//		TObjectDoubleMap<Transition> modelMoveCosts = new TObjectDoubleHashMap<>();
//		TObjectDoubleMap<String> labelMoveCosts = new TObjectDoubleHashMap<>();
//
//		Marking initialMarking = getInitialMarking(net);
//		Marking finalMarking = getFinalMarking(net);
//		//Marking finalMarking = null;
//
//		setupLabelMap(net, modelElementsToLabelMap, labelsToModelElementsMap);
//		setupModelMoveCosts(net, modelMoveCosts, labelMoveCosts, labelsToModelElementsMap);
//		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters = new IncrementalRevBasedReplayerParametersImpl<>();
//		parameters.setUseMultiThreading(false);
//		parameters.setLabelMoveCosts(labelMoveCosts);
//		parameters.setLabelToModelElementsMap(labelsToModelElementsMap);
//		parameters.setModelMoveCosts(modelMoveCosts);
//		parameters.setModelElementsToLabelMap(modelElementsToLabelMap);
//		parameters.setSearchAlgorithm(IncrementalReplayer.SearchAlgorithm.A_STAR);
//		parameters.setUseSolutionUpperBound(false);
//		/////fixed parameter
//		//parameters.setLookBackWindow(0);		
//		parameters.setLookBackWindow(Integer.MAX_VALUE);
//		//-----------------------------------------------------parameters to set
//
//		//parameters.setLookBackWindowType(true);  //if set to True then alignments will be reverted on the basis of observed events, not moves/states
//
//
//
//		//for marking for cost this should always be 0
//		//parameters.setMaxCasesToStore(500);
//		parameters.setMaxCasesToStore(Integer.MAX_VALUE);
//
//		parameters.setExperiment(false);
//		if (parameters.isExperiment()) {
//			;//return applyMeasurementAware(context, net, initialMarking, finalMarking, log, parameters);
//		} else {
//			applyGeneric(net, initialMarking, finalMarking, log, parameters);
//		}
//		return null;
//	}
//
//	private static void writeSetToFile(StringBuilder dataset, String type, int numOfFeatures) {
//
//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Scenario 11 ML based prefix Prediction/Cost_" + numOfFeatures + "_" + type +  ".arff";
//		File file = new File(outputFilePath);	
//		BufferedWriter bf = null;
//
//		try {
//			bf = new BufferedWriter(new FileWriter(file));
//			bf.write(dataset.toString());
//			bf.flush();
//			bf.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	@SuppressWarnings("unchecked")
//	public static <A extends PartialAlignment<String, Transition, Marking>> void applyGeneric(
//			final Petrinet net, final Marking initialMarking, final Marking finalMarking,
//			/*final*/ XLog log, final IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters) throws IOException {
//		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(net);
//		Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
//		for (Transition t : net.getTransitions()) {
//			if (!t.isInvisible()) {
//				labelsInPN.put(t, t.getLabel());
//			}
//		}
//		if (parameters.isExperiment()) {
//			;
//		} else {
//			Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
//			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
//					.construct(initialMarking, finalMarking, store, modelSemantics, parameters, labelsInPN,
//							IncrementalReplayer.Strategy.REVERT_BASED);
//			processXLog(log, net, initialMarking, replayer);
//		}
//	}
//
//
//	@SuppressWarnings("unchecked")
//	private static <A extends PartialAlignment<String, Transition, Marking>> IncrementalReplayResult<String, String, Transition, Marking, A> processXLog(
//			XLog log, Petrinet net, Marking iMarking,
//			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer) throws IOException{
//
////		IncrementalReplayResult<String, String, Transition, Marking, A> pluginResult = IncrementalReplayResult.Factory
////				.construct(IncrementalReplayResult.Impl.HASH_MAP);	
//
//		///////
//		XFactory xesFactory = new XFactoryBufferedImpl();
//		XLog trainingLog = xesFactory.createLog();
//		for(int k=0; k<9000; k++) {
//			trainingLog.add(log.get(k));
//		}
//
//		XLog testLog = xesFactory.createLog();
//		for(int l=9187; l<13087; l++) {
//			testLog.add(log.get(l));
//		}
//
//		System.out.println(trainingLog.size() + "," + testLog.size());
//
//		int numOfFeatures = 5;
//
//		StringBuilder trainingSetBody = new StringBuilder();
//		StringBuilder trainingSetHeader = new StringBuilder();
//		trainingSetHeader.append("@relation prefix-prediction");
//		trainingSetHeader.append("\n");
//		trainingSetHeader.append("\n");		
//
//		HashSet<Double> classes = new HashSet<>();
//		HashMap<Integer, HashSet<String>> nominalvalues = new HashMap<>();
//
//		for(XTrace trace : trainingLog) {
//			for(int x=0; x<trace.size()-1;x++) {				
//				//ArrayList<String> temp = new ArrayList<>();
//				int featureNumber = 1;
//				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
//					if(y<trace.size()) {
//						trainingSetBody.append(XConceptExtension.instance().extractName(trace.get(y)) + ",");
//						if(nominalvalues.containsKey(featureNumber)) {
//							nominalvalues.get(featureNumber).add(XConceptExtension.instance().extractName(trace.get(y)));
//						}else {
//							HashSet<String> temp = new HashSet<>();
//							temp.add(XConceptExtension.instance().extractName(trace.get(y)));
//							nominalvalues.put(featureNumber, temp);
//						}
//						featureNumber++;
//					}else {
//						trainingSetBody.append("?,");
//					}
//				}
//
//				for(int k=0; k<=x; k++) {
//					if(k<trace.size()) {
//						String caseId = XConceptExtension.instance().extractName(trace);
//						String event = XConceptExtension.instance().extractName(trace.get(k));
//						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event
//						if(k==x) {
//							classes.add(partialAlignment.getCost());			
//							trainingSetBody.append(partialAlignment.getCost() + "\n");
//
//						}
//					}	 					
//				}	
//				replayer.getDataStore().clear();
//			}
//		}
//
//		for(int i=1; i<=numOfFeatures; i++) {
//			trainingSetHeader.append("@attribute e" + i + " { ");
//			boolean first = true;
//			for(String entry: nominalvalues.get(i)) {
//				if(first) {
//					trainingSetHeader.append(entry);
//					first=false;
//				}else {
//					trainingSetHeader.append( "," + entry);
//				}
//			}
//			trainingSetHeader.append(" }");
//			trainingSetHeader.append("\n");
//		}
//
//		trainingSetHeader.append("@attribute class real" );
//		/*boolean first = true;
//		for(Double entry: classes) {
//			if(first) {
//				trainingSetHeader.append(entry);
//				first=false;
//			}else {
//				trainingSetHeader.append( "," + entry);
//			}
//		}
//		trainingSetHeader.append(" }");*/
//		trainingSetHeader.append("\n");
//		trainingSetHeader.append("\n");
//		trainingSetHeader.append("@data");
//		trainingSetHeader.append("\n");
//		trainingSetHeader.append("\n");	
//
//		trainingSetHeader.append(trainingSetBody);
//
//		System.out.println(trainingSetHeader);
//
//		writeSetToFile(trainingSetHeader,"training", numOfFeatures);
//		/////////////////////////////////
//
//		classes.clear();
//		nominalvalues.clear();
//
//		StringBuilder testSetBody = new StringBuilder();
//		StringBuilder testSetHeader = new StringBuilder();
//		testSetHeader.append("@relation prefix-prediction");
//		testSetHeader.append("\n");
//		testSetHeader.append("\n");
//
//		/*for(int i=0; i<numOfFeatures; i++) {
//				testSetHeader.append("@attribute e" + (i+1) + " string");
//				testSetHeader.append("\n");
//			}*/
//
//
//
//		for(XTrace trace : testLog) {
//			for(int x=0; x<trace.size()-1;x++) {
//				int featureNumber = 1;
//				for(int y=x+1; y<=(numOfFeatures+x) ; y++) {
//					if(y<trace.size()) {
//						testSetBody.append(XConceptExtension.instance().extractName(trace.get(y)) + ",");
//						if(nominalvalues.containsKey(featureNumber)) {
//							nominalvalues.get(featureNumber).add(XConceptExtension.instance().extractName(trace.get(y)));
//						}else {
//							HashSet<String> temp = new HashSet<>();
//							temp.add(XConceptExtension.instance().extractName(trace.get(y)));
//							nominalvalues.put(featureNumber, temp);
//						}
//						featureNumber++;
//					}else {
//						testSetBody.append("?,");
//					}
//				}				
//				for(int k=0; k<=x; k++) {
//					if(k<trace.size()) {
//						String caseId = XConceptExtension.instance().extractName(trace);
//						String event = XConceptExtension.instance().extractName(trace.get(k));
//						PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event
//						if(k==x) {
//							classes.add(partialAlignment.getCost());			
//							testSetBody.append(partialAlignment.getCost() + "\n");
//
//						}
//					}	 					
//				}	
//				replayer.getDataStore().clear();
//			}			
//		}
//
//		for(int i=1; i<=numOfFeatures; i++) {
//			testSetHeader.append("@attribute e" + i + " { ");
//			boolean check = true;
//			for(String entry: nominalvalues.get(i)) {
//				if(check) {
//					testSetHeader.append(entry);
//					check=false;
//				}else {
//					testSetHeader.append( "," + entry);
//				}
//			}
//			testSetHeader.append(" }");
//			testSetHeader.append("\n");
//		}
//
//
//
//
//		testSetHeader.append("@attribute class real" );
//		/*first = true;
//		for(Double entry: classes) {
//			if(first) {
//				testSetHeader.append(entry);
//				first=false;
//			}else {
//				testSetHeader.append( "," + entry);
//			}
//		}
//		testSetHeader.append(" }");*/
//		testSetHeader.append("\n");
//		testSetHeader.append("\n");
//		testSetHeader.append("@data");
//		testSetHeader.append("\n");
//		testSetHeader.append("\n");	
//
//		testSetHeader.append(testSetBody);
//
//		System.out.println(testSetHeader);
//		writeSetToFile(testSetHeader,"test", numOfFeatures);
//
//		/*XTrace t= log.get(0);
//			String caseId = XConceptExtension.instance().extractName(t);
//			System.out.println(caseId + " : ");
//			for (XEvent event : t) {
//				System.out.print(XConceptExtension.instance().extractName(event) + ", ");
//			}
//			//String newEventName = XConceptExtension.instance().extractName(t.get(0));
//		 */
//
//		return null;
//		////
//
//		//		ArrayList<Triplet<String,String,Date>>	eventLogSortedByDate = TimeStampsBasedLogToStreamConverter.sortEventLogByDate(log);			
//		//
//		//
//		//		Map<Integer, ArrayList<Double>> elapsedTime = new HashMap<>();
//		//
//		//		final int runs = 50;
//		//		final double noOfWindows = 10d;
//		//
//		//		for(int i=0; i<=runs; i++) {                //i<runs+1 because we need to discard the first run.
//		//
//		//			System.out.println("\nRun No. " + (i+1));
//		//			System.out.println("Window, Time Elapsed in Millis, Observed Events,Avg. Time per Event");
//		//
//		//			String caseId;
//		//			String event;
//		//			//Date eventTimeStamp;
//		//			int observedEvents = 0;
//		//
//		//			int eventsWindowSize = (int) Math.ceil(eventLogSortedByDate.size()/noOfWindows);
//		//			int remainder = eventLogSortedByDate.size()%eventsWindowSize;
//		//			int window = 0;
//		//			//replayer.getDataStore().clear();
//		//
//		//			ArrayList<Triplet<String,String,Date>>	eventLogSortedByDateCopy = new ArrayList<>();	
//		//
//		//
//		//			for (Triplet<String,String, Date> entry : eventLogSortedByDate) {
//		//				eventLogSortedByDateCopy.add(new Triplet<String, String, Date>(entry.getValue0()+i, entry.getValue1(), entry.getValue2()));
//		//			}
//		//
//		//			Instant start = Instant.now();
//		//
//		//			for (Triplet<String,String, Date> entry : eventLogSortedByDateCopy) {
//		//
//		//				caseId = entry.getValue0();
//		//				event = entry.getValue1();
//		//
//		//				/*if(!(activeCases.contains(caseId))) {
//		//					activeCases.add(caseId);
//		//					if(activeCases.size() > maxCasesToStore) {
//		//						activeCases.remove(replayer.forgetCase());
//		//					}
//		//				}*/				
//		//
//		//				PartialAlignment<String, Transition, Marking> partialAlignment = replayer.processEvent(caseId, event);  //Prefix Alignment of the current observed event				
//		//
//		//				observedEvents++;
//		//				if(observedEvents==eventsWindowSize || (window == noOfWindows-1 && observedEvents == remainder)){ 
//		//
//		//					//watch.stop();
//		//
//		//					Instant end = Instant.now();  
//		//
//		//					Duration timeElapsed = Duration.between(start, end);
//		//					//System.out.println(timeElapsed.toMillis());
//		//
//		//					window++; 
//		//
//		//					if(elapsedTime.containsKey(i)) {
//		//						elapsedTime.get(i).add((double)timeElapsed.toMillis()/(double)observedEvents);
//		//					}else {
//		//						ArrayList<Double> temp = new ArrayList<>();
//		//						temp.add((double)timeElapsed.toMillis()/(double)observedEvents);
//		//						elapsedTime.put(i, temp);
//		//					}
//		//
//		//					System.out.println(window + "," + timeElapsed.toMillis() + "," + observedEvents + "," + ((double)timeElapsed.toMillis()/(double)observedEvents));
//		//
//		//					observedEvents = 0;	
//		//					//System.out.println(replayer.getDataStore().size());
//		//
//		//					start = Instant.now();	
//		//
//		//				}
//		//
//		//			}		
//		//
//		//		}		
//		//
//		//		System.out.println("----------------------------------------------------Time History-------");
//		//
//		//		String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/test/time.txt";
//		//		File file = new File(outputFilePath);	
//		//		BufferedWriter bf = null;
//		//		bf = new BufferedWriter(new FileWriter(file));
//		//		//System.out.println(elapsedTime);
//		//
//		//		System.out.println("\nAverage for " + runs + " Runs");
//		//		System.out.println("Window, Avg. Time per Event");
//		//
//		//
//		//		for(int i=0; i<noOfWindows; i++) {
//		//			double sum = 0.0;
//		//			for(int j=1; j<=runs; j++) {
//		//				sum += elapsedTime.get(j).get(i);
//		//			}
//		//			System.out.println(i+1 + "," + (double)sum/(double)runs );				
//		//		}
//		//
//		//		//			System.out.println();
//		//		//			if(first) {
//		//		//				first = false;
//		//		//			}else {
//		//		//				bf.newLine();
//		//		//			}
//		//		//			System.out.print(entry.getKey() + ",");
//		//		//			bf.write(entry.getKey() + ",");
//		//		//			for(Double value: entry.getValue()) {
//		//		//				System.out.print(value + ",");
//		//		//				bf.write(value + ",");
//		//		//			}
//		//		//		}
//		//		bf.flush();
//		//		bf.close();
//		//
//		//		//System.out.println(eventLogSortedByDate);
//		//
//		//
//		//		return pluginResult;
//	}
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
//	public static Integer countFinalStates(Map<String, ArrayList<Integer>> alignmentsWindowsStates) {
//		Integer sum = 0;
//		for(Entry<String, ArrayList<Integer>> entry: alignmentsWindowsStates.entrySet()) {
//			sum += entry.getValue().get(entry.getValue().size()-1);
//		}
//		return sum;
//	}
//
//	public static Integer countAllStates(Map<String, ArrayList<Integer>> alignmentsWindowsStates) {
//		Integer sum = 0;
//		for(Entry<String, ArrayList<Integer>> entry: alignmentsWindowsStates.entrySet()) {
//			sum += getSum(entry.getValue());
//		}
//		return sum;
//	}
//
//	public static int getSum(ArrayList<Integer> list) {
//		int sum = 0;
//		for (int i: list) {
//			sum += i;
//		}
//		return sum;
//	}
//
//
//	public static double sumArrayList(ArrayList<Double> arrayList) {
//		Double sum = 0.0;
//		for(int i = 0; i < arrayList.size(); i++)
//		{
//			sum += arrayList.get(i);
//		}
//		return sum;		
//	}
//
//	//////ARCHIVE
//
//	private boolean isFeasible(String caseId, List<Move<String, Transition>> moves, List<String> trace, Petrinet net,
//			Marking iMarking) {
//		boolean res = true;
//		EfficientPetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(net);
//		semantics.setState(semantics.convert(iMarking));
//		int i = 0;
//		for (Move<String, Transition> move : moves) {
//			if (move.getTransition() != null) {
//				res &= semantics.isEnabled(move.getTransition());
//				if (!res) {
//					System.out.println("Violation for case " + caseId + ", " + "move " + move.toString() + ", at: "
//							+ semantics.getStateAsMarking().toString());
//				}
//				semantics.directExecuteExecutableTransition(move.getTransition());
//			}
//			if (move.getEventLabel() != null) {
//				//				res &= move.getEventLabel().equals(trace.get(i).toString() + "+complete");
//				res &= move.getEventLabel().equals(trace.get(i).toString());
//				if (!res) {
//					System.out.println("Violation for case " + caseId + " on label part. original: " + trace.toString()
//					+ ", moves: " + moves.toString());
//				}
//				i++;
//			}
//			if (!res)
//				break;
//		}
//		return res;
//	}
//
//
//	private static int calculateDiffWindowRelatedCases(Set<String> parentCasesSet, Set<String> childCasesSet){
//		int score = 0;
//		for (String item: childCasesSet) {
//			if(parentCasesSet.contains(item)) {
//				score++;
//			}
//		}
//		return score;
//	}
//
//	private static Date getWindowTimeStamp(ArrayList<Triplet<String,String,Date>> sortedByValue, String choice) {
//		//LinkedList<Pair<String,String>> listKeys = new LinkedList<Pair<String,String>>(sortedByValue.keySet());
//		if(choice.equals("start")) {
//			//return sortedByValue.get( listKeys.getFirst());
//			return sortedByValue.get(0).getValue2();
//		}else {
//			//return sortedByValue.get( listKeys.getLast());
//			return sortedByValue.get(sortedByValue.size()-1).getValue2();
//		}		
//	}	
//
//	public static <A extends PartialAlignment<String, Transition, Marking>> A processEventUsingReplayer(String caseId,
//			String event,
//			IncrementalReplayer<Petrinet, String, Marking, Transition, String, A, ? extends IncrementalReplayerParametersImpl<Petrinet, String, Transition>> replayer) {
//		return replayer.processEvent(caseId, event);
//	}
//
//	private List<String> toStringList(XTrace trace, XEventClasses classes) {
//		List<String> l = new ArrayList<>(trace.size());
//		for (int i = 0; i < trace.size(); i++) {
//			l.add(i, classes.getByIdentity(XConceptExtension.instance().extractName(trace.get(i))).toString());
//		}
//		return l;
//	}
//
//	private static double calculateCurrentCosts(TObjectDoubleMap<String> costPerTrace) {
//		double totalCost = 0;
//		for (String t : costPerTrace.keySet()) {
//			//totalCost += count.get(t) * costPerTrace.get(t);
//			totalCost += costPerTrace.get(t);
//		}
//		return totalCost;
//	}
//
//}
