package org.processmining.constraineddataocc.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.javatuples.Triplet;

public class ResultsCollection2 {
	
	public int featureSize;
	public int caseLimitSize;
	public LinkedHashMap<String, Double> costRecords;
	public int sumOfForgottenPrematureCases;
	public int sumOfEternalPrematureCases;
	public int conformantAsConformant;
	public int conformantAsNonConformant;
	public int nonConformantAsConformant;
	public int nonConformantAsNonConformantExactlyEstimated;
	public int nonConformantAsNonConformantUnderEstimated;
	public int nonConformantAsNonConformantOverEstimated;
	public double ATPE;
	public int maxStates=0;
	public ArrayList<Integer> allFoldsMaxStates;
	public int fold;
	public ArrayList<Triplet<Integer, String, Double>> foldsResults;
	
	public ResultsCollection2() {		
		this.costRecords = new LinkedHashMap<>();
		this.allFoldsMaxStates = new ArrayList<>();
	}
	
	

}
