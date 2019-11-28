package preprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.chocosolver.solver.variables.IntVar;
import org.jbpt.petri.NetSystem;

import relationsRM.RelationManager;
import relationsRM.SuccessorConfiguration;

public class LogPreprocessingManager {

	Log unlabeledLog;
	RelationManager relManager;
	NetSystem netSystem;

	// needed dictionaries for building the constraints and variables [may be
	// need more]
	// key is the event, e.g., e1
	// all the events that can't occurs in the same case based on RM
	ConcurrentHashMap<String, List<String>> xorEvents;
	// the direct successors based on RM and the model structure
	ConcurrentHashMap<String, List<String>> directPredEvents;
	// the direct successors based on RM and the model structure
	ConcurrentHashMap<String, List<String>> directSucEvents;
	// the sequence of the BP
	ConcurrentHashMap<String, List<String>> eventualSucEvents;

	ConcurrentHashMap<String, List<Integer>> predBoundaries;

	// key is activity name
	// all the events of the process that not belongs to any loops
	ConcurrentHashMap<String, List<String>> exactOneEvents;
	// loop main branch if its not silent transitions
	ConcurrentHashMap<String, List<String>> atLeastOneEvents;
	// can be the xor at that not belongs to any loops
	ConcurrentHashMap<String, List<String>> atMostOneEvents;

	CopyOnWriteArrayList<Integer> startEvents;
	CopyOnWriteArrayList<Integer> endEvents;
	ConcurrentHashMap<String, List<Integer>> succMaxBoundaries;

	/***
	 * This constructor is used when the input is a process model,i.e.,Petri-net
	 */
	public LogPreprocessingManager(Log _unlabeledLog, NetSystem _netSystem) {
		super();
		this.unlabeledLog = _unlabeledLog;
		this.netSystem = _netSystem;
		this.relManager = new RelationManager(_netSystem);
		this.predBoundaries = new ConcurrentHashMap<>();
		this.succMaxBoundaries = new ConcurrentHashMap<>();
		// this.buildDictionaries();
		// this.relManager.getEndActivities();
	}

	/***
	 * This constructor is used when the input is a process model,i.e.,Petri-net
	 */
	public LogPreprocessingManager(Log _unlabeledLog, NetSystem _netSystem, SuccessorConfiguration config) {
		super();
		this.unlabeledLog = _unlabeledLog;
		this.netSystem = _netSystem;
		this.relManager = new RelationManager(_netSystem, config);
		this.predBoundaries = new ConcurrentHashMap<>();
		this.succMaxBoundaries = new ConcurrentHashMap<>();

		// System.out.println(this.relManager.getPredecessors().getPredSets());
		// System.out.println(this.relManager.getSuccessors().getSuccessors());
		// this.buildDictionaries();
		// this.relManager.getEndActivities();
	}

