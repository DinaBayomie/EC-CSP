package correlation;

import java.io.File;
import java.io.PrintStream;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.objective.ObjectiveStrategy;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.loop.monitors.IMonitorDownBranch;
import org.chocosolver.solver.search.loop.monitors.IMonitorOpenNode;
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.ActivityBased;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.StrategiesSequencer;
import org.chocosolver.solver.trace.IMessage;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;
import org.chocosolver.util.tools.StringUtils;

import preprocessing.Event;
import preprocessing.Log;
import preprocessing.LogPreprocessingManager;
import static org.chocosolver.solver.search.strategy.Search.*;

public class ECmodelCreatorInt {

	Model model = new Model("event correlation");
	IntVar[] eventVars;
	LogPreprocessingManager logManager;
	ConcurrentHashMap<String, IntVar> variables;
	private IntVar obj;
	private IntVar CTs;

	public ECmodelCreatorInt(LogPreprocessingManager logManager, String processName) {
		super();
		this.logManager = logManager;
		long start = System.currentTimeMillis();
		addModelVariables(false);

		// printVariables();
		// addSumConstraints();
		// addSturctureConstraints();
		// addStructureComplexConstraints();
		addSturctureConstraintsV3(processName);
		// System.out.println("Print constraints using getcstr");
		// printConstraints();

		addObjectiveFunction();
		long end = System.currentTimeMillis();

		System.out.println(processName + " build model in MS " + (end - start));
		System.out.println(processName + " nVar = " + this.getNvar());
		System.out.println(processName + " nVar events= " + this.variables.size());

		System.out.println(processName + " nConstraints = " + this.getModel().getNbCstrs());
	}

	public List<Solution> buildandRunModel(int nSol, PrintStream outStat) {
		System.out.println("start the solver");
		// List<IntVar> intvarND = new CopyOnWriteArrayList<IntVar>();
		// List<IntVar> intvarAll = new CopyOnWriteArrayList<IntVar>();
		IntVar[] varsAll = new IntVar[this.variables.size() + 2];
		IntVar[] vars = new IntVar[this.variables.size() - this.logManager.getStartEvents().size()];// +1
		// List<IntVar> startCases = new ArrayList<>();
		int i = 0;
		int j = 0;
		for (IntVar v : this.variables.values()) {
			// this.variables.values().parallelStream().forEach(boolVar -> {
			// if (boolVar.getDomainSize() ==this.logManager.getNcases()) {
			varsAll[i++] = v;

			if (!v.isAConstant()) {
				// if (boolVar.getDomainSize() == this.logManager.getNcases())
				// intvarNcdomain.add(boolVar);
				// else
				// if (boolVar.getDomainSize() == 2)
				// intvarND.add(v);
				vars[j++] = v;

				// intvarAll.add(v);

			}
			// else {
			// startCases.add(v);
			// intvarAll.add(v);
			// }
			// else {
			// intvar1domain.add(boolVar);
			// varsAll[i]=boolVar;}
		}
		// vars[j] = this.obj;
		varsAll[i++] = this.obj;
		varsAll[i] = this.CTs;
		// });
		// i = 0;
		// intvarND.add(obj);
		// intvarAll.add(obj);
		//// IntVar[] vars = new IntVar[intvarND.size()];
		// for (IntVar boolVar : intvarND) {
		// vars[i] = boolVar;
		// // varsAll[i] = boolVar;
		// i++;
		//
		// }
		// int j = 0;
		// for (IntVar boolVar : intvarAll) {
		// varsAll[j] = boolVar;
		// j++;
		// }
		model.getSettings().setEnableViews(true);
		model.getSettings().setEnableSAT(true);
		model.getSettings().setEnableDecompositionOfBooleanSum(true);
		model.getSettings().setEnableTableSubstitution(true);
		model.getSettings().setHybridizationOfPropagationEngine((byte) 0b01);
		model.getSettings().setMaxTupleSizeForSubstitution(this.logManager.getNcases());
		model.getSettings().setWarnUser(true);
		model.getSettings().setSortPropagatorActivationWRTPriority(true);
		model.getSettings().setRatioForClauseStoreReduction((float) 0.2);

		long start = System.currentTimeMillis();
		this.model.setObjective(Model.MINIMIZE, obj);
		System.out.println("|Vars| = " + vars.length);
		Solver solver = model.getSolver();
		int ns = 0;
		List<Solution> solutions = new ArrayList<>();
		// solver.setSearch(activityBasedSearch(vars));
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();
		// // solver.setSearch(intVarSearch(vars));
		solver.setSearch(activityBasedSearch(vars), inputOrderLBSearch(vars),
				intVarSearch(new FirstFail(vars[0].getModel()), new IntDomainMin(), vars));// ,
		// randomSearch(vars, logManager.getNcases()));
		solver.setSearch(lastConflict(solver.getSearch(), this.logManager.getNcases()));
		conflictOrderingSearch(solver.getSearch());

		solver.getObjectiveManager().setWalkingDynamicCut();

		try {

			solver.getObjectiveManager().postDynamicCut();
			// solver.propagate();

		} catch (ContradictionException e) {
			// // TODO Auto-generated catch block
			e.printStackTrace();
		}
		// solver.setRestartOnSolutions();
		while (solver.solve() && ns < nSol) {

			Solution s = new Solution(this.model, varsAll);
			s.record();
			// System.out.println("------ solution"+ns+"--------");
			// for (IntVar v : startCases) {
			// System.out.println(v.getName()+" = "+ v.getValue() +" - " +
			// v.getDomainSize() );
			// }
			// System.out.println(this.obj.getName() + " = " +
			// s.getIntVal(this.obj));
			// System.out.println("CTs = " + s.getIntVal(this.CTs));
			solutions.add(s);
			ns++;
			// System.out.println("ns" + ns);
		}
		// solutions=solver.findAllOptimalSolutions(obj, Model.MINIMIZE, stop)

		solver.setOut(outStat);
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solutions;
	}

