package org.processmining.constraineddataocc.algorithms.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.IncrementalReplayer;
import org.processmining.constraineddataocc.helper.StatesCalculator;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.models.PartialAlignment.State;
import org.processmining.constraineddataocc.models.hipster4j.PartialAlignmentCostFunctionImpl;
import org.processmining.constraineddataocc.models.hipster4j.PartialAlignmentNaiveHeuristicFunctionImpl;
import org.processmining.constraineddataocc.models.hipster4j.PartialAlignmentPredicateImpl;
import org.processmining.constraineddataocc.models.hipster4j.PartialAlignmentStateTransitionFunctionImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.onlineconformance.models.MeasurementAwarePartialAlignment;
import org.processmining.onlineconformance.models.ModelSemantics;
import org.processmining.onlineconformance.models.Move;

import es.usc.citius.hipster.algorithm.Algorithm;
import es.usc.citius.hipster.algorithm.Hipster;
import es.usc.citius.hipster.model.function.CostFunction;
import es.usc.citius.hipster.model.function.HeuristicFunction;
import es.usc.citius.hipster.model.function.TransitionFunction;
import es.usc.citius.hipster.model.impl.WeightedNode;
import es.usc.citius.hipster.model.problem.ProblemBuilder;
import es.usc.citius.hipster.model.problem.SearchProblem;
import es.usc.citius.hipster.util.Predicate;