	public void buildXorSuccPredDictionaries() {
		xorEvents = new ConcurrentHashMap<String, List<String>>();
		directPredEvents = new ConcurrentHashMap<String, List<String>>();
		directSucEvents = new ConcurrentHashMap<String, List<String>>();
		// may be later to be added based on the bp
		eventualSucEvents = new ConcurrentHashMap<String, List<String>>();
		CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>(unlabeledLog.getEvents());
		for (int i = 0; i < unlabeledLog.getnEvents(); i++) {
			Event currentE_ = events.get(i);
			AtomicInteger currentIndex_ = new AtomicInteger(i);

			// adding the xor events in dictionary
			List<String> xorActivities = relManager.getEclusiveActivities(currentE_.getActivity());
			if (xorActivities != null && !xorActivities.isEmpty()) {
				List<String> xorEvents_ = new CopyOnWriteArrayList<>();
				xorActivities.parallelStream().forEach(act -> {
					List<Event> xorEventsL = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
					xorEventsL.parallelStream().forEach(e -> {
						xorEvents_.add("e" + e.getId());

					});
				});
				if (!xorEvents_.isEmpty())
					xorEvents.put("e" + currentE_.getId(), xorEvents_);
			}
			// adding the direct successors in dictionary
			// N.B. includes sequence and parallel
			if (!relManager.getbJoinActivities().contains(currentE_.getActivity())) {
				HashSet<HashSet<String>> succActivities = relManager.getSuccessorSet(currentE_.getActivity());

				// generate a flat list hena el 2wal actually 5li el relManager
				// y3mal ked

				if (succActivities != null && !succActivities.isEmpty()) {
					List<String> succEvents_ = new CopyOnWriteArrayList<>();
					// succActivities.parallelStream().forEach(act -> {
					succActivities.stream().forEach(acts -> {
						List<String> acts_ = new CopyOnWriteArrayList<>(acts);
						// in case of parallel predecessors
						if (acts.size() > 1) {
							// The flat version of the predecessors
							acts_.parallelStream().forEach(act -> {
								List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
								if (!preds_.isEmpty()) {
									// Set<String> temp = new HashSet<>();
									preds_.parallelStream().forEach(e -> {
										succEvents_.add("e" + e.getId());
									});
									// succEvents_.addAll(temp);
								}
							});
						} else {
							List<Event> preds_ = unlabeledLog.getActEventsBackward(acts_.get(0), currentIndex_.get());
							preds_.parallelStream().forEach(e -> {
								List<String> temp = new ArrayList<>();
								succEvents_.add("e" + e.getId());
							});
						}
					});

					if (!succEvents_.isEmpty())
						this.directSucEvents.put("e" + currentE_.getId(), succEvents_);

				}

			}

			// // direct predecessors
			if (relManager.getaJoinActivities().contains(currentE_.getActivity())) {
				HashSet<HashSet<String>> predActivities = relManager.getPredecessors(currentE_.getActivity());
				if (predActivities != null && !predActivities.isEmpty()) {
					List<String> predEvents_ = new CopyOnWriteArrayList<>();
					predActivities.stream().forEach(acts -> {
						List<String> acts_ = new CopyOnWriteArrayList<>(acts);
						// in case of parallel predecessors
						if (acts.size() > 1) {
							// The flat version of the predecessors
							acts_.parallelStream().forEach(act -> {
								List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
								if (!preds_.isEmpty()) {
									// Set<String> temp = new HashSet<>();
									preds_.parallelStream().forEach(e -> {
										predEvents_.add("e" + e.getId());
									});
									// predEvents_.addAll(temp);
								}
							});
							// we need to do a cartesian product between the
							// events
							// lists
							// List<String> parallelEvents = new
							// CopyOnWriteArrayList<>();
							// if (!parallelEvents.isEmpty()) {
							// List<List<String>> parallelCPsets = new
							// ArrayList<>(
							//
							// com.google.common.collect.Sets.cartesianProduct(parallelEvents));
							// predEvents_.addAll(parallelCPsets);
							// }
						}
						// xor single element in the set
						else {
							List<Event> preds_ = unlabeledLog.getActEventsBackward(acts_.get(0), currentIndex_.get());
							preds_.parallelStream().forEach(e -> {
								List<String> temp = new ArrayList<>();
								predEvents_.add("e" + e.getId());
							});
						}
					});
					if (!predEvents_.isEmpty())
						this.directPredEvents.put("e" + currentE_.getId(), predEvents_);
				}
			}
			// // eventual
			// List<String> eventuallyActivities =
			// relManager.getEventualActivities(currentE_.getActivity());
			// if (eventuallyActivities != null &&
			// !eventuallyActivities.isEmpty()) {
			// List<String> eventualEvents_ = new CopyOnWriteArrayList<>();
			// for (String act : eventuallyActivities) {
			//
			// List<Event> eveEvents = unlabeledLog.getActEvents(act,
			// currentIndex_.get());
			// eveEvents.parallelStream().forEach(e -> {
			// eventualEvents_.add("e" + e.getId());
			// });
			// }
			// if (!eventualEvents_.isEmpty())
			// this.eventualSucEvents.put("e" + currentE_.getId(),
			// eventualEvents_);
			// }

		}
	}