	public List<Solution> buildandRunModelOpt1() {
		System.out.println("start the solver");
		List<IntVar> intvar2domain = new CopyOnWriteArrayList();
		List<IntVar> intvarNcdomain = new CopyOnWriteArrayList();
		IntVar[] varsAll = new IntVar[this.variables.size()];
		List<IntVar> startCases = new ArrayList<>();
		int i = 0;
		for (IntVar boolVar : this.variables.values()) {
			// this.variables.values().parallelStream().forEach(boolVar -> {
			// if (boolVar.getDomainSize() ==this.logManager.getNcases()) {
			varsAll[i] = boolVar;
			i++;
			if (!boolVar.isAConstant()) {
				// if (boolVar.getDomainSize() == this.logManager.getNcases())
				// intvarNcdomain.add(boolVar);
				// else
				// if (boolVar.getDomainSize() == 2)
				intvar2domain.add(boolVar);

			} else {
				startCases.add(boolVar);
			}
			// else {
			// intvar1domain.add(boolVar);
			// varsAll[i]=boolVar;}
		}
		// });
		i = 0;
		intvar2domain.add(obj);
		IntVar[] vars = new IntVar[intvar2domain.size()];
		for (IntVar boolVar : intvar2domain) {
			vars[i] = boolVar;
			// varsAll[i] = boolVar;
			i++;

		}
		int j = 0;
		for (IntVar boolVar : intvarNcdomain) {
			varsAll[j] = boolVar;
			j++;
		}
		model.getSettings().setEnableViews(true);
		model.getSettings().setEnableSAT(true);
		model.getSettings().setEnableDecompositionOfBooleanSum(true);
		// model.getSettings().setEnableTableSubstitution(true);
		model.getSettings().setHybridizationOfPropagationEngine((byte) 0b01);
		// model.getSettings().setMaxTupleSizeForSubstitution(this.logManager.getNcases());
		model.getSettings().setWarnUser(true);
		model.getSettings().setSortPropagatorActivationWRTPriority(true);
		model.getSettings().setRatioForClauseStoreReduction((float) 0.2);

		long start = System.currentTimeMillis();
		this.model.setObjective(Model.MINIMIZE, obj);
		System.out.println("|Vars| = " + vars.length);
		Solver solver = model.getSolver();
		int ns = 0;
		List<Solution> solutions = new ArrayList<>();
		// solver.setSearch(activityBasedSearch(vars));
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();
		// // solver.setSearch(intVarSearch(vars));
		solver.setSearch(activityBasedSearch(vars), inputOrderUBSearch(vars), intVarSearch(vars));// ,
		// randomSearch(vars, logManager.getNcases()));
		solver.setSearch(lastConflict(solver.getSearch(), this.logManager.getNcases()));
		conflictOrderingSearch(solver.getSearch());
		solver.getObjectiveManager().setWalkingDynamicCut();

		solutions = solver.findAllOptimalSolutions(obj, Model.MINIMIZE);
		// solutions=solver.findAllOptimalSolutions(obj, Model.MINIMIZE, stop)
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solutions;
	}

	private void addObjectiveFunction() {
		// time diff as intVar
		System.out.println("adding objective condition");
		List<BoolVar> boolEndConstVars = new CopyOnWriteArrayList<>();
		List<Integer> timeDiff = new CopyOnWriteArrayList<>();
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());
		// AtomicInteger reifyVarCounter = new AtomicInteger(0);

		for (int i = startIndices.size() - 1; i > 0; i++) {

			if (i % 10 == 0) {
				int eS = startIndices.get(i);
				// AtomicInteger cId = new
				// AtomicInteger(startIndices.indexOf(eS));
				List<Integer> endIndices = new CopyOnWriteArrayList<>(logManager.getEndEvents());
				int count = 0;
				for (int j = 0; j < endIndices.size(); j++) {
					int eE = endIndices.get(j);
					if (eE > eS) {
						count++;
						int diff = this.logManager.timeDifference(eS, eE);
						timeDiff.add(diff);
						BoolVar objC = model.boolVar(eS + "-" + eE + "objCont_");
						model.arithm(variables.get("e" + eS), "=", variables.get("e" + eE)).reifyWith(objC);
						boolEndConstVars.add(objC);
					}
					if (count == 10)
						break;
				}
			}
		}
	