public class IncrementalRevBasedReplayerImpl1<M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>>
		implements IncrementalReplayer<M, C, S, T, L, A, P> {

	private final Map<C, A> dataStore;
	private final S finalState;
	private final S initialState;
	private final ModelSemantics<M, S, T> modelSemantics;
	private final P parameters;
	private final Map<T, L> labelMap;
	private final Map<C, List<Double>> compoundCost;
	private final Map<String, ArrayList<Triplet<String, Integer, Integer>>> eventsCategorisation;  //<Case<event,window,PAtype>>

	public IncrementalRevBasedReplayerImpl1(final S initialStateInModel, final S finalStateInModel,
			final Map<C, A> dataStore, final ModelSemantics<M, S, T> modelSemantics, final P parameters,
			final Map<T, L> labelMap) {
		this.initialState = initialStateInModel;
		this.finalState = finalStateInModel;
		this.parameters = parameters;
		this.dataStore = dataStore;
		this.modelSemantics = modelSemantics;
		this.labelMap = labelMap;
		this.compoundCost = new HashMap<>();
		this.eventsCategorisation = new HashMap<>();
	}

	public Map<C, A> getDataStore() {
		return dataStore;
	}
	
	public Map<C, List<Double>> getCompoundCost() {
		return compoundCost;
	}
	
	public Map<String, ArrayList<Triplet<String, Integer, Integer>>> getEventsCategorisation() {
		return eventsCategorisation;
	}

	public S getFinalStateInModel() {
		return finalState;
	}

	public P getParameters() {
		return parameters;
	}

	public PartialAlignment.State<S, L, T> getInitialState() {
		return PartialAlignment.State.Factory.construct(getInitialStateInModel(), 0, null, null);
		//return PartialAlignment.State.Factory.construct(getInitialStateInModel());
	}

	public S getInitialStateInModel() {
		return initialState;
	}

	public ModelSemantics<M, S, T> getModelSemantics() {
		return modelSemantics;
	}

	public A processEvent(C c, L l) { 									//only used in TPE
			
		A previousAlignment = dataStore.get(c);
				
		S state = previousAlignment == null ? getInitialStateInModel() : previousAlignment.getState().getStateInModel();
		A alignment = tryToExecuteSynchronous(state, l, previousAlignment);
		
		if (alignment == null) {
			alignment = searchForNewAlignment(c, l, previousAlignment, state);
		}
		
		//trackCosts(alignment, c);
		if(trimAlignment(alignment)) {
			alignment = resetAlignment(alignment);
		}		
		//verifyAlignment(alignment);
		this.dataStore.put(c, alignment);
		return alignment;
	}
	
	public A processEvent(C c, L l, int window) {						//used in Cost and Event Characterisation
			
		A previousAlignment = dataStore.get(c);	
		
		S state = previousAlignment == null ? getInitialStateInModel() : previousAlignment.getState().getStateInModel();
		A alignment = tryToExecuteSynchronous(state, l, previousAlignment);
		if(eventsCategorisation.containsKey(c)) {
			eventsCategorisation.get(c).add(new Triplet<String, Integer, Integer>((String)l, window, 0));
		}else {
			ArrayList<Triplet<String, Integer, Integer>> temp = new ArrayList<>();
			temp.add(new Triplet<String, Integer, Integer>((String)l, window, 0));
			eventsCategorisation.put((String)c, temp);
		}
		
		if (alignment == null) {
			alignment = searchForNewAlignment(c, l, previousAlignment, state);
			eventsCategorisation.get(c).set(eventsCategorisation.get(c).size()-1, new Triplet<String, Integer, Integer>((String)l, window, 2));
			}
		
		
		if(trimAlignment(alignment)) {
			alignment = resetAlignment(alignment);
		}	
		
		if(StatesCalculator.getNumberOfStates(alignment) > getParameters().getLookBackWindow()) { //to check the correctness of trimming
			System.out.println("I have more states than allowed...");
		}
		//verifyAlignment(alignment);
		this.dataStore.put(c, alignment);
		return alignment;
	}

	@SuppressWarnings("rawtypes")
	private Algorithm.SearchResult searchInSynchronousProduct(final State<S, L, T> startState, List<L> trace,
			double upperBound) {
		TransitionFunction<Void, PartialAlignment.State<S, L, T>> tf = new PartialAlignmentStateTransitionFunctionImpl<>(
				modelSemantics, trace, parameters.getPriceList());
		CostFunction<Void, PartialAlignment.State<S, L, T>, Double> cf = new PartialAlignmentCostFunctionImpl<>();
		HeuristicFunction<PartialAlignment.State<S, L, T>, Double> hf = new PartialAlignmentNaiveHeuristicFunctionImpl<>(
				trace, labelMap.values(), parameters.getPriceList());
		Predicate<WeightedNode<Void, PartialAlignment.State<S, L, T>, Double>> pred = new PartialAlignmentPredicateImpl<L, S, T, WeightedNode<Void, PartialAlignment.State<S, L, T>, Double>>(
				trace);
		SearchProblem<Void, PartialAlignment.State<S, L, T>, WeightedNode<Void, PartialAlignment.State<S, L, T>, Double>> problem = ProblemBuilder
				.create().initialState(startState).defineProblemWithoutActions().useTransitionFunction(tf)
				.useCostFunction(cf).useHeuristicFunction(hf).build();
		switch (getParameters().getSearchAlgorithm()) {
			case A_STAR :
			default :
				return Hipster.createAStar(problem, upperBound).search(pred);
		}
	}

	private List<L> fetchTrace(final C c, final L newLabel) {
		List<L> trace = new ArrayList<>();
		if (dataStore.containsKey(c)) {
			trace.addAll(dataStore.get(c).projectOnLabels());
		}
		trace.add(newLabel);
		return trace;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected A searchForNewAlignment(final C c, final L l, final A previousAlignment, final S state) {
		Double upperBound = Double.MAX_VALUE;
		if (getParameters().isUseSolutionUpperBound()) {
			upperBound = getParameters().getLabelCost(l);
			if (previousAlignment != null) {
				upperBound += previousAlignment.getCost();
			}
		}
		final List<L> trace = fetchTrace(c, l);
		Algorithm.SearchResult searchResult = searchInSynchronousProduct(getStartState(previousAlignment), trace,
				upperBound);
		State<S, L, T> alignmentState = (State<S, L, T>) searchResult.getGoalNode().state();
		if (alignmentState.getNumLabelsExplained() < trace.size()) {
			State<S, L, T> previousAlignmentState = previousAlignment == null ? getInitialState()
					: previousAlignment.getState();
			alignmentState = PartialAlignment.State.Factory.construct(state, trace.size(), previousAlignmentState,
					(Move<L, T>) Move.Factory.construct(l, null, getParameters().getLabelCost(l)));
		}
		A alignment = null;
		if (getParameters().isExperiment()) {
			Algorithm.SearchResult optimalSearchResult = searchInSynchronousProduct(getInitialState(), trace,
					Double.MAX_VALUE);
			A optimalAlignment = (A) PartialAlignment.Factory
					.construct((State<S, L, T>) optimalSearchResult.getGoalNode().state());
			A alignmentNonMeasurementAware = (A) PartialAlignment.Factory.construct(alignmentState);
			alignment = (A) MeasurementAwarePartialAlignment.Factory.construct(alignmentState,
					searchResult.getTotalEnQueuedNodes(), searchResult.getIterations(),
					searchResult.getAverageQueueSize(), searchResult.getElapsed(),
					alignmentNonMeasurementAware.getCost() - optimalAlignment.getCost(), true,
					searchResult.getTraversedEdges());
		} else {
			alignment = (A) PartialAlignment.Factory.construct(alignmentState);
		}
		return alignment;
	}

	protected State<S, L, T> getStartState(final A previousAlignment) {
		if (previousAlignment == null || previousAlignment.size() < (getParameters().getLookBackWindow()-1)) { // the second condition can only be true when the case never reached the limit w. Once reached, then it will always be false.
			return getInitialState();
		} else {
			State<S, L, T> state = previousAlignment.getState();
			int i = 0;
			while (state.getParentState() != null && i < getParameters().getLookBackWindow()) {
				state = state.getParentState();
				i++;
			}
			if(state.getParentState() !=null) {
				System.out.println("I managed to escape...."); //hints at a problem
			}
			return state == null ? getInitialState() : state;
		}	

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private A tryToExecuteSynchronous(S s, L l, A previousAlignment) {
		A alignment = null;
		for (T t : getModelSemantics().getEnabledTransitions(s)) {
			if (getLabelMap().containsKey(t) && getLabelMap().get(t).equals(l)) {
				S state = getModelSemantics().execute(s, t);
				int labs = previousAlignment == null ? 1 : previousAlignment.getState().getNumLabelsExplained() + 1;
				State<S, L, T> parent = previousAlignment == null ? null : previousAlignment.getState();
				Move<L, T> move = Move.Factory.construct(l, t, getParameters().getSynchronousMoveCosts());
				State<S, L, T> alignmentState = PartialAlignment.State.Factory.construct(state, labs, parent, move);
				if (getParameters().isExperiment()) {
					List<L> trace = new ArrayList<L>();
					double costs = 0d;
					if (previousAlignment != null) {
						trace.addAll(previousAlignment.projectOnLabels());
						costs = previousAlignment.getCost();
					}
					costs += getParameters().getSynchronousMoveCosts();
					trace.add(l);
					Algorithm.SearchResult optimalSearchResult = searchInSynchronousProduct(getInitialState(), trace,
							Double.MAX_VALUE);
					A optimalAlignment = (A) PartialAlignment.Factory
							.construct((State<S, L, T>) optimalSearchResult.getGoalNode().state());
					alignment = (A) MeasurementAwarePartialAlignment.Factory.construct(alignmentState, -1, -1, -1, -1,
							costs - optimalAlignment.getCost(), false, -1);
				} else {
					alignment = (A) PartialAlignment.Factory.construct(alignmentState);
				}
				break;
			}
		}
		return alignment;
	}

	public Map<T, L> getLabelMap() {
		return labelMap;
	}

	public IncrementalReplayer.Strategy getStrategy() {
		return Strategy.REVERT_BASED;
	}
	
	//------------
	private boolean trimAlignment(A alignment) {
		if ((alignment.size()  < getParameters().getLookBackWindow()) || (StatesCalculator.getNumberOfStates(alignment) <= getParameters().getLookBackWindow())) { //the first condition will only be true when the case has not reached limit w.
			return false;
		}else {
			//set the parent state of the state at position i to NULL;
			State<S, L, T> state = alignment.getState();
			int i = 1;
			while (state.getParentState() != null && i < (getParameters().getLookBackWindow()-1)) { //i < getParameters().getLookBackWindow() because we are adding ne dummy state
				state = state.getParentState();
				i++;
			}			
			
			double residualCosts =  state.getParentState() == null? 0 : calculateResidualCosts(state.getParentState());						
			state.setParentState(PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), 0, null, Move.Factory.construct(null,null , residualCosts)));
			//state.setParentState(PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel(), state.getParentState().getNumLabelsExplained(), null, state.getParentState().getParentMove()));
			//state.setParentState(PartialAlignment.State.Factory.construct(state.getParentState().getStateInModel()));
			return true;
		}		
		
	}
	
	public double calculateResidualCosts(State<S, L, T> statesToBeForgotten) {
		double costs = 0d;
		costs += statesToBeForgotten.getParentMove().getCost();
		while (statesToBeForgotten.getParentState() != null && statesToBeForgotten.getParentState().getParentMove() != null) {
			statesToBeForgotten = statesToBeForgotten.getParentState();
			costs += statesToBeForgotten.getParentMove().getCost();
		}
		return costs;
	}
	
	public A resetAlignment(A alignment) {
		State<S, L, T> state = alignment.getState();		
		return (A) PartialAlignment.Factory.construct(state);
	}

}