	public void buildXorSuccPredSetsDictionaries() {
		xorEvents = new ConcurrentHashMap<String, List<String>>();
		directPredEvents = new ConcurrentHashMap<String, List<String>>();
		directSucEvents = new ConcurrentHashMap<String, List<String>>();
		// may be later to be added based on the bp
		eventualSucEvents = new ConcurrentHashMap<String, List<String>>();
		CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>(unlabeledLog.getEvents());
		for (int i = 0; i < unlabeledLog.getnEvents(); i++) {
			Event currentE_ = events.get(i);
			AtomicInteger currentIndex_ = new AtomicInteger(i);

			// adding the xor events in dictionary
			List<String> xorActivities = relManager.getEclusiveActivities(currentE_.getActivity());
			if (xorActivities != null && !xorActivities.isEmpty()) {
				List<String> xorEvents_ = new CopyOnWriteArrayList<>();
				xorActivities.parallelStream().forEach(act -> {
					List<Event> xorEventsL = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
					xorEventsL.parallelStream().forEach(e -> {
						xorEvents_.add("e" + e.getId());

					});
				});
				if (!xorEvents_.isEmpty())
					xorEvents.put("e" + currentE_.getId(), xorEvents_);
			}

			// adding the direct successors in dictionary

//			System.out.println("Successors dictionary");
			HashSet<HashSet<String>> succActivities = relManager.getSuccessorSet(currentE_.getActivity());
			boolean constructSuccessorCons = relManager.checkPredecessorOfSuccessor(succActivities,
					currentE_.getActivity());
			if (succActivities != null && !succActivities.isEmpty() && constructSuccessorCons) {
				List<String> succEvents_ = new CopyOnWriteArrayList<>();

				// flat it and check the set direclty without all this checks
				Set<String> flatSetSucc = succActivities.stream().flatMap(p -> p.stream()).collect(Collectors.toSet());
				List<String> acts_ = new CopyOnWriteArrayList<>(flatSetSucc);
				acts_.parallelStream().forEach(act -> {
					List<Event> succes_ = unlabeledLog.getActEvents(act, currentIndex_.get());
					if (!succes_.isEmpty()) {
						succes_.parallelStream().forEach(e -> {
							succEvents_.add("e" + e.getId());
						});
					}
				});
				if (!succEvents_.isEmpty()) { // to remove redundancy
					HashSet<String> succActivitiesd = new HashSet<>(succEvents_);
					this.directSucEvents.put("e" + currentE_.getId(), new ArrayList<>(succActivitiesd));
				}
			}

			// // direct predecessors
			// System.out.println("Predecessors dictionary");
			if (!constructSuccessorCons && relManager.getaJoinActivities().contains(currentE_.getActivity())) {
				HashSet<HashSet<String>> predActivities = relManager.getPredecessors(currentE_.getActivity());
				if (predActivities != null && !predActivities.isEmpty()) {
					List<String> predEvents_ = new CopyOnWriteArrayList<>();
					Set<String> flatSetPred = predActivities.stream().flatMap(p -> p.stream())
							.collect(Collectors.toSet());
					List<String> acts_ = new CopyOnWriteArrayList<>(flatSetPred);

					acts_.parallelStream().forEach(act -> {
						List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
						if (!preds_.isEmpty()) {
							preds_.parallelStream().forEach(e -> {
								predEvents_.add("e" + e.getId());
							});
						}
					});
					if (!predEvents_.isEmpty()) {
						HashSet<String> predActivitiesd = new HashSet<>(predEvents_);
						this.directPredEvents.put("e" + currentE_.getId(), new ArrayList<>(predActivitiesd));
					}
				}
			}

		}
	}

