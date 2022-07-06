package org.processmining.constraineddataocc.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;

import org.deckfour.xes.extension.XExtensionManager;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryBufferedImpl;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;



/*@Plugin(
		name = "00 Synchronise timestamps of a clean log with equivalent nosiy logs", 
		parameterLabels = {"Event Data1", "Event Data2" }, 
		returnLabels = {}, 
		returnTypes = { }, 
		userAccessible = true)*/

public class TimestampsSynchronization {

	/*@UITopiaVariant(author = "Rashid", email = "r.zaman@tue.nl", affiliation = "Eindhoven University of Technology")
	@PluginVariant(requiredParameterLabels = {})*/

	public static XLog synchronizeLogs(final XLog timedLog, XLog untimedLog) throws Exception {
		
		
		
		for(int i=0; i<timedLog.size(); i++) {

			String noiseMechanism = compareTraces(timedLog.get(i), untimedLog.get(i));
			//now check if the trace is the same as in the orginal log, or with missing events or displaces events.
			//case 1: just copy the corresponding timestamps, case 2: match the events and copy the timestamps, case3: copy the timestamp of the corresponding events in the traces
			switch(noiseMechanism){
				case "same":
					copyTimestamps(timedLog.get(i), untimedLog.get(i));
					break;
				case "missing":
					copyTimestampsWithMissing(timedLog.get(i), untimedLog.get(i));
					break;
				case "displaced":							
					copyTimestamps(timedLog.get(i), untimedLog.get(i));            //copy the timestamps for the corresponding events, so that we do not disturb the distribution and arrival times of the events and that we see the impact of noisy events.
					break;												//for instance, we ,as like unnoisy log, observe the x event of trace Y at the same time timestamp but say it is noisy then how our forgetting and predicting decisions are impacted.
				default:
					System.out.println("unknown choice option");
			}
			
		}

		//check correctness of the synchronization
		
		if(true) {
			for(XTrace trace: untimedLog) {
				for(org.deckfour.xes.model.XEvent event : trace) {
					if(XTimeExtension.instance().extractTimestamp(event)==null) {
						System.out.println("There is an event without a timestamp");
					}
				}
			}

			for(int i=0; i<timedLog.size(); i++) {

				//XTrace trace = log_.get(i);

				String noiseMechanism = compareTraces(timedLog.get(i), untimedLog.get(i));
				//now check if the trace is the same as in the orginal log, or with missing events or displaces events.
				//case 1: do nothing, case 2: match the events and copy the timestamps, case3: copy the timestamp of the neighbouring event for the displaces event
				if(noiseMechanism.equals("missing") || noiseMechanism.equals("displaced")) {
					/*displayTrace(sourceLog.get(i));
					displayTrace(targetLog.get(i));*/
				}else {
					for(int j=0; j<timedLog.get(i).size();j++) {
						Date date1 = XTimeExtension.instance().extractTimestamp(timedLog.get(i).get(j));
						Date date2 = XTimeExtension.instance().extractTimestamp(untimedLog.get(i).get(j));
						if(date1.compareTo(date2)!=0) {
							System.out.println("problem with timestamps");
						}				
					}
				}
				//String caseId = XConceptExtension.instance().extractName(log.get(i));
			}	
		}	
		return untimedLog; 
		//writeLogToFile(targetLog, inputAndOutputFilePath + targetFileName + "_"+ variant +  ".xes" );
	}
	
	public static String compareTraces(XTrace trace, XTrace trace_) {
		if(trace.size()==trace_.size()) {
			for(int i=0; i<trace.size(); i++) {
				if(!XConceptExtension.instance().extractName(trace.get(i)).equals(XConceptExtension.instance().extractName(trace_.get(i)))) {
					break;
				}
				if(i==trace.size()-1) {
					return "same";             //the two traces are the same
				}			
			}
		}		

		//the two traces are not the same
		ArrayList<String> events = new ArrayList<>();
		ArrayList<String> events_ = new ArrayList<>();
		for(int i=0; i<trace.size(); i++) {
			events.add(XConceptExtension.instance().extractName(trace.get(i)));
		}
		for(int i=0; i<trace_.size();i++) {
			events_.add(XConceptExtension.instance().extractName(trace_.get(i)));
		}
		for(int i=0; i<events.size(); i++) {
			if(!events_.isEmpty() && events.get(i).equals(events_.get(0))){
				events_.remove(0);
			}else {
				continue;
			}
		}

		if(events_.isEmpty()) {
			return "missing";                 //We assume that there is always only one kind of noise and that in case of missingness, the size of the tracewith noise is less than the original trace without noise.
		}else {
			return "displaced";
		}	
	}

	private static void copyTimestamps(XTrace trace, XTrace trace_) {

		//System.out.println(XConceptExtension.instance().extractName(trace));

		XFactory xesFactory = new XFactoryBufferedImpl();
		XExtensionManager xesExtensionManager = XExtensionManager.instance();
		for(int i=0; i<trace.size(); i++) {
			Date date = XTimeExtension.instance().extractTimestamp(trace.get(i));
			trace_.get(i).getAttributes().put("time:timestamp",
					xesFactory.createAttributeTimestamp("time:timestamp",date,xesExtensionManager.getByName("Time")));
		}

	}

	private static void copyTimestampsWithMissing(XTrace trace, XTrace trace_) {
		XFactory xesFactory = new XFactoryBufferedImpl();
		XExtensionManager xesExtensionManager = XExtensionManager.instance();
		int j=0;
		for(int i=0; i<trace.size(); i++) {
			for(; j<trace_.size();) {
				if(XConceptExtension.instance().extractName(trace.get(i)).equals(XConceptExtension.instance().extractName(trace_.get(j)))){
					Date date = XTimeExtension.instance().extractTimestamp(trace.get(i));
					trace_.get(j).getAttributes().put("time:timestamp",
							xesFactory.createAttributeTimestamp("time:timestamp",date,xesExtensionManager.getByName("Time")));
					j++;
					break;
				}else {
					break;
				}
			}			
		}
	}

	private static void displayTrace(XTrace trace) {
		System.out.print(XConceptExtension.instance().extractName(trace));
		for(XEvent event : trace) {
			System.out.print("(" + XConceptExtension.instance().extractName(event) + ":" + XTimeExtension.instance().extractTimestamp(event) + ") ");
		}
		System.out.println();
	}

	public void writeLogToFile(XLog log, String outputFilePath) throws Exception {

		//String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/a12/a12f9timed/" + fileName + "_"+ variant +  ".xes";
		File file = new File(outputFilePath);

		XSerializer serializer = new XesXmlSerializer();	

		try {			
			FileOutputStream fos = new FileOutputStream(file);
			serializer.serialize(log, fos);
			fos.close();
		} catch(Exception e) {
			throw e;
		} 		
	}

	//	private static void copyTimestampsWithDisplacements(XTrace trace, XTrace trace_) {
	//		
	//	}

}
