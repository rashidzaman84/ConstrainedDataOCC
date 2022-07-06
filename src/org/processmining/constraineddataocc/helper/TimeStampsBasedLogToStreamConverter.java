package org.processmining.constraineddataocc.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.javatuples.Triplet;
import org.processmining.framework.util.Pair;

import com.google.common.collect.Ordering;

public class TimeStampsBasedLogToStreamConverter {

	public static ArrayList<Triplet<String,String,Date>> sortEventLogByDate(XLog log){

		List<Triplet<String,String,Date>> eventsStream = new ArrayList<Triplet<String,String,Date>>();

		for (XTrace t : log) {
			for(XEvent e: t) {
				String caseId = XConceptExtension.instance().extractName(t);
				String newEventName = XConceptExtension.instance().extractName(e);
				//Pair<String,String> eventPacket = new Pair<String, String>(caseId, newEventName);
				Date date = XTimeExtension.instance().extractTimestamp(e);
				Triplet<String,String,Date> eventPacket = new Triplet<String,String,Date>(caseId, newEventName, date);				
				eventsStream.add(eventPacket);
			}
		}
		//need to sort the hashmap on date
		Comparator<Triplet<String,String,Date>> valueComparator = new Comparator<Triplet<String,String,Date>>() { 
			@Override public int compare(Triplet<String,String,Date> e1, Triplet<String,String,Date> e2) { 
				Date v1 = e1.getValue2(); 
				Date v2 = e2.getValue2(); 
				return v1.compareTo(v2);
			}
		};
		ArrayList<Triplet<String,String,Date>> entries = new ArrayList<Triplet<String,String,Date>>(eventsStream);
		//entries.addAll(eventsStream.);
		Collections.copy(entries,eventsStream);
		List<Triplet<String,String,Date>> listOfEntries = new ArrayList<Triplet<String,String,Date>>(entries);
		Collections.sort(listOfEntries, valueComparator);
		ArrayList<Triplet<String,String,Date>> sortedByValue = new ArrayList<Triplet<String,String,Date>>(listOfEntries.size());
		//System.out.println(sortedByValue.size());
		for(Triplet<String,String,Date> entry : listOfEntries){
			sortedByValue.add(entry);
		}
		//printTripletList(sortedByValue);
		return sortedByValue;
	}

	public static XLog sortEventLogByCaseArrivalTime(XLog log){

		XLog sortedLog = (XLog) log.clone();

		sortedLog.clear();

		ArrayList<XTrace> tracesList = new ArrayList<>();

		for (XTrace t : log) {
			tracesList.add(t);
		}

		//need to sort the arraylist on date
		Comparator<XTrace> valueComparator = new Comparator<XTrace>() { 
			@Override public int compare(XTrace trace1, XTrace trace2) { 
				Date v1 = XTimeExtension.instance().extractTimestamp(trace1.get(0));
				Date v2 = XTimeExtension.instance().extractTimestamp(trace2.get(0)); 
				return v1.compareTo(v2);
			}
		};

		ArrayList<XTrace> entries = new ArrayList<XTrace>(tracesList);
		Collections.copy(entries,tracesList);
		List<XTrace> listOfEntries = new ArrayList<XTrace>(entries);
		Collections.sort(listOfEntries, valueComparator);
		ArrayList<XTrace> sortedByValue = new ArrayList<XTrace>(listOfEntries.size());

		for(XTrace entry : listOfEntries){
			sortedByValue.add(entry);
		}

		for(XTrace entry : sortedByValue) {
			sortedLog.add(entry);
		}

		return sortedLog;
	}