	// Used for encoding V3
	public void buildXorSuccPredSetDict() {
		xorEvents = new ConcurrentHashMap<String, List<String>>();
		directPredEvents = new ConcurrentHashMap<String, List<String>>();
		directSucEvents = new ConcurrentHashMap<String, List<String>>();
		// may be later to be added based on the bp
		eventualSucEvents = new ConcurrentHashMap<String, List<String>>();
		CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>(unlabeledLog.getEvents());
		for (int i = 0; i < unlabeledLog.getnEvents(); i++) {
			Event currentE_ = events.get(i);
			AtomicInteger currentIndex_ = new AtomicInteger(i);

			// adding the xor events in dictionary
			List<String> xorActivities = relManager.getEclusiveActivities(currentE_.getActivity());
			if (xorActivities != null && !xorActivities.isEmpty()) {
				List<String> xorEvents_ = new CopyOnWriteArrayList<>();
				xorActivities.parallelStream().forEach(act -> {
					List<Event> xorEventsL = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
					xorEventsL.parallelStream().forEach(e -> {
						xorEvents_.add("e" + e.getId());

					});
				});
				if (!xorEvents_.isEmpty())
					xorEvents.put("e" + currentE_.getId(), xorEvents_);
			}

			// Separate the decision of successor and predecessor as if there is
			// a loop mostly there will be a big deal with mixed gates
			// All events will have a successor set

			// adding the direct successors in dictionary

			// System.out.println("Successors dictionary");
			HashSet<HashSet<String>> succActivities = relManager.getSuccessorSet(currentE_.getActivity());

			if (succActivities != null && !succActivities.isEmpty()) {
				List<String> succEvents_ = new CopyOnWriteArrayList<>();

				// flat it and check the set direclty without all this checks
				Set<String> flatSetSucc = succActivities.stream().flatMap(p -> p.stream()).collect(Collectors.toSet());
				List<String> acts_ = new CopyOnWriteArrayList<>(flatSetSucc);
				acts_.parallelStream().forEach(act -> {
					List<Event> succes_ = unlabeledLog.getActEvents(act, currentIndex_.get());
					if (!succes_.isEmpty()) {
						succes_.parallelStream().forEach(e -> {
							succEvents_.add("e" + e.getId());
						});
					}
				});
				if (!succEvents_.isEmpty()) { // to remove redundancy
					HashSet<String> succActivitiesd = new HashSet<>(succEvents_);
					this.directSucEvents.put("e" + currentE_.getId(), new ArrayList<>(succActivitiesd));
				}
			}

			// join gates will have extra condition
			boolean constPredEset = relManager.checkPredActCons(currentE_.getActivity());
			if (constPredEset) {
				HashSet<HashSet<String>> predActivities = relManager.getPredecessors(currentE_.getActivity());
				if (predActivities != null && !predActivities.isEmpty()) {
					List<String> predEvents_ = new CopyOnWriteArrayList<>();
					Set<String> flatSetPred = predActivities.stream().flatMap(p -> p.stream())
							.collect(Collectors.toSet());
					List<String> acts_ = new CopyOnWriteArrayList<>(flatSetPred);

					acts_.parallelStream().forEach(act -> {
						List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
						if (!preds_.isEmpty()) {
							preds_.parallelStream().forEach(e -> {
								predEvents_.add("e" + e.getId());
							});
						}
					});
					if (!predEvents_.isEmpty()) {
						HashSet<String> predActivitiesd = new HashSet<>(predEvents_);
						this.directPredEvents.put("e" + currentE_.getId(), new ArrayList<>(predActivitiesd));
					}
				}
			}

		}
	}

	// i dont think it will hold completely for the self loop[ need to test it]
	public List<String> getLoopRestartEndEvents(String e1, String e2) {
		List<String> rEs = new CopyOnWriteArrayList<>();
		String a1 = this.getUnlabeledLog().getEvents().get(Integer.parseInt(e1.split("e")[1])).getActivity();
		String a2 = this.getUnlabeledLog().getEvents().get(Integer.parseInt(e2.split("e")[1])).getActivity();

		List<String> endActs = this.relManager.getRm().getLoopsBranchesActivities(a1, a2);
		List<Event> endEs = this.getUnlabeledLog().getActEvents(endActs, Integer.parseInt(e1.split("e")[1]),
				Integer.parseInt(e2.split("e")[1]));
		endEs.parallelStream().forEach(e -> {
			rEs.add("e" + e.getId());
		});
		return rEs;
	}

	public void buildSuccPredDictionaries() {
		xorEvents = new ConcurrentHashMap<String, List<String>>();
		directPredEvents = new ConcurrentHashMap<String, List<String>>();
		directSucEvents = new ConcurrentHashMap<String, List<String>>();
		// may be later to be added based on the bp
		eventualSucEvents = new ConcurrentHashMap<String, List<String>>();
		CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>(unlabeledLog.getEvents());
		for (int i = 0; i < unlabeledLog.getnEvents(); i++) {
			Event currentE_ = events.get(i);
			AtomicInteger currentIndex_ = new AtomicInteger(i);
			if (!relManager.getbJoinActivities().contains(currentE_.getActivity())) {
				HashSet<HashSet<String>> succActivities = relManager.getSuccessorSet(currentE_.getActivity());
				if (succActivities != null && !succActivities.isEmpty()) {
					List<String> succEvents_ = new CopyOnWriteArrayList<>();
					// succActivities.parallelStream().forEach(act -> {
					succActivities.stream().forEach(acts -> {
						List<String> acts_ = new CopyOnWriteArrayList<>(acts);
						// in case of parallel predecessors
						if (acts.size() > 1) {
							// The flat version of the predecessors
							acts_.parallelStream().forEach(act -> {
								List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
								if (!preds_.isEmpty()) {
									// Set<String> temp = new HashSet<>();
									preds_.parallelStream().forEach(e -> {
										succEvents_.add("e" + e.getId());
									});
									// succEvents_.addAll(temp);
								}
							});
						} else {
							List<Event> preds_ = unlabeledLog.getActEventsBackward(acts_.get(0), currentIndex_.get());
							preds_.parallelStream().forEach(e -> {
								List<String> temp = new ArrayList<>();
								succEvents_.add("e" + e.getId());
							});
						}
					});

					if (!succEvents_.isEmpty())
						this.directSucEvents.put("e" + currentE_.getId(), succEvents_);

				}

			}

			// // direct predecessors
			if (relManager.getaJoinActivities().contains(currentE_.getActivity())) {
				HashSet<HashSet<String>> predActivities = relManager.getPredecessors(currentE_.getActivity());
				if (predActivities != null && !predActivities.isEmpty()) {
					List<String> predEvents_ = new CopyOnWriteArrayList<>();
					predActivities.stream().forEach(acts -> {
						List<String> acts_ = new CopyOnWriteArrayList<>(acts);
						// in case of parallel predecessors
						if (acts.size() > 1) {
							// The flat version of the predecessors
							acts_.parallelStream().forEach(act -> {
								List<Event> preds_ = unlabeledLog.getActEventsBackward(act, currentIndex_.get());
								if (!preds_.isEmpty()) {
									// Set<String> temp = new HashSet<>();
									preds_.parallelStream().forEach(e -> {
										predEvents_.add("e" + e.getId());
									});
									// predEvents_.addAll(temp);
								}
							});
						}
						// xor single element in the set
						else {
							List<Event> preds_ = unlabeledLog.getActEventsBackward(acts_.get(0), currentIndex_.get());
							preds_.parallelStream().forEach(e -> {
								List<String> temp = new ArrayList<>();
								predEvents_.add("e" + e.getId());
							});
						}
					});
					if (!predEvents_.isEmpty())
						this.directPredEvents.put("e" + currentE_.getId(), predEvents_);
				}
			}
		}
	}

