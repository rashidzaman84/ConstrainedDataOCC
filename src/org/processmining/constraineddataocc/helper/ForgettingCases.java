package org.processmining.constraineddataocc.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.processmining.onlineconformance.models.PartialAlignment;
import org.processmining.onlineconformance.models.PartialAlignment.State;

public class ForgettingCases {
	

	public static <A> String selectCaseToBeForgotten(Map<String, A> datastore) {
		int minTraceLength = Integer.MAX_VALUE;
		String caseIdToBeForgotten = null;
		
		for(Entry<String, A> entry: datastore.entrySet()) {
			PartialAlignment partialAlignment = (PartialAlignment) entry.getValue();
			
			if (partialAlignment.size()<minTraceLength) {
		    	caseIdToBeForgotten = entry.getKey();
		    	minTraceLength = partialAlignment.size();
		    }
			
			if(partialAlignment.getState()==null || partialAlignment.getCost()>0.0) {  //any other condition??
				continue;
			}			
			return entry.getKey();			
		}
		
		return caseIdToBeForgotten;
	}
	
	public static <A> String selectCaseToBeForgotten(Map<String, A> datastore, String forgettingPolicy) {
		
		if(forgettingPolicy.equals("longest non-conf")) {
			int maxTraceLength = Integer.MIN_VALUE;
			String caseIdToBeForgotten = null;
			
			for(Entry<String, A> entry: datastore.entrySet()) {
				PartialAlignment partialAlignment = (PartialAlignment) entry.getValue();
				
				if (partialAlignment.size()>maxTraceLength) {
			    	caseIdToBeForgotten = entry.getKey();
			    	maxTraceLength = partialAlignment.size();
			    }
				
				if(partialAlignment.getState()==null || partialAlignment.getCost()>0.0) {  //any other condition??
					continue;
				}			
				return entry.getKey();			
			}
			
			return caseIdToBeForgotten;
		}else{
			//System.out.println("The forgetting policy is: SHORTEST-Non-COnformant");
			return selectCaseToBeForgotten(datastore);
		}
		
	}
	
	public static <A> String selectCaseToBeForgotten(Map<String, A> datastore, HashSet<String> caseEndingEvents) {
		LinkedHashMap<String, Integer> casesForgettingPriority = new LinkedHashMap<>();
		boolean priority1, priority2, priority3, priority4;
		priority1 = priority2 = priority3 = priority4 = false;
		
		for(Entry<String, A> entry: datastore.entrySet()) {
			PartialAlignment partialAlignment = (PartialAlignment) entry.getValue();
			if(partialAlignment.getCost()==0) {
				//System.out.println(partialAlignment.toString());
				ArrayList<String> temp = (ArrayList<String>) partialAlignment.projectOnLabels();
				if(caseEndingEvents.contains(temp.get(temp.size()-1))) {
					return entry.getKey();
				}else if(!priority1 && getStartState(partialAlignment).getParentMove().getEventLabel()==null){
					priority1 = priority2= priority3 = priority4 = true;
					casesForgettingPriority.put(entry.getKey(), 1);
					continue;
				}else if(!priority2){
					priority2= priority3 = priority4 = true;
					casesForgettingPriority.put(entry.getKey(), 2);
					continue;
				}/*else {
					System.out.println("A case with cost = 0 iswithout an assgined priority");
				}*/
			}else {				
				if(!priority3 && partialAlignment.getState().getStateInModel()!=null){
					casesForgettingPriority.put(entry.getKey(), 3);
					priority3 = priority4= true;
					//System.out.println("A priority 1 case has been found, so now we only look for a fresh case");
					continue;
				}else if(!priority4){
					casesForgettingPriority.put(entry.getKey(), 4);
					priority4 = true;
					//System.out.println("A priority 2 case has been found, so now we only search for a fresh or a priority 1 case");
					continue;
				}/*else{
					System.out.println("A case with cost>1 is without an assgined priority");
				}*/
				
			}
			
		}
		
		String toBeForgotten = Collections.min(casesForgettingPriority.entrySet(), Map.Entry.comparingByValue()).getKey();
		return toBeForgotten;
		
		
	}
	
	protected static State getStartState(final PartialAlignment previousAlignment) {
		
		State state = previousAlignment.getState();
		//int i = 1;
		while (state.getParentState() != null /*&& i < getParameters().getLookBackWindow()*/) {
			state = state.getParentState();
			//i++;
		}
		if(state.getParentState() !=null) {
			System.out.println("I managed to escape...."); //hints at a problem
		}
//		if(!(state instanceof NullParentState) && i<getParameters().getLookBackWindow() ) {  // i< look back .i.e., w, then it can be an alignment with a NullParentState from Rc 
//																							 //or it can be a shorter case, in the latter case getInitialState() is sure
//			//System.out.println(++counter);
//			return getInitialState();
//		}else  
		return state;
		
		

	}
		
}