	public static ArrayList<Triplet<String, String, Date>> addShortHaulComplaintEvents(XLog log) {
		ArrayList<Triplet<String, String, Date>> eventLogSortedByDateCopy = sortEventLogByDate(log);

		ArrayList<Triplet<String,String, Date>> tempArray = new ArrayList<>();
		ArrayList<String> casesImputed = new ArrayList<>();
		int z=0;

		while(z<1153) {
		
			for(XTrace trace: log) {

				String caseIdd = XConceptExtension.instance().extractName(trace);
				String lastEventName = XConceptExtension.instance().extractName(trace.get(trace.size()-1));

				if(!casesImputed.contains(caseIdd) && ((lastEventName.equals("A_DECLINED") && trace.size()<=4) || (lastEventName.equals("A_CANCELLED") && trace.size()<=4))) {


					tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,
							"END", null));
					tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,
							"dummy1", null));
					tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,
							"dummy2", null));
					tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,
							"dummy1", null));


					casesImputed.add(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/ caseIdd);

					z++;
				}
				if(z==1153) {
					break;
				}

			}
		}

		


		//Collections.shuffle(tempArray);
		eventLogSortedByDateCopy.addAll(tempArray);
		System.out.println(eventLogSortedByDateCopy.size());
		return eventLogSortedByDateCopy;
	}

	public static ArrayList<Triplet<String, String, Date>> addLongHaulNoisyEvents(XLog log) {

		ArrayList<Triplet<String, String, Date>> eventLogSortedByDateCopy = sortEventLogByDate(log);

		ArrayList<Triplet<String,String, Date>> tempArray = new ArrayList<>();
		ArrayList<String> casestobeImputed = new ArrayList<>();
		int z=0;


		for(XTrace trace: log) {

			String caseIdd = XConceptExtension.instance().extractName(trace);
			String lastEventName = XConceptExtension.instance().extractName(trace.get(trace.size()-1));

			if((lastEventName.equals("A_DECLINED") && trace.size()<=4) || (lastEventName.equals("A_CANCELLED") && trace.size()<=4)) {




				casestobeImputed.add(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/ caseIdd);

				z++;
			}
			if(z==3722) {
				break;
			}

		}

		//tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,"END", null));
		for(int k=0;k<33;k++) {
			for(String casee: casestobeImputed) {
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy4", null));							
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy5", null));							
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy2", null));	
			}


		}



		//Collections.shuffle(tempArray);
		eventLogSortedByDateCopy.addAll(tempArray);
		System.out.println(eventLogSortedByDateCopy.size());
		return eventLogSortedByDateCopy;
	}

	public static ArrayList<Triplet<String, String, Date>> addLongHaulComplaintEvents(XLog log) {

		ArrayList<Triplet<String, String, Date>> eventLogSortedByDateCopy = sortEventLogByDate(log);

		ArrayList<Triplet<String,String, Date>> tempArray = new ArrayList<>();
		ArrayList<String> casestobeImputed = new ArrayList<>();
		int z=0;


	
		for(XTrace trace: log) {

			String caseIdd = XConceptExtension.instance().extractName(trace);
			String lastEventName = XConceptExtension.instance().extractName(trace.get(trace.size()-1));

			if((lastEventName.equals("A_DECLINED") && trace.size()<=4) || (lastEventName.equals("A_CANCELLED") && trace.size()<=4)) {




				casestobeImputed.add(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/ caseIdd);

				z++;
			}
			if(z==3722) {
				break;
			}

		}

		for(String cass: casestobeImputed) {
			tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/cass,
					"END", null));
		}

		//tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/caseIdd,"END", null));
		for(int k=0;k<25;k++) {
			for(String casee: casestobeImputed) {
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy1", null));							
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy2", null));							
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy1", null));
				tempArray.add(new Triplet<String, String, Date>(/*eventLogSortedByDateCopy.get(eventLogSortedByDateCopy.size()-index).getValue0()*/casee,
						"dummy2", null));
			}


		}


	

		//Collections.shuffle(tempArray);
		eventLogSortedByDateCopy.addAll(tempArray);
		System.out.println(eventLogSortedByDateCopy.size());
		return eventLogSortedByDateCopy;
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



	private static void checkCorrectness(XLog log, ArrayList<Triplet<String,String,Date>> eventLogSortedByDate) {
		for(int i=0; i<log.size();i++) {
			String caseId = XConceptExtension.instance().extractName(log.get(i));
			ArrayList<Integer> temp = new ArrayList<>();
			for(int j=0; j<log.get(i).size();j++) {
				String newEventName = XConceptExtension.instance().extractName(log.get(i).get(j));
				Date date = XTimeExtension.instance().extractTimestamp(log.get(i).get(j));
				for(int k=0; k<eventLogSortedByDate.size(); k++) {
					Triplet<String,String,Date> trip = eventLogSortedByDate.get(k);
					if(trip.getValue0().equals(caseId) && trip.getValue1().equals(newEventName) && trip.getValue2().compareTo(date)==0) {
						temp.add(k);
						break;

					}
				}
			}
			//check if temp is in order
			boolean sorted = Ordering.natural().isOrdered(temp);
			boolean sorted2 = isCollectionSorted(temp);
			if(sorted==true && sorted2==true) {
				System.out.println(temp);
				continue;
			}else {
				System.out.println("The case with Id: " + caseId + " is problematic");
			}
			//System.out.println(temp);
		}
		System.out.println("The checking is finished");
	}

	public static boolean isCollectionSorted(ArrayList list) {
		List copy = new ArrayList(list);
		Collections.sort(copy);
		return copy.equals(list);
	}

	
}