	// startIndices.parallelStream().forEach(eS -> {
	// AtomicInteger cId = new AtomicInteger(startIndices.indexOf(eS));
	// List<Integer> endIndices = new
	// CopyOnWriteArrayList<>(logManager.getEndEvents());
	//
	// endIndices.parallelStream().forEach(eE -> {
	// int diff = this.logManager.timeDifference(eS, eE);
	// timeDiff.add(diff);
	// BoolVar objC = model.boolVar(eS + "-" + eE + "objCont_");
	// model.arithm(variables.get("e" + eS), "=", variables.get("e" +
	// eE)).reifyWith(objC);
	// boolEndConstVars.add(objC);
	// });
	// });

	BoolVar[] endVarsA = new BoolVar[boolEndConstVars.size()];
	int[] diffsA = new int[timeDiff.size()];for(
	int i = 0;i<boolEndConstVars.size();i++)
	{
		endVarsA[i] = boolEndConstVars.get(i);
		diffsA[i] = timeDiff.get(i); // Math.abs(timeDiff.get(i));
	}

	this.obj=this.model.intVar("Objective avgCT--Min",0,Integer.MAX_VALUE-10,true);this.CTs=this.model.intVar("total CT",0,Integer.MAX_VALUE-10,true);this.model.scalar(endVarsA,diffsA,"=",CTs).post();

