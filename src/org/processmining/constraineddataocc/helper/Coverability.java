package org.processmining.constraineddataocc.helper;

/***********************************************************
 * This software is part of the ProM package * http://www.processmining.org/ * *
 * Copyright (c) 2003-2008 TU/e Eindhoven * and is licensed under the * LGPL
 * License, Version 1.0 * by Eindhoven University of Technology * Department of
 * Information Systems * http://www.processmining.org * *
 ***********************************************************/



import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.framework.plugin.events.Logger.MessageLevel;
import org.processmining.models.connections.petrinets.behavioral.CoverabilityGraphConnection;
import org.processmining.models.connections.petrinets.behavioral.CoverabilitySetConnection;
import org.processmining.models.connections.petrinets.behavioral.DeadMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.directed.petrinet.InhibitorNet;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.analysis.CoverabilitySet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.CoverabilityGraph;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemFactory;
import org.processmining.models.graphbased.directed.utils.Node;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.Semantics;
import org.processmining.models.semantics.petrinet.CTMarking;
import org.processmining.models.semantics.petrinet.InhibitorNetSemantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.PetrinetSemantics;
import org.processmining.models.semantics.petrinet.impl.PetrinetSemanticsFactory;
import org.processmining.plugins.petrinet.behavioralanalysis.CGGenerator;

/**
 * This class represents a plugin to transform net into coverability graph
 * 
 * @author arya
 * @email arya.adriansyah@gmail.com
 * @version 1 September 2008
 */
@Plugin(name = "Construct Coverability Graph of a Petri Net", returnLabels = { "Coverability Graph",
		"Coverability Set", "Initial states", "Final states" }, returnTypes = { CoverabilityGraph.class,
		CoverabilitySet.class, StartStateSet.class, AcceptStateSet.class }, parameterLabels = { "Net", "Marking",
		"Semantics" }, userAccessible = true)
public class Coverability {

