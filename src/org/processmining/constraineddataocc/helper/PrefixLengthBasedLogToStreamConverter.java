package org.processmining.constraineddataocc.helper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.framework.util.Pair;
//import org.processmining.prefiximputation.inventory.NullConfiguration;

public class PrefixLengthBasedLogToStreamConverter {
	
	public static LinkedHashMap<Integer, ArrayList<Pair<String, String>>> sortEventLog(XLog log){
		int index = 0;
		LinkedHashMap<Integer, ArrayList<Pair<String, String>>> eventsStream = new LinkedHashMap<>();
		for (XTrace t : log) {
			String caseId = XConceptExtension.instance().extractName(t);
			index = 0;
			for(XEvent e: t) {				
				String newEventName = XConceptExtension.instance().extractName(e);
				//Pair<String,String> eventPacket = new Pair<String, String>(caseId, newEventName);
				//Date date = XTimeExtension.instance().extractTimestamp(e);
				Pair<String,String> eventPacket = new Pair<String,String>(caseId, newEventName);
				if(eventsStream.containsKey(index)) {
					eventsStream.get(index).add(eventPacket);
				}else {
					ArrayList<Pair<String, String>> temp= new ArrayList<>();
					temp.add(eventPacket);
					eventsStream.put(index, temp);
				}
				
				index++;
			}
		}
		
		return eventsStream;
	}
	
	private static void printTripletList(ArrayList<Triplet<String,String,Date>> tripletList ) {
		for(Triplet entry: tripletList) {
			 System.out.println(entry.getValue0() + ", " +  entry.getValue1() + ", " +  entry.getValue2());
			 //System.out.println(entry.getValue(0) + ", " +  entry.getValue(1) + ", " +  entry.getValue(2));
		}
	}
	
	private static void printLinkedHashMap(LinkedHashMap<Pair<String,String>,Date> sortedByValue  ){
		for (Map.Entry<Pair<String,String>,Date> entry : sortedByValue.entrySet()) {		    
		   System.out.println(entry.getKey() + ", " +  entry.getValue());
		}
	}
	
	public static void main(String args[]) {
		String logFile = "D:\\Research Work\\latest\\Streams\\Rashid Prefix Alignment\\"
				+ "Process Models BPI 2012 from Boudewijn\\A only Events.xes";
		XLog log = null;
		//XEventClassifier eventClassifier;
		try {
			log = new XUniversalParser().parse(new File(logFile)).iterator().next();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		LinkedHashMap<Integer, ArrayList<Pair<String, String>>>	eventLogSorted = PrefixLengthBasedLogToStreamConverter.sortEventLog(log);
		checkCorrectness(log, eventLogSorted);
		
	}
	
	private static void checkCorrectness(XLog log, LinkedHashMap<Integer, ArrayList<Pair<String, String>>> eventLogSorted) {
		int totalevents = 0;
		int prefixLength = 0;
		for(ArrayList<Pair<String, String>> entry :  eventLogSorted.values()) {
			System.out.println((prefixLength + 1) + "," + entry.size());
			prefixLength++;
			totalevents += entry.size();			
		}
		
		System.out.println("Total no. of events in the log are: " + totalevents);		
	}
	
	public static boolean isCollectionSorted(ArrayList list) {
	    List copy = new ArrayList(list);
	    Collections.sort(copy);
	    return copy.equals(list);
	}
	

}
