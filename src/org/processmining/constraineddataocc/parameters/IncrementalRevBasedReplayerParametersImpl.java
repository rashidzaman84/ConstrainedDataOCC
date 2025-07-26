package org.processmining.constraineddataocc.parameters;

public class IncrementalRevBasedReplayerParametersImpl<M, L, T> extends /*org.processmining.onlineconformance.parameters.IncrementalRevBasedReplayerParametersImpl<M, L, T>*/ IncrementalReplayerParametersImpl<M, L, T> {

	private int lookBackWindow = Integer.MAX_VALUE;
	private boolean lookBackWindowTypeEvent = false;
	private int maxCasesToStore = Integer.MAX_VALUE;
	private String storeType = null;
	private String forgettingCriteria = null;

	public String getForgettingCriteria() {
		return forgettingCriteria;
	}

	public void setForgettingCriteria(String forgettingCriteria) {
		this.forgettingCriteria = forgettingCriteria;
	}

	public String getStoreType() {
		return storeType;
	}

	public void setStoreType(String storeType) {
		this.storeType = storeType;
	}

	public int getMaxCasesToStore() {
		return maxCasesToStore;
	}

	public void setMaxCasesToStore(int maxCasesToStore) {
		this.maxCasesToStore = maxCasesToStore;
	}

	public int getLookBackWindow() {
		return lookBackWindow;
	}

	public void setLookBackWindow(int lookBackWindow) {
		this.lookBackWindow = lookBackWindow;
	}
		
	public boolean getlookBackWindowTypeEvent() {
		return lookBackWindowTypeEvent;
	}

	public void setlookBackWindowTypeEvent(boolean lookBackWindowTypeEvent) {
		this.lookBackWindowTypeEvent = lookBackWindowTypeEvent;
	}

}
