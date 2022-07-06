package org.processmining.constraineddataocc.helper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.javatuples.Triplet;

public class PublishResults {

	public static void writeToFilesCC(HashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice, String outputFolderPath) {

		//-------------- CC information

				StringBuilder stringBuilderHeader = new StringBuilder();
				stringBuilderHeader.append("Case,");

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderHeader.append(entryOuter.getKey() + ",");
				}		

				StringBuilder stringBuilderCosts = new StringBuilder();

				boolean first = true;

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					if(first) {
						for(Entry<String, Double> entryInner : entryOuter.getValue().costRecords.entrySet()) {
							String caseId = entryInner.getKey();
							stringBuilderCosts.append(caseId + ",");				

							for(Entry<String, ResultsCollection2> records : globalResults.entrySet()) {
								stringBuilderCosts.append(records.getValue().costRecords.get(caseId) + ",");						
							}
							//stringBuilderCosts.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
							//stringBuilderCosts.append(entryOuter.getValue().sumOfEternalPrematureCases);
							stringBuilderCosts.append("\n");
						}
						first=false;				
					}			
				}	

				String outputFilePath = outputFolderPath +  fileName + "_CC.csv";
				File file = new File(outputFilePath);

				BufferedWriter bf = null;

				if(file.exists() && !file.isDirectory()){
					try {
						bf = new BufferedWriter(new FileWriter(file, false));
						bf.newLine();
						bf.write(stringBuilderHeader.toString());
						bf.write(stringBuilderCosts.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else {
					try {
						bf = new BufferedWriter(new FileWriter(file));
						bf.write(stringBuilderHeader.toString());
						bf.newLine();
						bf.write(stringBuilderCosts.toString());

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

				//-------------- States information

				StringBuilder stringBuilderStates = new StringBuilder();
				//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderStates.append(entryOuter.getKey() + ",");
				}

				stringBuilderStates.append("\n");

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderStates.append(entryOuter.getValue().maxStates + ",");
				}

				System.out.println(stringBuilderStates.toString());	

				outputFilePath = outputFolderPath + fileName + "_States.csv";
				file = new File(outputFilePath);
				BufferedWriter bf2 = null;

				try {	
					bf2 = new BufferedWriter(new FileWriter(file, true));
					bf2.write(stringBuilderStates.toString());
					bf2.flush();
					bf2.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//-------------- additional information

				StringBuilder stringBuilderCasesMeta = new StringBuilder();
				//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");
				stringBuilderCasesMeta.append("W and N combination, Forgotten Premature, Forgotten Eternal");
				stringBuilderCasesMeta.append("\n");
				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderCasesMeta.append(entryOuter.getKey() + ",");
					stringBuilderCasesMeta.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
					stringBuilderCasesMeta.append(entryOuter.getValue().sumOfEternalPrematureCases + "\n");
				}

				System.out.println(stringBuilderCasesMeta.toString());	

				outputFilePath = outputFolderPath + fileName + "_meta.csv";
				file = new File(outputFilePath);
				BufferedWriter bf3 = null;

				try {	
					bf3 = new BufferedWriter(new FileWriter(file, true));
					bf3.write(stringBuilderCasesMeta.toString());
					bf3.flush();
					bf3.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
	}
	
	public static void writeToFilesCC_(HashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice, String outputFolderPath) {

		//-------------- CC information

				StringBuilder stringBuilderHeader = new StringBuilder();
				stringBuilderHeader.append("Fold,Case,");
				
				//LinkedHashMap<String, ResultsCollection2> globalResults = new LinkedHashMap<>();

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderHeader.append(entryOuter.getKey() + ",");
				}		

				StringBuilder stringBuilderCosts = new StringBuilder();

				boolean first = true;

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					if(first) {
						for(Triplet<Integer, String, Double> entryInner : entryOuter.getValue().foldsResults) {
							int fold = entryInner.getValue0();
							String caseId = entryInner.getValue1();
							
							stringBuilderCosts.append(fold + "," + caseId + ",");
											

							for(Entry<String, ResultsCollection2> records : globalResults.entrySet()) {
								for(Triplet<Integer, String, Double> entry : records.getValue().foldsResults) {
									if(entry.getValue0() == fold && entry.getValue1().equals(caseId)) {
										stringBuilderCosts.append(entry.getValue2() + ",");
										break;
									}
								}					
							}
							//stringBuilderCosts.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
							//stringBuilderCosts.append(entryOuter.getValue().sumOfEternalPrematureCases);
							stringBuilderCosts.append("\n");
						}
						first=false;				
					}			
				}	

				String outputFilePath = outputFolderPath +  fileName + "_CC.csv";
				File file = new File(outputFilePath);

				BufferedWriter bf = null;

				if(file.exists() && !file.isDirectory()){
					try {
						bf = new BufferedWriter(new FileWriter(file, false));
						bf.newLine();
						bf.write(stringBuilderHeader.toString());
						bf.write(stringBuilderCosts.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else {
					try {
						bf = new BufferedWriter(new FileWriter(file));
						bf.write(stringBuilderHeader.toString());
						bf.newLine();
						bf.write(stringBuilderCosts.toString());

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

				//-------------- States information

				StringBuilder stringBuilderATPE = new StringBuilder();
				//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderATPE.append(entryOuter.getKey() + ",");
				}

				stringBuilderATPE.append("\n");

				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderATPE.append(entryOuter.getValue().maxStates + ",");
				}

				System.out.println(stringBuilderATPE.toString());	

				outputFilePath = outputFolderPath + fileName + "_States.csv";
				file = new File(outputFilePath);
				BufferedWriter bf2 = null;

				try {	
					bf2 = new BufferedWriter(new FileWriter(file, true));
					bf2.write(stringBuilderATPE.toString());
					bf2.flush();
					bf2.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//-------------- additional information

				StringBuilder stringBuilderCasesMeta = new StringBuilder();
				//System.out.println(" W and N combination, Max. states, Forgotten Premature, Forgotten Eternal");
				stringBuilderCasesMeta.append("W and N combination, Forgotten Premature, Forgotten Eternal");
				stringBuilderCasesMeta.append("\n");
				for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
					stringBuilderCasesMeta.append(entryOuter.getKey() + ",");
					stringBuilderCasesMeta.append(entryOuter.getValue().sumOfForgottenPrematureCases + ",");
					stringBuilderCasesMeta.append(entryOuter.getValue().sumOfEternalPrematureCases + "\n");
				}

				System.out.println(stringBuilderCasesMeta.toString());	

				outputFilePath = outputFolderPath + fileName + "_meta.csv";
				file = new File(outputFilePath);
				BufferedWriter bf3 = null;

				try {	
					bf3 = new BufferedWriter(new FileWriter(file, true));
					bf3.write(stringBuilderCasesMeta.toString());
					bf3.flush();
					bf3.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
	}

	public static void writeToFilesATPE(HashMap<String, ResultsCollection2> globalResults, String fileName, String classifierChoice, String outputFolderPath) {

		StringBuilder stringBuilderATPE = new StringBuilder();

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getKey() + ",");
		}	

		stringBuilderATPE.append("\n");

		for(Entry<String, ResultsCollection2> entryOuter : globalResults.entrySet()) {
			stringBuilderATPE.append(entryOuter.getValue().ATPE + ",");						
		}	

		String outputFilePath = outputFolderPath + fileName + "_ATPE.csv";
		File file = new File(outputFilePath);

		BufferedWriter bf = null;

		
			try {
				bf = new BufferedWriter(new FileWriter(file));
				bf.write(stringBuilderATPE.toString());
				bf.flush();
				bf.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		

	}

}
