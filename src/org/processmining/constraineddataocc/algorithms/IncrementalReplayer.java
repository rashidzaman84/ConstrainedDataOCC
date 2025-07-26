
package org.processmining.constraineddataocc.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.javatuples.Triplet;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl0;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl1;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl1a;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl1a2Tests;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl2;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl2a;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl2b;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl2c;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl3;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl3a;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl4;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl5;
import org.processmining.constraineddataocc.algorithms.impl.IncrementalRevBasedReplayerImpl5a;
import org.processmining.constraineddataocc.models.PartialAlignment;
import org.processmining.constraineddataocc.parameters.IncrementalReplayerParametersImpl;
import org.processmining.constraineddataocc.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.onlineconformance.models.ModelSemantics;

public interface IncrementalReplayer<M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalReplayerParametersImpl<M, L, T>> {

	public class Factory {

		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) throws IllegalArgumentException {
			switch (strategy) {
				case REVERT_BASED :
				default :
					throw new IllegalArgumentException("Parameters do not match chosen strategy");
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct0(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl0<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}

		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct1( //????With Bounded States and dynamic Windows
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl1<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct1a(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl1a<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct1aTests(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl1a2Tests<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
				
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct2( //With Bounded Traces (N)
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl2<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct2a(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl2a<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct2b(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl2b<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct2c(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl2c<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct3(  //Combined Bounded States and Traces (W&N)
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl3<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct3a(
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl3a<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct4(  //marking prediction
		final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
		final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
		final Strategy strategy) {
	switch (strategy) {
		case REVERT_BASED :
		default :
			return new IncrementalRevBasedReplayerImpl4<M, C, S, T, L, A, P>(initialStateInModel,
					finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
	}
}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct5(   //partial-alignments/Relaxed Initial Marking
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl5<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct5a(  
				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
				final Strategy strategy) {
			switch (strategy) {
				case REVERT_BASED :
				default :
					return new IncrementalRevBasedReplayerImpl5a<M, C, S, T, L, A, P>(initialStateInModel,
							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
			}
		}
		
//		public static <M, C, S, T, L, A extends PartialAlignment<L, T, S>, P extends IncrementalRevBasedReplayerParametersImpl<M, L, T>> IncrementalReplayer<M, C, S, T, L, A, P> construct5(  //marking prediction
//				final S initialStateInModel, final S finalStateInModel, final Map<C, A> dataStore,
//				final ModelSemantics<M, S, T> modelSemantics, final P parameters, final Map<T, L> labelmap,
//				final Strategy strategy) {
//			switch (strategy) {
//				case REVERT_BASED :
//				default :
//					return new IncrementalRevBasedReplayerImpl5<M, C, S, T, L, A, P>(initialStateInModel,
//							finalStateInModel, dataStore, modelSemantics, parameters, labelmap);
//			}
//		}
		
		
	}

	public enum SearchAlgorithm {
		A_STAR, IDA_STAR;
	}

	public enum Strategy {
		REVERT_BASED;
	}

	Map<C, A> getDataStore();
	
	Object getObject();

	S getFinalStateInModel();

	S getInitialStateInModel();

	A processEvent(C c, L l);
	
	A processEvent(C c, L l, int window);  //added for my experiment

	PartialAlignment.State<S, L, T> getInitialState();

	P getParameters();
	
	Map<C, List<Double>> getCompoundCost();
	Map<String, ArrayList<Triplet<String, Integer, Integer>>> getEventsCategorisation();

	Strategy getStrategy();	

}
