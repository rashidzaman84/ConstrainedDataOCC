package org.processmining.constraineddataocc.models.hipster4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.onlineconformance.models.ModelSemantics;
import org.processmining.onlineconformance.models.Move;
import org.processmining.onlineconformance.models.PriceList;

import es.usc.citius.hipster.model.function.impl.StateTransitionFunction;

public class PartialAlignmentStateTransitionFunctionImpl<M, L, T, S>
		extends StateTransitionFunction<PartialAlignment.State<S, L, T>> {

	private final ModelSemantics<M, S, T> semantics;
	private final List<L> trace;
	private final PriceList<L, T> priceList;

	public PartialAlignmentStateTransitionFunctionImpl(final ModelSemantics<M, S, T> semantics, final List<L> trace,
			final PriceList<L, T> priceList) {
		this.semantics = semantics;
		this.trace = trace;
		this.priceList = priceList;
	}

	public PriceList<L, T> getPriceList() {
		return priceList;
	}

	public ModelSemantics<M, S, T> getSemantics() {
		return semantics;
	}

	private List<PartialAlignment.State<S, L, T>> getSynchronousMoves(PartialAlignment.State<S, L, T> state) {
		List<PartialAlignment.State<S, L, T>> successors = new ArrayList<>();
		final Collection<T> enabled = getSemantics().getEnabledTransitions(state.getStateInModel());
		final L freeLabel = state.getNumLabelsExplained() < trace.size() ? trace.get(state.getNumLabelsExplained())
				: null;
		for (T moveInModel : enabled) {
			if (freeLabel != null && getPriceList().getTransitionToLabelsMap().containsKey(moveInModel)
					&& getPriceList().getTransitionToLabelsMap().get(moveInModel).equals(freeLabel)) {
				final S newStateInModel = getSemantics().execute(state.getStateInModel(), moveInModel);
				successors.add(PartialAlignment.State.Factory.construct(newStateInModel,
						state.getNumLabelsExplained() + 1, state,
						Move.Factory.construct(freeLabel, moveInModel, getPriceList().getPriceOfSynchronous())));
			}
		}
		return successors;
	}

	private PartialAlignment.State<S, L, T> getLabelMove(PartialAlignment.State<S, L, T> state) {
		final L freeLabel = state.getNumLabelsExplained() < trace.size() ? trace.get(state.getNumLabelsExplained())
				: null;
		PartialAlignment.State<S, L, T> newState = null;
		if (freeLabel != null) {
			newState = PartialAlignment.State.Factory.construct(state.getStateInModel(),
					state.getNumLabelsExplained() + 1, state,
					Move.Factory.construct(freeLabel, (T) null, getPriceList().getPriceOfLabel(freeLabel)));
		}
		return newState;
	}

	private List<PartialAlignment.State<S, L, T>> getModelMoves(PartialAlignment.State<S, L, T> state) {
		List<PartialAlignment.State<S, L, T>> successors = new ArrayList<>();
		final Collection<T> enabled = getSemantics().getEnabledTransitions(state.getStateInModel());
		for (T moveInModel : enabled) {
			final S newStateInModel = getSemantics().execute(state.getStateInModel(), moveInModel);
			successors.add(PartialAlignment.State.Factory.construct(newStateInModel, state.getNumLabelsExplained(),
					state,
					Move.Factory.construct((L) null, moveInModel, getPriceList().getPriceOfTransition(moveInModel))));
		}
		return successors;
	}

	public List<L> getTrace() {
		return trace;
	}

	public Iterable<PartialAlignment.State<S, L, T>> successorsOf(PartialAlignment.State<S, L, T> arg0) {
		List<PartialAlignment.State<S, L, T>> successors = new ArrayList<>();
		successors.addAll(getSynchronousMoves(arg0));
		PartialAlignment.State<S, L, T> labelMove = getLabelMove(arg0);
		if (labelMove != null) {
			successors.add(labelMove);
		}
		successors.addAll(getModelMoves(arg0));
		return successors;

	}
}