	// variant with Petrinet and marking
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "A. Adriansyah", email = "a.adriansyah@tue.nl", pack = "PNAnalysis")
	@PluginVariant(variantLabel = "Petrinet to Coverability Graph", requiredParameterLabels = { 0, 1 })
	public CoverabilityGraph petriNetToCoverabilityGraph(PluginContext context, Petrinet net, Marking state)
			throws ConnectionCannotBeObtained {
		return petrinetetToCoverabilityGraph(context, net, state,
				PetrinetSemanticsFactory.regularPetrinetSemantics(Petrinet.class));
	}

	// variant with InhibitorNet and marking
	@PluginVariant(variantLabel = "Inhibitor net to Coverability Graph", requiredParameterLabels = { 0, 1 })
	public CoverabilityGraph inhibitorNetToCoverabilityGraph(PluginContext context, InhibitorNet net, Marking state)
			throws ConnectionCannotBeObtained {
		return inhibitorNetToCoverabilityGraph(context, net, state,
				PetrinetSemanticsFactory.regularInhibitorNetSemantics(InhibitorNet.class));
	}

	// variant with PetriNet, marking, and semantic
	@PluginVariant(variantLabel = "Petrinet net to Coverability Graph", requiredParameterLabels = { 0, 1, 2 })
	public CoverabilityGraph petrinetetToCoverabilityGraph(PluginContext context, Petrinet net, Marking state,
			PetrinetSemantics semantics) throws ConnectionCannotBeObtained {
		return buildAndConnect(context, net, state, semantics);
	}

	// variant with InhibitorNet, marking, and semantic
	@PluginVariant(variantLabel = "Inhibitor net to Coverability Graph", requiredParameterLabels = { 0, 1, 2 })
	public CoverabilityGraph inhibitorNetToCoverabilityGraph(PluginContext context, InhibitorNet net, Marking state,
			InhibitorNetSemantics semantics) throws ConnectionCannotBeObtained {
		return buildAndConnect(context, net, state, semantics);
	}

	// variant wiithout context
	public static CoverabilityGraph getCoverabilityGraph(Petrinet net, Marking initial,
			Semantics<Marking, Transition> semantics) {
		CGGenerator generator = new CGGenerator();
		CoverabilityGraph ts = generator.doBreadthFirst(null, net.getLabel(), new CTMarking(initial), semantics);
		return ts;
	}

	// variant without context
	public static CoverabilityGraph getCoverabilityGraph(InhibitorNet net, Marking initial,
			Semantics<Marking, Transition> semantics) {
		CGGenerator generator = new CGGenerator();
		CoverabilityGraph ts = generator.doBreadthFirst(null, net.getLabel(), new CTMarking(initial), semantics);
		return ts;
	}

	/**
	 * Method to build a coverability graph, as well as make its connection
	 * 
	 * @param context
	 *            Context of the net
	 * @param net
	 *            net to be analyzed
	 * @param initial
	 *            Initial state (initial marking)
	 * @param semantics
	 *            Semantic of this net
	 * @return Object[] CoverabilityGraph, CoverabilitySet, StartStateSet, and
	 *         AcceptStateSet
	 * @throws ConnectionCannotBeObtained
	 */
	private CoverabilityGraph buildAndConnect(PluginContext context, PetrinetGraph net, Marking initial,
			Semantics<Marking, Transition> semantics) throws ConnectionCannotBeObtained {
		context.getConnectionManager().getFirstConnection(InitialMarkingConnection.class, context, net, initial);

		semantics.initialize(net.getTransitions(), initial);
		CoverabilityGraph ts = doBreadthFirst(context, net.getLabel(), new CTMarking(initial), semantics);
		if (ts == null) {
			assert context.getProgress().isCancelled();
			context.log("Coverability Graph computation cancelled.", MessageLevel.DEBUG);
			return null;
		}

		StartStateSet startStates = new StartStateSet();
		startStates.add(new CTMarking(initial));

		AcceptStateSet acceptingStates = new AcceptStateSet();
		for (State state : ts.getNodes()) {
			if (ts.getOutEdges(state).isEmpty()) {
				acceptingStates.add(state.getIdentifier());
			}
		}

		CTMarking[] markings = ts.getStates().toArray(new CTMarking[0]);
		CoverabilitySet cs = new CoverabilitySet(markings);
		context.addConnection(new CoverabilitySetConnection(net, initial, cs, semantics, "Coverability Set"));
		context.addConnection(new CoverabilityGraphConnection(net, ts, initial, semantics));
		context.addConnection(new TransitionSystemConnection(ts, startStates, acceptingStates));
		context.addConnection(new DeadMarkingConnection(net, initial, acceptingStates, semantics));

		context.getFutureResult(0).setLabel("Coverability graph of " + net.getLabel());
		//context.getFutureResult(1).setLabel("Coverability set of " + net.getLabel());
		//context.getFutureResult(2).setLabel("Initial states of " + ts.getLabel());
		//context.getFutureResult(3).setLabel("Accepting states of " + ts.getLabel());

		context.log("Coverability Graph size: " + ts.getStates().size() + " states and " + ts.getEdges().size()
				+ " transitions.", MessageLevel.DEBUG);

		return ts; 
	}

	/**
	 * Build a coverability graph from initial state with breadth-first approach
	 * 
	 * @param context
	 *            context of the net
	 * @param label
	 *            label of the net
	 * @param state
	 *            Initial state (initial marking)
	 * @param semantics
	 *            semantics obtained from initial state
	 * @return CoverabilityGraph to be displayed
	 */
	public CoverabilityGraph doBreadthFirst(PluginContext context, String label, CTMarking state,
			Semantics<Marking, Transition> semantics) {

		// work using tree and transition system in parallel
		Node<CTMarking> root = new Node<CTMarking>();
		// create a tree to describe the coverability tree

		root.setData(new CTMarking(state));
		root.setParent(null);

		// build transitionSystem based on the root node
		CoverabilityGraph ts = TransitionSystemFactory.newCoverabilityGraph("Coverability Graph of " + label);
		ts.addState(state);

		// expands all
		Queue<Node<CTMarking>> expandedNodes = new LinkedList<Node<CTMarking>>();
		expandedNodes.add(root);

		// if CG is used as an intermediary result, no need to have context as a parameter
		// therefore the context should be null
		// checking context inside of extend methods
		do {
			Collection<? extends Node<CTMarking>> newNodes = extend(expandedNodes.poll(), semantics, context, ts);

			if (context != null && context.getProgress().isCancelled()) {
				return null;
			}

			expandedNodes.addAll(newNodes);
		} while (!expandedNodes.isEmpty());
		return ts;
	}

	/**
	 * Extend an input state to get all of its children by executing available
	 * transition. Omega notation is added as needed
	 * 
	 * @param root
	 *            state to be extended, represented in form of element of a tree
	 * @param semantics
	 *            semantic of current net
	 * @param context
	 *            Context of the net
	 * @param ts
	 *            transition system associated with the tree
	 * @return list of all nodes need to be further extended
	 */
	private Collection<? extends Node<CTMarking>> extend(Node<CTMarking> root,
			Semantics<Marking, Transition> semantics, PluginContext context, TransitionSystem ts) {
		// init
		Marking rootState = root.getData();
		semantics.setCurrentState(rootState);

		List<Node<CTMarking>> needToBeExpanded = new ArrayList<Node<CTMarking>>();
		// this is the variable to be returned
		//		if (context != null)
		//			context.log("Current root = " + root.getData().toString(), MessageLevel.DEBUG);

		// execute transitions
		for (Transition t : semantics.getExecutableTransitions()) {

			//BVD:
			if (context != null && context.getProgress().isCancelled()) {
				return null;
			}

			//			if (context != null)
			//				context.log("Transition going to be executed : " + t.getLabel(), MessageLevel.DEBUG);
			semantics.setCurrentState(rootState);
			try {
				/*
				 * [HV] The local variable info is never read
				 * ExecutionInformation info =
				 */semantics.executeExecutableTransition(t);
				//				if (context != null)
				//					context.log(info.toString(), MessageLevel.DEBUG);
			} catch (IllegalTransitionException e) {
				e.printStackTrace();
				assert (false);
			}
			//			if (context != null)
			//				context.log("After execution= " + semantics.getCurrentState().toString(), MessageLevel.DEBUG);

			// convert current state to CTMarking
			// change the place in all the nodes of the tree with omega
			// representation
			CTMarking currStateCTMark = new CTMarking(semantics.getCurrentState());
			if (root.getData().hasOmegaPlace()) {
				currStateCTMark = currStateCTMark.transformToOmega(root.getData().getOmegaPlaces());
			}

			// currStateCTMark node
			Node<CTMarking> currStateCTMarkNode = new Node<CTMarking>();

			// is newState marking identical to a marking on the path from the
			// root?
			CTMarking lessOrEqualMarking = null;
			//			if (context != null)
			//				context.log("currStateCTMark = " + currStateCTMark.toString(), MessageLevel.DEBUG);
			//			if (context != null)
			//				context.log("root = " + root.getData().toString(), MessageLevel.DEBUG);

			for (State node : ts.getNodes()) {
				if (node.getIdentifier().equals(currStateCTMark)) {
					lessOrEqualMarking = currStateCTMark;
				}
			}
			if (lessOrEqualMarking == null) {
				lessOrEqualMarking = getIdenticalOrCoverable(currStateCTMark, root);
			}
			if (lessOrEqualMarking != null) {

				//				if (context != null)
				//					context.log("less or equal is found", MessageLevel.DEBUG);
				//				if (context != null)
				//					context.log(lessOrEqualMarking.toString(), MessageLevel.DEBUG);

				// check if the state is the same
				if (!lessOrEqualMarking.equals(currStateCTMark)) {
					// if not the same, check in case there are places that
					// needs to be changed into omega
					//					if (context != null)
					//						context.log("Equal node is not found. Coverable node is found", MessageLevel.DEBUG);

					// The places that need to be marked as omega, are those places that
					// occur more often in currStateCTMark, than in lessOrEqualMarking
					CTMarking temp = new CTMarking(currStateCTMark);
					temp.removeAll(lessOrEqualMarking);

					Set<Place> listToBeChanged = temp.baseSet();

					// list of places need to be transformed into omega
					currStateCTMark = currStateCTMark.transformToOmega(listToBeChanged);

					// set the node
					currStateCTMarkNode.setData(currStateCTMark);
					currStateCTMarkNode.setParent(root);

					root.addChild(currStateCTMarkNode); // insert the new node
					// to root

					// update transition system
					ts.addState(currStateCTMark);
					int size = ts.getStates().size();
					if (size % 1000 == 0 && context != null) {
						context.log("Coverability Graph size: " + size + " states and " + ts.getEdges().size()
								+ " transitions.", MessageLevel.DEBUG);
					}
					// BVD:new Marking(currStateCTMark));
					ts.addTransition(rootState, currStateCTMark, t);
					// BVD: new Marking(currStateCTMarkNode.getData()),
					//					if (context != null)
					//						context.log("Added Child (also need to be expanded): "
					//								+ currStateCTMarkNode.getData().toString(), MessageLevel.DEBUG);

					// node to be expanded
					needToBeExpanded.add(currStateCTMarkNode);
				} else { // exactly the same node is found
					//					if (context != null)
					//						context.log("Equal node is found", MessageLevel.DEBUG);
					// just set the node and add
					// set the node
					currStateCTMarkNode.setData(lessOrEqualMarking);
					currStateCTMarkNode.setParent(root);

					root.addChild(currStateCTMarkNode); // insert the new node
					// to root

					// update transition system
					ts.addState(currStateCTMark);
					// BVD:new Marking(currStateCTMark));
					ts.addTransition(rootState, currStateCTMark, t);
					// BVD: new Marking(currStateCTMarkNode.getData()),

					//					if (context != null)
					//						context.log("Added Child : " + lessOrEqualMarking.toString(), MessageLevel.DEBUG);
				}
				//				if (context != null)
				//					context.log("root after = " + root.toString(), MessageLevel.DEBUG);
			} else {
				// set the node
				currStateCTMarkNode.setData(currStateCTMark);
				currStateCTMarkNode.setParent(root);

				root.addChild(currStateCTMarkNode); // insert the new node to
				// root

				// update transition system
				ts.addState(currStateCTMark);
				int size = ts.getStates().size();
				if (size % 1000 == 0) {
					context.log("Coverability Graph size: " + size + " states and " + ts.getEdges().size()
							+ " transitions.", MessageLevel.DEBUG);
				}
				// BVD:new Marking(currStateCTMark));
				ts.addTransition(rootState, currStateCTMark, t);
				// BVD: new Marking(currStateCTMarkNode.getData()),

				// node only need to be expanded
				needToBeExpanded.add(currStateCTMarkNode);

				//				if (context != null)
				//					context.log("Added Child (also need to be expanded): " + currStateCTMarkNode.getData().toString(),
				//							MessageLevel.DEBUG);
			}
			//			if (context != null)
			//				context.log("---------------", MessageLevel.DEBUG);
		}
		return needToBeExpanded;
	}

	/**
	 * Return an identical node or a node coverable by newState node. The node
	 * should be on the top of tree hierarchy. Return null if there is no
	 * identical node nor coverable node.
	 * 
	 * @param newState
	 * @param referenceNode
	 *            node which represent the parent of the newState node
	 * @return
	 */
	private CTMarking getIdenticalOrCoverable(CTMarking newState, Node<CTMarking> referenceNode) {
		if (referenceNode.getParent() != null) { // checking not against root
			// node
			if (referenceNode.getData().isLessOrEqual(newState)) {
				return referenceNode.getData();
			} else {
				return getIdenticalOrCoverable(newState, referenceNode.getParent());
			}
		} else { // now checking against the root node
			if (referenceNode.getData().isLessOrEqual(newState)) {
				return referenceNode.getData();
			} else {
				return null;
			}
		}
	}
}