	public int timeDifference(int e1Index, int e2Index) {
		int diff = (int) ((this.unlabeledLog.getEvents().get(e2Index).getTime()
				- this.unlabeledLog.getEvents().get(e1Index).getTime()) / 36000000);// in mins

		return diff;
	}

	public List<Integer> getnActpredBoundaries(int eventId) {
		String Act = this.unlabeledLog.getEvents().get(eventId).getActivity();
		if (this.predBoundaries.containsKey(Act)) {
			return this.predBoundaries.get(Act);
		} else {
			HashSet<HashSet<String>> predActivities = relManager.getPredecessors(Act);
			int min = Integer.MAX_VALUE;
			int max = -1;
			for (HashSet<String> p : predActivities) {
				if (p.size() > max)
					max = p.size();
				if (p.size() < min)
					min = p.size();
			}
			List<Integer> boundaries = new ArrayList<>();
			boundaries.add(0, min);
			boundaries.add(1, max);
			return boundaries;
		}
	}

	/****
	 * @return list of integer where 0 is the min number of successor and 1 is
	 *         the max no of successors
	 */
	public List<Integer> getActSuccessorBoundaries(int eventId) {
		String Act = this.unlabeledLog.getEvents().get(eventId).getActivity();
		if (this.succMaxBoundaries.containsKey(Act)) {
			return this.succMaxBoundaries.get(Act);
		} else {
			HashSet<HashSet<String>> predActivities = relManager.getSuccessorSet(Act);
			int min = Integer.MAX_VALUE;
			int max = -1;
			for (HashSet<String> p : predActivities) {
				if (p.size() > max)
					max = p.size();
				if (p.size() < min)
					min = p.size();
			}
			List<Integer> boundaries = new ArrayList<>();
			boundaries.add(0, min);
			boundaries.add(1, max);
			return boundaries;
		}
	}

	public boolean hasXorSucc(int eventId) {
		String Act = this.unlabeledLog.getEvents().get(eventId).getActivity();
		HashSet<HashSet<String>> s = relManager.getSuccessorSet(Act);
		if (s.size() > 1)
			return true;
		return false;
	}

