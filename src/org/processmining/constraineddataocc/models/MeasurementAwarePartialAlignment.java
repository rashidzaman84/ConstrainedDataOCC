package org.processmining.constraineddataocc.models;

import java.util.ArrayList;
import java.util.List;

import org.processmining.onlineconformance.models.Move;

public interface MeasurementAwarePartialAlignment<L, T, S> extends PartialAlignment<L, T, S> {

	public class Factory {

		public static <S, L, T> MeasurementAwarePartialAlignment<L, T, S> construct(final State<S, L, T> state,
				final long enqueuedNodes, final int numIterations, final double avgQueueSize, final long compTime,
				final double distToOpt, final boolean stateSpExpl, final long traversedEdges) {
			List<Move<L, T>> alignment = new ArrayList<>();
			State<S, L, T> s = state;
			alignment.add(s.getParentMove());
			double costs = s.getParentMove().getCost();
			while (s.getParentState() != null && s.getParentState().getParentMove() != null) {
				s = s.getParentState();
				alignment.add(0, s.getParentMove());
				costs += s.getParentMove().getCost();
			}
			return new NaiveImpl<L, T, S>(alignment, costs, state, enqueuedNodes, numIterations, avgQueueSize, compTime,
					distToOpt, stateSpExpl, traversedEdges);

		}
	}

	public class NaiveImpl<L, T, S> extends PartialAlignment.NaiveImpl<L, T, S>
			implements MeasurementAwarePartialAlignment<L, T, S> {

		private static final long serialVersionUID = -6288560191782759728L;

		private final double avgQueue;
		private final long compTime;
		private final long enqueued;
		private final int numIt;
		private final double distToOpt;
		private final boolean stateSpExpl;
		private final long traversedEdges;

		public NaiveImpl(final List<Move<L, T>> alignment, final double cost,
				final PartialAlignment.State<S, L, T> correspondingState, final long enqueued, final int numIt,
				final double avgQueue, final long compTime, final double distToOpt, final boolean stateSpExpl,
				final long traversedEdges) {
			super(alignment, cost, correspondingState);
			this.enqueued = enqueued;
			this.numIt = numIt;
			this.avgQueue = avgQueue;
			this.compTime = compTime;
			this.distToOpt = distToOpt;
			this.stateSpExpl = stateSpExpl;
			this.traversedEdges = traversedEdges;
		}

		public double getAverageQueueSize() {
			return avgQueue;
		}

		public long getComputationTime() {
			return compTime;
		}

		public int getNumberOfIterations() {
			return numIt;
		}

		public long getTotalEnqueuedNodes() {
			return enqueued;
		}

		public double getDistanceToOptimum() {
			return distToOpt;
		}

		public boolean stateSpaceWasExplored() {
			return stateSpExpl;
		}

		public long getTraversedEdges() {
			return traversedEdges;
		}

	}

	double getAverageQueueSize();

	long getComputationTime();

	double getDistanceToOptimum();

	int getNumberOfIterations();

	long getTotalEnqueuedNodes();

	boolean stateSpaceWasExplored();

	long getTraversedEdges();

}
