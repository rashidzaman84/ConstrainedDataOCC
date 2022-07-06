package org.processmining.constraineddataocc.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface IncrementalReplayResult<C, L, T, S, A extends PartialAlignment<L, T, S>> extends Map<C, List<A>> {

	public enum Impl {
		HASH_MAP;
	}

	public class NaiveHashMapImpl<C, L, T, S, A extends PartialAlignment<L, T, S>> extends HashMap<C, List<A>>
			implements IncrementalReplayResult<C, L, T, S, A> {

		private static final long serialVersionUID = -4174290675297989675L;
	}

	public class Factory {

		public static <C, L, T, S, A extends PartialAlignment<L, T, S>> IncrementalReplayResult<C, L, T, S, A> construct(
				final Impl impl) {
			switch (impl) {
				case HASH_MAP :
				default :
					return new NaiveHashMapImpl<>();
			}
		}

	}

}