	public void buildActbasedDictionaries() {
		exactOneEvents = new ConcurrentHashMap<String, List<String>>();
		atLeastOneEvents = new ConcurrentHashMap<String, List<String>>();
		atMostOneEvents = new ConcurrentHashMap<String, List<String>>();
		startEvents = new CopyOnWriteArrayList<>();
		endEvents = new CopyOnWriteArrayList<>();
		// Hashtable<String, List<String>> filteredLists =
		// relManager.getActsMustOcu();
		// List<String> existOnce = filteredLists.get("seqAcyclicList");

		// existOnce.parallelStream().forEach(act -> {
		// List<String> temp = new CopyOnWriteArrayList<>();
		// unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
		// temp.add("e" + e.getId());
		// });
		// if (!temp.isEmpty())
		// exactOneEvents.put(act, temp);
		// });
		//
		// List<String> atMostOnce = filteredLists.get("xorAcyclicList");
		// atMostOnce.parallelStream().forEach(act -> {
		// List<String> temp = new CopyOnWriteArrayList<>();
		// unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
		// temp.add("e" + e.getId());
		// });
		// if (!temp.isEmpty())
		// atMostOneEvents.put(act, temp);
		// });
		//
		// List<String> atLeastOnce = relManager.getLoopInElementsMustOcc();
		// atLeastOnce.parallelStream().forEach(act -> {
		// List<String> temp = new CopyOnWriteArrayList<>();
		// unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
		// temp.add("e" + e.getId());
		// });
		// if (!temp.isEmpty())
		// atLeastOneEvents.put(act, temp);
		// });

		ConcurrentHashMap<String, Boolean> actsStatus = new ConcurrentHashMap<>(this.relManager.getActivitiesInLoop());
		actsStatus.keySet().parallelStream().forEach(a -> {
			if (actsStatus.get(a)) {
				List<String> temp = new CopyOnWriteArrayList<>();
				unlabeledLog.getActEvents(a).parallelStream().forEach(e -> {
					temp.add("e" + e.getId());
				});
				if (!temp.isEmpty())
					atLeastOneEvents.put(a, temp);
			} else {
				List<String> temp = new CopyOnWriteArrayList<>();
				unlabeledLog.getActEvents(a).parallelStream().forEach(e -> {
					temp.add("e" + e.getId());
				});
				if (!temp.isEmpty())
					exactOneEvents.put(a, temp);
			}

		});

		List<String> startActs = relManager.getStartActivities();
//		System.out.println(startActs);
		startActs.stream().forEach(sact -> {
			unlabeledLog.getActEvents(sact).parallelStream().forEach(e -> {
				this.startEvents.add(e.getId());
			});
		});
		Collections.sort(this.startEvents);
		unlabeledLog.setnCases(this.startEvents.size());

		// one end
		List<String> endActs = relManager.getEndActivities();
		endActs.stream().forEach(eact -> {
			unlabeledLog.getActEvents(eact).parallelStream().forEach(e -> {
				this.endEvents.add(e.getId());
			});
		});
		Collections.sort(this.endEvents);

	}

	public Log getUnlabeledLog() {
		return unlabeledLog;
	}

	public void setUnlabeledLog(Log unlabeledLog) {
		this.unlabeledLog = unlabeledLog;
	}

	public ConcurrentHashMap<String, List<String>> getXorEvents() {
		return xorEvents;
	}

	public ConcurrentHashMap<String, List<String>> getXorEventsFiltered() {
		for (String e : this.xorEvents.keySet()) {

		}
		return xorEvents;
	}

	public void setXorEvents(ConcurrentHashMap<String, List<String>> xorEvents) {
		this.xorEvents = xorEvents;
	}

	public ConcurrentHashMap<String, List<String>> getDirectPredEvents() {
		return directPredEvents;
	}

	public void setDirectPredEvents(ConcurrentHashMap<String, List<String>> directPredEvents) {
		this.directPredEvents = directPredEvents;
	}

	public ConcurrentHashMap<String, List<String>> getDirectSucEvents() {
		return directSucEvents;
	}

	public void setDirectSucEvents(ConcurrentHashMap<String, List<String>> directSucEvents) {
		this.directSucEvents = directSucEvents;
	}

	public ConcurrentHashMap<String, List<String>> getEventualSucEvents() {
		return eventualSucEvents;
	}

	public void setEventualSucEvents(ConcurrentHashMap<String, List<String>> eventualSucEvents) {
		this.eventualSucEvents = eventualSucEvents;
	}

	public ConcurrentHashMap<String, List<String>> getExactOneEvents() {
		return exactOneEvents;
	}

	public void setExactOneEvents(ConcurrentHashMap<String, List<String>> exactOneEvents) {
		this.exactOneEvents = exactOneEvents;
	}

	public ConcurrentHashMap<String, List<String>> getAtLeastOneEvents() {
		return atLeastOneEvents;
	}

	public void setAtLeastOneEvents(ConcurrentHashMap<String, List<String>> atLeastOneEvents) {
		this.atLeastOneEvents = atLeastOneEvents;
	}

