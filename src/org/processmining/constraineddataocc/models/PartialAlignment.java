package org.processmining.constraineddataocc.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.processmining.onlineconformance.models.Move;
//import org.processmining.onlineconformance.models.PartialAlignment;;

public interface PartialAlignment<L, T, S> extends org.processmining.onlineconformance.models.PartialAlignment<L, T, S> {

	public class Factory {

		public static <S, L, T> PartialAlignment<L, T, S> construct(State<S, L, T> state) {
			List<Move<L, T>> alignment = new ArrayList<>();
			State<S, L, T> s = state;
			if (s.getParentMove() != null) {
				alignment.add(s.getParentMove());
			}
			double costs = s.getParentMove() != null ? s.getParentMove().getCost() : 0d;
			while (s.getParentState() != null && s.getParentState().getParentMove() != null) {
				s = s.getParentState();
				alignment.add(0, s.getParentMove());
				costs += s.getParentMove().getCost();
			}
			return new NaiveImpl<L, T, S>(alignment, costs, state);

		}
	}

	public class NaiveImpl<L, T, S> extends ArrayList<Move<L, T>> implements PartialAlignment<L, T, S> {
		private static final long serialVersionUID = -5482051097110300943L;
		private final List<L> projL = new ArrayList<>();
		private final List<T> projT = new ArrayList<>();
		private final double cost;
		private final State<S, L, T> state;

		public NaiveImpl(final List<Move<L, T>> alignment, final double cost, final State<S, L, T> correspondingState) {
			super.addAll(alignment);
			L l;
			T t;
			for (Move<L, T> m : this) {
				if ((l = m.getEventLabel()) != null) {
					projL.add(l);
				}
				if ((t = m.getTransition()) != null) {
					projT.add(t);
				}
			}
			this.cost = cost;
			this.state = correspondingState;
		}

		public double getCost() {
			return cost;
		}

		public List<L> projectOnLabels() {
			return projL;
		}

		public List<T> projectOnModel() {
			return projT;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + getCost() + ")";
		}

		public PartialAlignment.State<S, L, T> getState() {
			return state;
		}

		public boolean isPrefixAlignment(List<L> ls) {
			return this.projectOnLabels().equals(ls);
		}

		public boolean isAlignment(List<L> ls, Collection<S> finalMarkings) {
			return isPrefixAlignment(ls) && finalMarkings.contains(getState().getStateInModel());
		}

	}

	public interface State<S, L, T> extends org.processmining.onlineconformance.models.PartialAlignment.State<S, L, T> {

		public class Factory {
			public static <S, L, T> State<S, L, T> construct(final S state, final int labs, final State<S, L, T> parent,
					final Move<L, T> move) {
				return new Impl<>(state, labs, parent, move);
			}
			
			/*public static <S, L, T> State<S, L, T> construct(final S state, final Move<L, T> move) {
				return new LightWeight<>(state, move);
			}*/
			
			public static <S, L, T> State<S, L, T> construct(final S state, final int labs, final Move<L, T> move) {
				return new NullParentState<>(state, labs, move);
			}
		}

		class Impl<S, L, T> implements State<S, L, T> {

			private final int labs;
			private final Move<L, T> move;
			private State<S, L, T> parent;		//final has been removed in order to set parent to Null for forgetting prefix states.
			private final S state;

			public Impl(final S state, final int labs, final State<S, L, T> parent, final Move<L, T> move) {
				this.state = state;
				this.labs = labs;
				this.parent = parent;
				this.move = move;
			}

			@Override
			public boolean equals(Object o) {
				boolean eq = o instanceof State;
				if (eq) {
					State<?, ?, ?> cast = (State<?, ?, ?>) o;
					eq &= cast.getStateInModel().equals(getStateInModel());
					eq &= cast.getNumLabelsExplained() == getNumLabelsExplained();
				}
				return eq;
			}

			public int getNumLabelsExplained() {
				return labs;
			}

			public Move<L, T> getParentMove() {
				return move;
			}

			public State<S, L, T> getParentState() {
				return parent;
			}
			
			public void setParentState(State<S, L, T> parent) {
				this.parent = parent;
			}

			public S getStateInModel() {
				return state;
			}

			@Override
			public int hashCode() {
				return 31 * getStateInModel().hashCode() + 37 * getNumLabelsExplained();
			}

			@Override
			public String toString() {
				return "(labels: " + getNumLabelsExplained() + ", state_in_model: " + getStateInModel() + ",  move: "
						+ getParentMove().toString() + ")";
			}
		}
		
		class NullParentState<S, L, T> implements State<S, L, T> {

			private final int labs;
			private final Move<L, T> move;
			private State<S, L, T> parent;		//final has been removed in order to set parent to Null for forgetting prefix states.
			private final S state;

			public NullParentState(final S state, final int labs, final Move<L, T> move) {
				this.state = state;
				this.labs = labs;
				this.parent = null;
				this.move = move;
			}

			@Override
			public boolean equals(Object o) {
				boolean eq = o instanceof State;
				if (eq) {
					State<?, ?, ?> cast = (State<?, ?, ?>) o;
					eq &= cast.getStateInModel().equals(getStateInModel());
					eq &= cast.getNumLabelsExplained() == getNumLabelsExplained();
				}
				return eq;
			}

			public int getNumLabelsExplained() {
				return labs;
			}

			public Move<L, T> getParentMove() {
				return move;
			}

			public State<S, L, T> getParentState() {
				return parent;
			}
			
			public void setParentState(State<S, L, T> parent) {
				this.parent = parent;
			}

			public S getStateInModel() {
				return state;
			}

			@Override
			public int hashCode() {
				return 31 * getStateInModel().hashCode() + 37 * getNumLabelsExplained();
			}

			@Override
			public String toString() {
				return "(labels: " + getNumLabelsExplained() + ", state_in_model: " + getStateInModel() + ",  move: "
						+ getParentMove().toString() + ")";
			}
		}
		
		class LightWeight<S, L, T> implements State<S, L, T> {
			
			private final S state;
			private final Move<L, T> move;
			
			public LightWeight(final S state, final Move<L, T> move ) {
				this.state = state;	
				this.move = move;
			}

			public int getNumLabelsExplained() {
				// TODO Auto-generated method stub
				return 0;
			}

			public Move<L, T> getParentMove() {
				return this.move;
			}

			public State<S, L, T> getParentState() {
				// TODO Auto-generated method stub
				return null;
			}

			public void setParentState(State<S, L, T> parent) {
				// TODO Auto-generated method stub
				
			}

			public S getStateInModel() {
				return this.state;
			}
			
		}

		int getNumLabelsExplained();

		Move<L, T> getParentMove();

		State<S, L, T> getParentState();
		
		void setParentState(State<S, L, T> parent);

		S getStateInModel();
	}

	double getCost();

	List<L> projectOnLabels();

	List<T> projectOnModel();

	State<S, L, T> getState();

	boolean isPrefixAlignment(List<L> ls);

	boolean isAlignment(List<L> ls, Collection<S> finalMarkings);
}
