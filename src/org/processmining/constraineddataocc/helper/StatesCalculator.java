package org.processmining.constraineddataocc.helper;

import java.util.Map;

import org.processmining.onlineconformance.models.PartialAlignment;
import org.processmining.onlineconformance.models.PartialAlignment.State;



public class StatesCalculator {

	@SuppressWarnings("unchecked")
	public static <A extends PartialAlignment, S, L, T> int getNumberOfStates(final A previousAlignment) {
		/*if (previousAlignment == null || previousAlignment.size() < getParameters().getLookBackWindow()) {
			return getInitialState();
		} else {*/
		//System.out.println(previousAlignment.size());
		if (previousAlignment != null) {
			State<S, L, T> state = previousAlignment.getState();
			int i = 1;
			while (state.getParentState() != null /*&& state.getParentState().getParentMove() !=null*/ ) {
				/*if(state.getParentState() instanceof LightWeight) {
					break;
				}*/
				state = state.getParentState();
				i++;
			}
			return i;
		}
		System.out.println("The prefix-alignment is NULL");
		return 0;
		//}
	}

	public static <A extends PartialAlignment, C> int getNumberOfStatesInMemory(final Map<C, A> dataStore) {
		Integer sum = 0;
		for(java.util.Map.Entry<C, A> record:dataStore.entrySet()) {
			sum += getNumberOfStates(record.getValue());
		}
		//System.out.println(" = "+ sum);
		return sum;
	}
}