	public ConcurrentHashMap<String, List<String>> getAtMostOneEvents() {
		return atMostOneEvents;
	}

	public void setAtMostOneEvents(ConcurrentHashMap<String, List<String>> atMostOneEvents) {
		this.atMostOneEvents = atMostOneEvents;
	}

	public CopyOnWriteArrayList<Integer> getStartEvents() {
		return startEvents;
	}

	public CopyOnWriteArrayList<Integer> getEndEvents() {
		return endEvents;
	}

	public void setEndEvents(CopyOnWriteArrayList<Integer> endEvents) {
		this.endEvents = endEvents;
	}

	public void setStartEvents(CopyOnWriteArrayList<Integer> startEvents) {
		this.startEvents = startEvents;
	}

	// to be changed into execution service threads
	public void buildDictionaries() {
		// buildXorSuccPredDictionaries();
		// buildXorSuccPredSetsDictionaries();
		buildXorSuccPredSetDict();
		buildActbasedDictionaries();
		// buildSuccPredDictionaries();
		// ExecutorService executorService = Executors.newCachedThreadPool();
		//
		// Future<Void> future1 =
		// executorService.submit(buildXorSuccPredDictionaries);
		// Future<Void> future2 =
		// executorService.submit(buildActbasedDictionaries);
		// try {
		// future1.get();
		// future2.get();
		// } catch (InterruptedException | ExecutionException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// executorService.shutdown();
	}

	public void printDictionaries() {
		System.out.println("this.directPredEvents");
		System.out.println(this.directPredEvents);
		System.out.println("this.directSucEvents");
		System.out.println(this.directSucEvents);
		System.out.println("this.atLeastOneEvents");
		System.out.println(this.atLeastOneEvents);
		System.out.println("this.exactOneEvents");
		System.out.println(this.exactOneEvents);
		// System.out.println("this.atMostOneEvents");
		// System.out.println(this.atMostOneEvents);
		System.out.println("this.startEvents");
		System.out.println(this.startEvents);
		System.out.println("this.xorEvents");
		System.out.println(this.xorEvents);

	}

	public int getNcases() {
		return this.unlabeledLog.getnCases();
	}

