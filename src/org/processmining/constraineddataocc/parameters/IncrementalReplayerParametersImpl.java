package org.processmining.constraineddataocc.parameters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.processmining.basicutils.parameters.impl.PluginParametersImpl;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.onlineconformance.models.PriceList;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 * 
 * @author svzelst
 *
 * @param <M>
 *            type of model
 * @param <L>
 *            type of "label" move
 * @param <T>
 *            type of transition in model
 */
public class IncrementalReplayerParametersImpl<M, L, T> extends PluginParametersImpl {

	private final TObjectDoubleMap<L> labelMoveCosts = new TObjectDoubleHashMap<>();
	private final Map<L, Collection<T>> labelToModelElementsMap = new HashMap<>();
	private M model;
	private final Map<T, L> modelElementsToLabelMap = new HashMap<>();

	private final TObjectDoubleMap<T> modelMoveCosts = new TObjectDoubleHashMap<>();
	private int numberOfThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	private IncrementalReplayer.SearchAlgorithm searchAlgorithm;
	private double synchronousMoveCosts = 0d;

	private double unknownLabelCosts = 1d;
	private boolean useMultiThreading = true;

	private boolean useSolutionUpperBound = true;

	private boolean isExperiment = false;

	public boolean isExperiment() {
		return isExperiment;
	}

	public void setExperiment(boolean isExperiment) {
		this.isExperiment = isExperiment;
	}

	public double getLabelCost(L label) {
		return labelMoveCosts.containsKey(label) ? labelMoveCosts.get(label) : unknownLabelCosts;
	}

	public TObjectDoubleMap<L> getLabelMoveCosts() {
		return labelMoveCosts;
	}

	public Map<L, Collection<T>> getLabelToModelElementsMap() {
		return labelToModelElementsMap;
	}

	public M getModel() {
		return model;
	}

	public Map<T, L> getModelElementsToLabelMap() {
		return modelElementsToLabelMap;
	}

	public double getModelMoveCost(T moveInModel) {
		return modelMoveCosts.get(moveInModel);
	}

	public TObjectDoubleMap<T> getModelMoveCosts() {
		return modelMoveCosts;
	}

	public int getNumberOfThreads() {
		return numberOfThreads;
	}

	public PriceList<L, T> getPriceList() {
		return PriceList.Factory.construct(labelMoveCosts, modelMoveCosts, unknownLabelCosts, synchronousMoveCosts,
				modelElementsToLabelMap);
	}

	public IncrementalReplayer.SearchAlgorithm getSearchAlgorithm() {
		return searchAlgorithm;
	}

	public double getSynchronousMoveCosts() {
		return synchronousMoveCosts;
	}

	public double getUnknownLabelCosts() {
		return unknownLabelCosts;
	}

	public boolean isUseMultiThreading() {
		return useMultiThreading;
	}

	public void setLabelMoveCosts(TObjectDoubleMap<L> lmCosts) {
		labelMoveCosts.clear();
		labelMoveCosts.putAll(lmCosts);
	}

	public void setLabelToModelElementsMap(Map<L, Collection<T>> map) {
		labelToModelElementsMap.clear();
		labelToModelElementsMap.putAll(map);
	}

	public void setModel(M model) {
		this.model = model;
	}

	public void setModelElementsToLabelMap(Map<T, L> map) {
		modelElementsToLabelMap.clear();
		modelElementsToLabelMap.putAll(map);
	}

	public void setModelMoveCosts(TObjectDoubleMap<T> mmCosts) {
		modelMoveCosts.clear();
		modelMoveCosts.putAll(mmCosts);
	}

	public void setNumberOfThreads(int numberOfThreads) {
		this.numberOfThreads = numberOfThreads;
	}

	public void setSearchAlgorithm(IncrementalReplayer.SearchAlgorithm searchAlgorithm) {
		this.searchAlgorithm = searchAlgorithm;
	}

	public void setSynchronousMoveCosts(final double synchronousMoveCosts) {
		this.synchronousMoveCosts = synchronousMoveCosts;
	}

	public void setUnknownLabelCosts(final double unknownLabelCosts) {
		this.unknownLabelCosts = unknownLabelCosts;
	}

	public void setUseMultiThreading(boolean useMultiThreading) {
		this.useMultiThreading = useMultiThreading;
	}

	public boolean isUseSolutionUpperBound() {
		return useSolutionUpperBound;
	}

	public void setUseSolutionUpperBound(boolean useSolutionUpperBound) {
		this.useSolutionUpperBound = useSolutionUpperBound;
	}

}
