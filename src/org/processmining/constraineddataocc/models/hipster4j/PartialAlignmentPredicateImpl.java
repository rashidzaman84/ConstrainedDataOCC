package org.processmining.constraineddataocc.models.hipster4j;

import java.util.List;

import org.processmining.constraineddataocc.models.PartialAlignment;

import es.usc.citius.hipster.model.impl.WeightedNode;
import es.usc.citius.hipster.util.Predicate;

public class PartialAlignmentPredicateImpl<L, S, T, N extends WeightedNode<Void, PartialAlignment.State<S, L, T>, Double>>
		implements Predicate<N> {

	private final List<L> trace;

	public PartialAlignmentPredicateImpl(List<L> trace) {
		super();
		this.trace = trace;
	}

	public boolean apply(N input) {
		return input.state().getNumLabelsExplained() == trace.size();
	}

}
