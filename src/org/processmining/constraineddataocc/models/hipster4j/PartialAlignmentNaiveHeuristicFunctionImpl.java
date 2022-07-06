package org.processmining.constraineddataocc.models.hipster4j;

import java.util.Collection;
import java.util.List;

import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.models.PartialAlignment.State;
import org.processmining.onlineconformance.models.PriceList;

import es.usc.citius.hipster.model.function.HeuristicFunction;

public class PartialAlignmentNaiveHeuristicFunctionImpl<S, L, T>
		implements HeuristicFunction<PartialAlignment.State<S, L, T>, Double> {

	private final Collection<L> potentiallySynchronous;

	private final PriceList<L, T> priceList;

	private final List<L> trace;

	public PartialAlignmentNaiveHeuristicFunctionImpl(List<L> trace, Collection<L> potentiallySynchronous,
			PriceList<L, T> priceList) {
		super();
		this.trace = trace;
		this.potentiallySynchronous = potentiallySynchronous;
		this.priceList = priceList;
	}

	public Double estimate(State<S, L, T> state) {
		List<L> todo = trace.subList(state.getNumLabelsExplained(), trace.size());
		double costs = 0;
		for (L l : todo) {
			if (potentiallySynchronous.contains(l)) {
				costs += priceList.getPriceOfSynchronous();
			} else {
				costs += priceList.getPriceOfLabel(l);
			}
		}
		return costs;

	}

}