	IntVar nCases = this.model.intVar("nCases",
			this.logManager.getStartEvents().size());this.model.div(CTs,nCases,obj).post();
	}

	/***
	 * Add an integer variable to the model per each event with a range from 0
	 * till the last case open before event A1,A2,B3 e0 = 0 , e1=0, e2 in [0,1]
	 * will be a value in this range
	 */
	private void addModelVariables(boolean isBounded) {
		List<Event> events = new CopyOnWriteArrayList<>(logManager.getUnlabeledLog().getEvents());
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());

		variables = new ConcurrentHashMap<>(events.size());
		// System.out.println(startIndices);
		events.stream().forEach(e -> {
			int index = startIndices.indexOf(e.getId());

			// start event [ as fixed variables]
			if (index != -1) {
				IntVar v = model.intVar("e" + e.getId(), index);
				variables.put(v.getName(), v);
				// System.out.println(v.getName() + ":" + v.getValue() + "-" +
				// v.getDomainSize());
			}
			// all other events
			else {
				// AtomicInteger eIndex = new AtomicInteger(e.getId());
				int largestCId = Collections.binarySearch(startIndices, e.getId());
				int insertion_point = -(largestCId + 2);
				if (insertion_point > 0)
				// IntStream.range(0, insertion_point +
				// 1).sequential().forEach(cId -> {
				// BoolVar v = model.boolVar("c." + cId + "_e" + eIndex.get());
				// variables.put("c." + cId + "_e" + e.getId(), v);
				// });
				{
					// so 0 means not assigned
					IntVar v = model.intVar("e" + e.getId(), 0, insertion_point, isBounded);
					variables.put(v.getName(), v);

				} else {
					IntVar v = model.intVar("e" + e.getId(), insertion_point);
					variables.put(v.getName(), v);
				}
			}
		});
		// eventVars = this.model.retrieveIntVars(false);
		eventVars = new IntVar[variables.size()];

		int i = 0;
		for (String k : variables.keySet()) {
			eventVars[i++] = variables.get(k);
		}
	}

	/***
	 * This function at the constraints related to sum conditions, i.e.,
	 * assigning to 1 case only, handling xor relation, exact one occurrence of
	 * an activity within a case, at least one occurrence if there is a loop, at
	 * most one occurrence if there is an xor activities
	 */
	private void addSumConstraints() {
		System.out.println("Add sum constraints");
		// An event belongs to 1 case only satisified by the var type as now the
		// domain is the case ids.

		// // exact occurrence of an activity
		// ConcurrentHashMap<String, List<String>> exactOne =
		// this.logManager.getExactOneEvents();
		// if (exactOne != null && !exactOne.isEmpty()) {
		// for (String act : exactOne.keySet()) {
		// List<String> events = exactOne.get(act);
		// for (int i = 0; i < this.logManager.getNcases(); i++) {
		// List<BoolVar> temp = new ArrayList<>();
		// for (String e : events) {
		// String varName = "c." + i + "_" + e;
		// if (variables.containsKey(varName))
		// temp.add(this.variables.get(varName));
		// }
		// if (!temp.isEmpty()) {
		// // BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
		// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
		// for (int k = 0; k < temp.size(); k++) {
		// boolVarTemp[k] = temp.get(k);
		// }
		// this.model.sum(boolVarTemp, "=", 1).post();
		// }
		// }
		// }
		// }
		//
		// // AtLeast once
		// ConcurrentHashMap<String, List<String>> atleastOnce =
		// this.logManager.getAtLeastOneEvents();
		// if (atleastOnce != null && !atleastOnce.isEmpty()) {
		// for (String act : atleastOnce.keySet()) {
		// List<String> events = atleastOnce.get(act);
		// for (int i = 0; i < this.logManager.getNcases(); i++) {
		// List<BoolVar> temp = new ArrayList<>();
		// for (String e : events) {
		// String varName = "c." + i + "_" + e;
		// if (variables.containsKey(varName))
		// temp.add(this.variables.get(varName));
		// }
		// if (!temp.isEmpty()) {
		// // BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
		// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
		// for (int k = 0; k < temp.size(); k++) {
		// boolVarTemp[k] = temp.get(k);
		// }
		// this.model.sum(boolVarTemp, ">=", 1).post();
		// }
		// }
		// }
		// }
		//
		// // At most once [ to represent the occurrence of the xor in a case
		// ConcurrentHashMap<String, List<String>> atmostOnce =
		// this.logManager.getAtMostOneEvents();
		// if (atmostOnce != null && !atmostOnce.isEmpty()) {
		// for (String act : atmostOnce.keySet()) {
		// List<String> events = atmostOnce.get(act);
		// for (int i = 0; i < this.logManager.getNcases(); i++) {
		// List<BoolVar> temp = new ArrayList<>();
		// for (String e : events) {
		// String varName = "c." + i + "_" + e;
		// if (variables.containsKey(varName))
		// temp.add(this.variables.get(varName));
		// }
		// if (!temp.isEmpty()) {
		// // BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
		// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
		// for (int k = 0; k < temp.size(); k++) {
		// boolVarTemp[k] = temp.get(k);
		// }
		// this.model.sum(boolVarTemp, "<=", 1).reify();
		// }
		// }
		// }
		// }
	}

	private void addSturctureConstraints() {
		int reifyVarCounter = 0;
		List<IntVar> eventVars = new CopyOnWriteArrayList<>(this.variables.values());
		// XOR
		// System.out.println("Xor");
		//
		ConcurrentHashMap<String, List<String>> xorsDict = this.logManager.getXorEvents();
		List<IntVar> xorEvents_ = new CopyOnWriteArrayList<>();
		if (xorsDict != null && !xorsDict.isEmpty()) {
			for (int i = 0; i < eventVars.size(); i++) {
				IntVar var1 = eventVars.get(i);
				String e1 = var1.getName();
				if (xorsDict.containsKey(e1)) {

					List<String> xorEvents = xorsDict.get(e1);
					// List<IntVar> temp = new ArrayList<>();
					// for (String e2 : xorEvents) {
					// if (variables.containsKey(e2))
					// temp.add(this.variables.get(e2));
					// }
					// if (!temp.isEmpty() && !xorEvents_.containsAll(temp)) {
					if (!xorEvents.isEmpty()) {// &&
						// !xorEvents_.containsAll(xorEvents))
						// {
						xorEvents_.add(var1);
						// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						BoolVar[] boolVarTemp = new BoolVar[xorEvents.size()];
						for (int k = 0; k < xorEvents.size(); k++) {
							IntVar xVar = this.variables.get(xorEvents.get(k));
							boolVarTemp[k] = model.boolVar(xVar.getName() + "ORCont_" + reifyVarCounter);
							reifyVarCounter++;
							// IntVar[] bool_ = new IntVar[2];
							// bool_[0] = var1;
							// bool_[1] = xVar;
							// model.notAllEqual(bool_).reifyWith(boolVarTemp[k]);

							model.arithm(var1, "!=", xVar).reifyWith(boolVarTemp[k]);
						}
						this.model.and(boolVarTemp).post();
					}
				}
			}
		}

		System.out.println("successors");
		// add successors
		ConcurrentHashMap<String, List<String>> directSeqDict = this.logManager.getDirectSucEvents();
		if (directSeqDict != null && !directSeqDict.isEmpty()) {
			for (int i = 0; i < eventVars.size(); i++) {
				IntVar var1 = eventVars.get(i);
				String e1 = var1.getName();

				if (directSeqDict.containsKey(e1)) {

					List<String> seqEvents = directSeqDict.get(e1);
					if (!seqEvents.isEmpty()) {
						BoolVar[] boolVarTemp = new BoolVar[seqEvents.size()];
						for (int k = 0; k < seqEvents.size(); k++) {
							IntVar sVar = this.variables.get(seqEvents.get(k));
							boolVarTemp[k] = model.boolVar(sVar.getName() + "SuccCont_" + reifyVarCounter);
							reifyVarCounter++;

							model.arithm(var1, "=", sVar).reifyWith(boolVarTemp[k]);
							// IntVar[] bool_ = new IntVar[2];
							// bool_[0] = var1;
							// bool_[1] = sVar;
							// model.notAllEqual(bool_).reifyWith(boolVarTemp[k]);

						}
						// if (boolVarTemp.length > 1)
						// this.model.or(boolVarTemp).post();
						int maxSuccs = this.logManager.getActSuccessorBoundaries(Integer.parseInt(e1.split("e")[1]))
								.get(1);
						if (boolVarTemp.length < maxSuccs)
							this.model.sum(boolVarTemp, "<", maxSuccs);
						else
							this.model.sum(boolVarTemp, "=", maxSuccs);

					}
				}
			}
		}

		// add predecessors
		System.out.println("pred");
		ConcurrentHashMap<String, List<String>> directpredDict = this.logManager.getDirectPredEvents();
		if (directpredDict != null && !directpredDict.isEmpty()) {
			for (int i = 0; i < eventVars.size(); i++) {
				IntVar var1 = eventVars.get(i);
				String e1 = var1.getName();
				if (directpredDict.containsKey(e1)) {

					List<String> predEvents = directpredDict.get(e1);
					BoolVar[] boolVarTemp = new BoolVar[predEvents.size()];
					if (!predEvents.isEmpty()) {
						for (int k = 0; k < predEvents.size(); k++) {
							IntVar pVar = this.variables.get(predEvents.get(k));
							boolVarTemp[k] = model.boolVar(pVar.getName() + "predCont_" + reifyVarCounter);
							reifyVarCounter++;
							// IntVar[] bool_ = new IntVar[2];
							// bool_[0] = var1;
							// bool_[1] = pVar;
							// model.notAllEqual(bool_).reifyWith(boolVarTemp[k]);
							model.arithm(var1, "=", pVar).reifyWith(boolVarTemp[k]);

						}
					}
					// if (boolVarTemp.length > 1)
					// // List<Integer> bound =
					// this.logManager.getnActpredBoundaries(Integer.parseInt(e1.split("e")[1]));

					int maxPred = this.logManager.getnActpredBoundaries(Integer.parseInt(e1.split("e")[1])).get(1);

					this.model.sum(boolVarTemp, "=", maxPred);

					// if (bound.get(0) != bound.get(1)) {
					// this.model.sum(boolVarTemp, ">=",
					// BOUND.GET(0)).POST();
					// this.model.sum(boolVarTemp, "<", bound.get(1) +
					// 1).post();
					// } else {
					// this.model.sum(boolVarTemp, "<=",
					// bound.get(0)).post();// as
					//
					// } // it
					// could
					// be
					// 0

				}
			}
		}

		// ConcurrentHashMap<String, List<String>> indirectSeq =
		// this.logManager.getEventualSucEvents();
		// if (indirectSeq != null && !indirectSeq.isEmpty()) {
		// for (int i = 0; i < boolVars.length; i++) {
		// BoolVar var1 = boolVars[i];
		// // System.out.println(var1.getName());
		// String e1 = var1.getName().split("_")[1];
		// String cId1 = var1.getName().split("_")[0];
		//
		// if (indirectSeq.containsKey(e1)) {
		//
		// List<String> seqEvents = indirectSeq.get(e1);
		// List<BoolVar> temp = new ArrayList<>();
		// for (String e2 : seqEvents) {
		// String varName = cId1 + "_" + e2;
		// if (variables.containsKey(varName))
		// temp.add(this.variables.get(varName));
		//
		// }
		// if (!temp.isEmpty()) {
		// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
		// for (int k = 0; k < temp.size(); k++) {
		// BoolVar[] bool_ = new BoolVar[2];
		// boolVarTemp[k] = model.boolVar(temp.get(k).getName() + "ORCont_" +
		// reifyVarCounter);
		// reifyVarCounter++;
		// // System.out.println(var1.toString() + "=" +
		// // temp.get(k).toString());
		// // model.arithm(var1, "=",
		// // temp.get(k)).reifyWith(boolVarTemp[k]);
		// bool_[0] = var1;
		// bool_[1] = temp.get(k);
		// model.allEqual(bool_).reifyWith(boolVarTemp[k]);
		//
		// }
		// // System.out.println("Or the above condition");
		//
		// // this.model.sum(boolVarTemp, ">=",
		// // var1.getValue()).reify();
		// this.model.or(boolVarTemp).reify();
		// }
		// }
		// }
		// }

	}

	private void addStructureComplexConstraints() {
		int reifyVarCounter = 0;
		List<IntVar> eventVars = new CopyOnWriteArrayList<>(this.variables.values());
		System.out.println("successors with the internal constraints");
		// add successors
		ConcurrentHashMap<String, List<String>> directSeqDict = this.logManager.getDirectSucEvents();
		if (directSeqDict != null && !directSeqDict.isEmpty()) {
			// for (int i = 0; i < eventVars.size(); i++) {
			// IntVar var1 = eventVars.get(i);
			for (String n : directSeqDict.keySet()) {
				IntVar var1 = this.variables.get(n);
				String e1 = var1.getName();
				// if (directSeqDict.containsKey(e1)) {
				List<String> seqEvents = directSeqDict.get(n);
				if (!seqEvents.isEmpty()) {
					List<BoolVar> bList = new CopyOnWriteArrayList<>();
					List<String> done = new CopyOnWriteArrayList<>();
					Hashtable<String, List<String>> xP = new Hashtable<String, List<String>>();
					for (int k = seqEvents.size() - 1; k >= 0; k--) {
						List<BoolVar> subOr = new CopyOnWriteArrayList<>();

						IntVar sVar = this.variables.get(seqEvents.get(k));
						if (!done.contains(seqEvents.get(k))) {
							done.add(seqEvents.get(k));
							BoolVar sBV = model
									.boolVar(var1.getName() + "-succ:-" + sVar.getName() + "--" + reifyVarCounter);
							reifyVarCounter++;

							model.arithm(var1, "=", sVar).reifyWith(sBV);
							subOr.add(sBV);
						}
						AtomicInteger xReifyVarCounter = new AtomicInteger(reifyVarCounter);

						if (this.logManager.getXorEvents().containsKey(sVar.getName())) {
							List<String> x = new CopyOnWriteArrayList<>(
									this.logManager.getXorEvents().get(sVar.getName()));
							List<String> t = new CopyOnWriteArrayList<>(seqEvents);
							t.retainAll(x);
							List<BoolVar> subAnd = new CopyOnWriteArrayList<>();

							// t.parallelStream().forEach(xA -> {
							// for (String xA : t) {
							for (int i = 0; i < t.size(); i++) {
								String xA = t.get(i);
								if (xP.containsKey(seqEvents.get(k)) && xP.get(seqEvents.get(k)).contains(xA))
									continue;
								IntVar xVar = this.variables.get(xA);

								BoolVar sxBV = model.boolVar(var1.getName() + "-succ:-" + sVar.getName() + "- # -"
										+ xVar.getName() + "--" + xReifyVarCounter.get());
								xReifyVarCounter.incrementAndGet();
								model.arithm(xVar, "!=", sVar).reifyWith(sxBV);
								subAnd.add(sxBV);
								if (!xP.containsKey(seqEvents.get(k)))
									xP.put(seqEvents.get(k), new ArrayList<>());
								xP.get(seqEvents.get(k)).add(xA);
								if (!xP.containsKey(xA))
									xP.put(xA, new ArrayList<>());
								xP.get(xA).add(seqEvents.get(k));

								for (int j = i + 1; j < t.size(); j++) {
									String xA2 = t.get(j);
									IntVar xV2 = this.variables.get(xA2);
									BoolVar xxBV = model.boolVar(var1.getName() + "-succ:-" + xVar.getName() + "- # -"
											+ xV2.getName() + "--" + xReifyVarCounter.get());
									xReifyVarCounter.incrementAndGet();
									model.arithm(xVar, "!=", xV2).reifyWith(xxBV);
									subAnd.add(xxBV);
									if (!xP.containsKey(xA))
										xP.put(xA, new ArrayList<>());
									if (!xP.containsKey(xA2))
										xP.put(xA2, new ArrayList<>());

									xP.get(xA).add(xA2);
									xP.get(xA2).add(xA);
								}
								done.add(xA);
								BoolVar sxoBV = model.boolVar(
										var1.getName() + "-succ:-" + xVar.getName() + "--" + xReifyVarCounter.get());
								xReifyVarCounter.incrementAndGet();

								model.arithm(var1, "=", xVar).reifyWith(sxoBV);
								subOr.add(sxoBV);

							}
							// });
							// shift lists to arrays
							BoolVar sAndBV = null;
							if (subAnd.size() > 0) {
								BoolVar[] bvAnd = new BoolVar[subAnd.size()];
								for (int j = 0; j < subAnd.size(); j++) {
									bvAnd[j] = subAnd.get(j);
								}

								sAndBV = model.boolVar("xorAnd" + xReifyVarCounter.get());
								xReifyVarCounter.incrementAndGet();

								this.model.and(bvAnd).reifyWith(sAndBV);
							}
							BoolVar sOrBV = null;
							if (subOr.size() > 0) {
								BoolVar[] bvOr = new BoolVar[subOr.size()];
								for (int j = 0; j < subOr.size(); j++) {
									bvOr[j] = subOr.get(j);
								}
								sOrBV = model.boolVar("succOr" + xReifyVarCounter.get());
								xReifyVarCounter.incrementAndGet();

								this.model.sum(bvOr, "=", 1).reifyWith(sOrBV);
							}
							if (sAndBV != null && sOrBV != null) {
								BoolVar bvAO = model.boolVar("xorAnd" + xReifyVarCounter.get());
								xReifyVarCounter.incrementAndGet();
								this.model.and(sOrBV, sAndBV).reifyWith(bvAO);
								bList.add(bvAO);
							} else if (sOrBV != null)
								bList.add(sOrBV);
						}
						reifyVarCounter = xReifyVarCounter.get() + 1;

					}
					if (bList.size() > 0) {
						BoolVar[] barray = new BoolVar[bList.size()];
						for (int j = 0; j < bList.size(); j++) {
							barray[j] = bList.get(j);
						}
						int maxSuccs = this.logManager.getActSuccessorBoundaries(Integer.parseInt(e1.split("e")[1]))
								.get(1);
						// if (barray.length < maxSuccs)
						// this.model.sum(barray, "<", maxSuccs).post();
						// else
						this.model.sum(barray, "<=", maxSuccs).post();
					}
				}
				// }
			}
			// }
		}

	}

	/***
	 * @return true if there is an event in the subevents belongs to a loop,
	 *         false otherwise
	 */
	private boolean checkCycEvents(List<String> subEvents) {
		for (String e : subEvents) {
			if (this.logManager.getAtLeastOneEvents().values().contains(e))
				return true;
		}
		return false;
	}

	/***
	 * @return true if all the events in the subevents doesnt belong to a loop,
	 *         false otherwise
	 */
	private boolean checkAllACycEvents(List<String> subEvents) {
		for (String e : subEvents) {
			if (!this.logManager.getExactOneEvents().values().contains(e))
				return false;
		}
		return true;
	}

	private void addSturctureConstraintsV3(String processName) {
		int reifyVarCounter = 0;
		// add successors
		// constraint types 1 and 2
		int sCount = addSuccCstr(reifyVarCounter);
		reifyVarCounter = sCount;
		System.out.println(processName + " number of successors type 1 and 2 constraints = " + sCount);

		// add predecessors
		// implementing constraint types 3 and 4
		int pCount = addPredCstr(reifyVarCounter);
		System.out.println(processName + " number of predecessors type 3 and 4 constraints = " + (pCount - sCount));
		reifyVarCounter = pCount;

		// add XOR constraints 5-a and 5-b
		int xCount = addLXorCstr(reifyVarCounter);
		System.out.println(processName + " number of local exclusiveness relation type 5, 6, 7 constraints = "
				+ (xCount - pCount));
		reifyVarCounter = xCount;
	}

	/***
	 * create the successors constraints [types 1 and 2 ] and add them to the
	 * model
	 * 
	 * @param rCount
	 *            is the reify-counter for the sub-constraints
	 * @return rcount to represent the number of the incremented constraints
	 */
	private int addSuccCstr(int rCount) {
		System.out.println("successors");
		ConcurrentHashMap<String, List<String>> directSeqDict = this.logManager.getDirectSucEvents();
		if (directSeqDict != null && !directSeqDict.isEmpty()) {
			for (String e1 : directSeqDict.keySet()) {
				IntVar var1 = this.variables.get(e1);
				List<String> seqEvents = directSeqDict.get(e1);
				if (!seqEvents.isEmpty()) {
					BoolVar[] boolVarTemp = new BoolVar[seqEvents.size()];
					for (int k = 0; k < seqEvents.size(); k++) {
						IntVar sVar = this.variables.get(seqEvents.get(k));
						boolVarTemp[k] = model.boolVar(e1 + "->" + sVar.getName() + "--" + rCount);
						rCount++;
						model.arithm(var1, "=", sVar).reifyWith(boolVarTemp[k]);
					}
					int maxSuccs = this.logManager.getActSuccessorBoundaries(Integer.parseInt(e1.split("e")[1])).get(1);
					if (this.checkAllACycEvents(seqEvents)) {
						this.model.sum(boolVarTemp, "<=", maxSuccs);
					} else {
						if (maxSuccs > boolVarTemp.length)
							maxSuccs = boolVarTemp.length;
						this.model.sum(boolVarTemp, ">=", maxSuccs);
					}
				}
			}
		}
		return rCount;
	}

	/***
	 * create the predecessors constraints [types 3 and 4 ] and add them to the
	 * model
	 * 
	 * @param rCount
	 *            is the reify-counter for the sub-constraints
	 * @return rcount to represent the number of the incremented constraints
	 */
	private int addPredCstr(int rCount) {
		System.out.println("predecessors");
		ConcurrentHashMap<String, List<String>> directpredDict = this.logManager.getDirectPredEvents();
		if (directpredDict != null && !directpredDict.isEmpty()) {

			for (String e1 : directpredDict.keySet()) {
				IntVar var1 = this.variables.get(e1);
				List<String> predEvents = directpredDict.get(e1);
				BoolVar[] boolVarTemp = new BoolVar[predEvents.size()];
				if (!predEvents.isEmpty()) {
					for (int k = 0; k < predEvents.size(); k++) {
						IntVar pVar = this.variables.get(predEvents.get(k));
						boolVarTemp[k] = model.boolVar(e1 + "<-" + pVar.getName() + "--" + rCount);
						rCount++;
						model.arithm(var1, "=", pVar).reifyWith(boolVarTemp[k]);
					}
					int maxPred = this.logManager.getnActpredBoundaries(Integer.parseInt(e1.split("e")[1])).get(1);
					if (this.checkAllACycEvents(predEvents)) {
						this.model.sum(boolVarTemp, "<=", maxPred);
					} else {
						if (maxPred > boolVarTemp.length)
							maxPred = boolVarTemp.length;
						this.model.sum(boolVarTemp, ">=", maxPred);
					}
				}
			}
		}
		return rCount;
	}

	/***
	 * create the xor constraints [types 5 a.b, and c ] and add them to the
	 * model
	 * 
	 * @param rCount
	 *            is the reify-counter for the sub-constraints
	 * @return rcount to represent the number of the incremented constraints
	 */
	private int addLXorCstr(int rCount) {
		// XOR
		// System.out.println("Xor");
		//
		ConcurrentHashMap<String, List<String>> xorsDict = this.logManager.getXorEvents();
		if (xorsDict != null && !xorsDict.isEmpty()) {
			for (String e1 : xorsDict.keySet()) {
				IntVar var1 = this.variables.get(e1);
				List<String> xorEvents = xorsDict.get(e1);
				if (!xorEvents.isEmpty()) {
					BoolVar[] boolVarTemp = new BoolVar[xorEvents.size()];
					for (int k = 0; k < xorEvents.size(); k++) {
						IntVar xVar = this.variables.get(xorEvents.get(k));
						// take decision which case a or b or do nothing at all
						// check the successor of each of the events
						// two events are acyclic events
						if (this.logManager.getExactOneEventsFL().contains(xVar.getName())
								&& this.logManager.getExactOneEventsFL().contains(e1)) {
							// boolVarTemp[k] = model.boolVar(e1 + "#" +
							// xVar.getName() + "--" + rCount);
							// rCount++;
							model.arithm(var1, "!=", xVar).post();// .reifyWith(boolVarTemp[k]);
						} else {
							if (this.logManager.getDirectSucEvents().containsKey(e1)
									&& this.logManager.getDirectSucEvents().containsKey(xVar.getName())) {
								List<String> Svar1 = this.logManager.getDirectSucEvents().get(e1);
								Collections.sort(Svar1);
								List<String> Sxvar = this.logManager.getDirectSucEvents().get(xVar.getName());
								Collections.sort(Sxvar);
								if (Svar1.equals(Sxvar)) {
									boolVarTemp[k] = model.boolVar(e1 + "#" + xVar.getName() + "--" + rCount);
									rCount++;
									model.arithm(var1, "!=", xVar).post();
								} else {
									// Svar1 is subset of Sxvar as we compose
									// the
									// xor
									// events backward and the successor forward
									// so
									// V1
									// occurs after xVar; thus xVar should have
									// more
									// successor that V1 if both are not equal
									if (Collections.lastIndexOfSubList(Sxvar, Svar1) != -1) {
										List<String> endEs = this.logManager.getLoopRestartEndEvents(xVar.getName(),
												e1);
										if (endEs.size() >= 1) {
											BoolVar[] endTemp = new BoolVar[endEs.size() * 2];
											int q = 0;
											for (String e : endEs) {
												IntVar end = this.variables.get(e);
												endTemp[q] = model.boolVar(
														xVar.getName() + "#->" + end.getName() + "--" + rCount);
												rCount++;
												model.arithm(xVar, "=", end).reifyWith(endTemp[q]);
												q++;
												endTemp[q] = model.boolVar(
														var1.getName() + "<-#" + end.getName() + "--" + rCount);
												rCount++;
												model.arithm(var1, "=", end).reifyWith(endTemp[q]);
												q++;
											}
											model.ifThen(model.arithm(var1, "=", xVar), model.sum(endTemp, ">=", 2));
										}
									}

								}
							}
						}
					}

				}
			}
		}

		return rCount;
	}

	public int getNvar() {
		return this.model.getNbBoolVar();
	}

	public void printVariables() {
		System.out.println("------------------------------------------");
		for (IntVar var : this.model.retrieveIntVars(false)) {
			System.out.println(var.getName() + " : " + var.getValue() + " -  " + var.getDomainSize());
		}

		System.out.println("------------------------------------------");
	}

	public void printConstraints() {

		for (Constraint var : this.model.getCstrs()) {
			System.out.println(var.getName() + " -  " + var.toString());
		}

	}

	public void updateEventsCIds() {
		ConcurrentHashMap<String, Integer> vars = new ConcurrentHashMap<>(this.variables.size() + 10);
		for (IntVar var : this.variables.values()) {
			vars.put(var.getName(), var.getValue());
			// System.out.println(var.getName() + " - " + var.getValue());

		}
		this.logManager.updateEventsCIds(vars);

	}

	public void updateEventsCIds(Log updated) {
		ConcurrentHashMap<String, Integer> vars = new ConcurrentHashMap<>(this.variables.size() + 10);
		for (IntVar var : this.variables.values()) {
			vars.put(var.getName(), var.getValue());
			// System.out.println(var.getName() + " - " + var.getValue());

		}
		this.logManager.updateEventsCIds(updated, vars);

	}

	public void updateEventsCIds(Solution s, Log updated) {
		ConcurrentHashMap<String, Integer> vars = new ConcurrentHashMap<>(this.variables.size() + 10);
		for (IntVar var : this.variables.values()) {
			vars.put(var.getName(), s.getIntVal(var));
			// System.out.println(var.getName() + " - " + s.getIntVal(var));

		}
		this.logManager.updateEventsCIds(updated, vars);

	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}
}
