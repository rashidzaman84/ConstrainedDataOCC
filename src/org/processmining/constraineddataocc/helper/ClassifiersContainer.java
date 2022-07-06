package org.processmining.constraineddataocc.helper;

import java.util.ArrayList;
import java.util.HashMap;

public class ClassifiersContainer {
	
	public int featureSize;
	public int caseLimitSize;
	public String storageFolder;
	public ArrayList<WekaDataSetsCreation> foldsclassifiers;
	public HashMap<Integer, WekaDataSetsCreation> classifiersRepository;
	public boolean endMarkerEnabled = false;
	public String forgettingPolicy;
	public String StoreType;
	public String classifiersRepositoryPath;
	public Integer fold;
	public String fileName;
	
	public ClassifiersContainer() {		
		this.foldsclassifiers =  new ArrayList<>();
		this.classifiersRepository = new HashMap<>();
	}
	
	

}