	// Callable<Void> buildXorSuccPredDictionaries = () -> {
	//
	// xorEvents = new ConcurrentHashMap<String, List<String>>();
	// directPredEvents = new ConcurrentHashMap<String, List<List<String>>>();
	// directSucEvents = new ConcurrentHashMap<String, List<String>>();
	// // may be later to be added based on the bp
	// // eventualSucEvents = new ConcurrentHashMap<String, List<String>>();
	// CopyOnWriteArrayList<Event> events = new
	// CopyOnWriteArrayList<>(unlabeledLog.getEvents());
	// for (int i = 0; i < unlabeledLog.getnEvents(); i++) {
	// Event currentE_ = events.get(i);
	// AtomicInteger currentIndex_ = new AtomicInteger(i);
	//
	// // adding the xor events in dictionary
	// List<String> xorActivities =
	// relManager.getEclusiveActivities(currentE_.getActivity());
	// if (xorActivities != null && !xorActivities.isEmpty()) {
	// List<String> xorEvents_ = new CopyOnWriteArrayList<>();
	// xorActivities.parallelStream().forEach(act -> {
	// List<Event> xorEventsL = unlabeledLog.getActEvents(act,
	// currentIndex_.get());
	// xorEventsL.parallelStream().forEach(e -> {
	// xorEvents_.add("e" + e.getId());
	// });
	// });
	// if (!xorEvents_.isEmpty())
	// xorEvents.put("e" + currentE_.getId(), xorEvents_);
	// }
	// // adding the direct successors in dictionary
	// // N.B. includes sequence and parallel
	// List<String> succActivities =
	// relManager.getSuccessors(currentE_.getActivity());
	// if (succActivities != null && !succActivities.isEmpty()) {
	// List<String> succEvents_ = new CopyOnWriteArrayList<>();
	// // succActivities.parallelStream().forEach(act -> {
	// for (String act : succActivities) {
	//
	// List<Event> succEvents = unlabeledLog.getActEvents(act,
	// currentIndex_.get());
	// succEvents.parallelStream().forEach(e -> {
	// succEvents_.add("e" + e.getId());
	// });
	// }
	// // });
	// if (!succEvents_.isEmpty())
	// this.directSucEvents.put("e" + currentE_.getId(), succEvents_);
	// }
	// // direct predecessors
	// HashSet<HashSet<String>> predActivities =
	// relManager.getPredecessors(currentE_.getActivity());
	// if (predActivities != null && !predActivities.isEmpty()) {
	// List<List<String>> predEvents_ = new CopyOnWriteArrayList<>();
	// predActivities.stream().forEach(acts -> {
	// List<String> acts_ = new CopyOnWriteArrayList<>(acts);
	// // in case of parallel predecessors
	// if (acts.size() > 1) {
	// // we need to do a cartesian product between the events
	// // lists
	// List<Set<String>> parallelEvents = new CopyOnWriteArrayList<>();
	// acts_.parallelStream().forEach(act -> {
	// List<Event> preds_ = unlabeledLog.getActEventsBackward(act,
	// currentIndex_.get());
	// if (!preds_.isEmpty()) {
	// Set<String> temp = new HashSet<>();
	// preds_.parallelStream().forEach(e -> {
	// temp.add("e" + e.getId());
	// });
	// parallelEvents.add(temp);
	// }
	// });
	// if (!parallelEvents.isEmpty()) {
	// List<List<String>> parallelCPsets = new ArrayList<>(
	// com.google.common.collect.Sets.cartesianProduct(parallelEvents));
	// predEvents_.addAll(parallelCPsets);
	// }
	// }
	// // xor single element in the set
	// else {
	// List<Event> preds_ = unlabeledLog.getActEventsBackward(acts_.get(0),
	// currentIndex_.get());
	// preds_.parallelStream().forEach(e -> {
	// List<String> temp = new ArrayList<>();
	// temp.add("e" + e.getId());
	// predEvents_.add(temp);
	// });
	// }
	// });
	// if (!predEvents_.isEmpty())
	// this.directPredEvents.put("e" + currentE_.getId(), predEvents_);
	// }
	// }
	// return null;
	// };
	Callable<Void> buildActbasedDictionaries = () -> {
		exactOneEvents = new ConcurrentHashMap<String, List<String>>();
		atLeastOneEvents = new ConcurrentHashMap<String, List<String>>();
		atMostOneEvents = new ConcurrentHashMap<String, List<String>>();
		startEvents = new CopyOnWriteArrayList<>();
		Hashtable<String, List<String>> filteredLists = relManager.getActsMustOcu();
		List<String> existOnce = filteredLists.get("seqAcyclicList");

		existOnce.parallelStream().forEach(act -> {
			List<String> temp = new CopyOnWriteArrayList<>();
			unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
				temp.add("e" + e.getId());
			});
			if (!temp.isEmpty())
				exactOneEvents.put(act, temp);
		});

		List<String> atMostOnce = filteredLists.get("xorAcyclicList");
		atMostOnce.parallelStream().forEach(act -> {
			List<String> temp = new CopyOnWriteArrayList<>();
			unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
				temp.add("e" + e.getId());
			});
			if (!temp.isEmpty())
				atMostOneEvents.put(act, temp);
		});

		List<String> atLeastOnce = relManager.getLoopInElementsMustOcc();
		atLeastOnce.parallelStream().forEach(act -> {
			List<String> temp = new CopyOnWriteArrayList<>();
			unlabeledLog.getActEvents(act).parallelStream().forEach(e -> {
				temp.add("e" + e.getId());
			});
			if (!temp.isEmpty())
				atLeastOneEvents.put(act, temp);
		});

		List<String> startActs = relManager.getStartActivities();
		startActs.stream().forEach(sact -> {
			unlabeledLog.getActEvents(sact).parallelStream().forEach(e -> {
				this.startEvents.add(e.getId());
			});
		});

		return null;

	};

	public void updateEventsCIds(List<String> vars) {
		this.unlabeledLog.updateEventCIds(vars);

	}

	public void writeCorrelatedLog(String filename) {
		this.unlabeledLog.writeLogCSV(filename);
	}

	public void writeCorrelatedLog(Log temp, String filename) {
		temp.writeLogCSV(filename);
	}

	public void updateEventsCIds(ConcurrentHashMap<String, Integer> vars) {
		// TODO Auto-generated method stub
		this.unlabeledLog.updateEventCIds(vars);

	}

	public void updateEventsCIds(Log update, ConcurrentHashMap<String, Integer> vars) {
		// TODO Auto-generated method stub
		update.updateEventCIds(vars);

	}

	public Set<String> getExactOneEventsFL() {
		// TODO Auto-generated method stub
		return  this.exactOneEvents.values().stream().flatMap(p -> p.stream()).collect(Collectors.toSet());
	
	}

}
