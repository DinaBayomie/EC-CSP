package correlation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.loop.monitors.IMonitorDownBranch;
import org.chocosolver.solver.search.loop.monitors.IMonitorOpenNode;
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.ActivityBased;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.StrategiesSequencer;
import org.chocosolver.solver.trace.IMessage;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;
import org.chocosolver.util.tools.StringUtils;

import preprocessing.Event;
import preprocessing.LogPreprocessingManager;
import static org.chocosolver.solver.search.strategy.Search.*;

public class ECmodelCreator {

	Model model = new Model("event correlation");
	BoolVar[] boolVars;
	LogPreprocessingManager logManager;
	ConcurrentHashMap<String, BoolVar> variables;
	public IntVar obj;

	public ECmodelCreator(LogPreprocessingManager logManager) {
		super();
		this.logManager = logManager;
		long start = System.currentTimeMillis();
		addModelVariables();
		// printVariables();
		addSumConstraints();
		addSturctureConstraints();
		// System.out.println("Print constraints using getcstr");
		// printConstraints();

		addObjectiveFunction();
		long end = System.currentTimeMillis();

		System.out.println(" build model in MS " + (end - start));
		System.out.println("nVar = " + this.getNvar());
		System.out.println("nVar events= " + this.variables.size());
		
		System.out.println("nConstraints = " + this.getModel().getNbCstrs());
	}

	public List<Solution> buildandRunModel(int nSol) {
		System.out.println("start the solver");
		List<IntVar> xx = new CopyOnWriteArrayList();
		int i = 0;
		// for (BoolVar boolVar : this.variables.values()) {
		this.variables.values().parallelStream().forEach(boolVar -> {
			if (boolVar.getDomainSize() == 2) {
				xx.add(boolVar);
			}
		});
		xx.add(obj);
		IntVar[] vars = new IntVar[xx.size()];
		for (IntVar boolVar : xx) {
			vars[i] = boolVar;
			i++;

		}
		Solver solver = model.getSolver();
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();
		long start = System.currentTimeMillis();
		// Solution solution =solver.findOptimalSolution(obj, Model.MINIMIZE);
		// solver.limitTime(120000);// 1hour
		int ns = 0;
		List<Solution> solution = new ArrayList<>();
		// solver.setSearch(activityBasedSearch(vars));
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();

		solver.setSearch(intVarSearch(vars),activityBasedSearch(vars),inputOrderUBSearch(vars));
//solver.setSearch(randomSearch(vars,));
		// solver.plugMonitor(new IMonitorDownBranch() {
		// @Override
		// public void beforeDownBranch(boolean left) {
		// // System.out.printf("%s %s ", StringUtils.pad("",
		// // solver.getEnvironment().getWorldIndex(), "."),
		// // solver.getDecisionPath().lastDecisionToString());
		// System.out.println(solver.getDecisionPath().toString());
		// // System.out.printf("message to out");
		// }
		// });
		// solver.plugMonitor(new IMonitorOpenNode() {
		// @Override
		// public void beforeOpenNode() {
		// // TODO Auto-generated method stub
		// IMonitorOpenNode.super.beforeOpenNode();
		// solver.showDecisions();
		// }
		// });
		solver.getModel().setObjective(Model.MINIMIZE, obj);
		// solver.addStopCriterion(stop);
		// Solution s = new Solution(ref().getModel());
		// while (ref().solve()) {
		// s.record();
		// }
		// ref().removeStopCriterion(stop);

		while (solver.solve() && ns < nSol) {

			// solution.add(solver.findOptimalSolution(obj, Model.MINIMIZE));
			Solution s = new Solution(this.model);
			s.record();
			System.out.println(s.toString());
			solution.add(s);
			ns++;
			System.out.println("ns");
		}
		// solution=solver.findAllSolutions(stop)
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solution;
	}

	public Solution buildandRunModel() {
		System.out.println("start the solver");
		List<IntVar> xx = new CopyOnWriteArrayList();
		int i = 0;
		// for (BoolVar boolVar : this.variables.values()) {
		this.variables.values().parallelStream().forEach(boolVar -> {
			if (boolVar.getDomainSize() == 2) {
				xx.add(boolVar);
			}
		});
		IntVar[] vars = new IntVar[xx.size()];
		for (IntVar boolVar : xx) {
			vars[i] = boolVar;
			i++;

		}
		Solver solver = model.getSolver();
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();

		solver.setSearch(activityBasedSearch(vars), minDomUBSearch(vars));

		conflictOrderingSearch(solver.getSearch());
		long start = System.currentTimeMillis();
		// Solution solution =solver.findOptimalSolution(obj, Model.MINIMIZE);
		// solver.limitTime(120000);// 1hour

		Solution solution = solver.findSolution();
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solution;
	}

	public Solution buildandRunModelOpt() {
		System.out.println("start the solver");
		IntVar[] vars = new IntVar[this.variables.size()];
		int i = 0;
		for (BoolVar boolVar : this.variables.values()) {
			vars[i] = boolVar;
			i++;
		}
		Solver solver = model.getSolver();
		solver.setLNS(INeighborFactory.random(vars));
		long start = System.currentTimeMillis();
		Solution solution = solver.findOptimalSolution(obj, Model.MINIMIZE);

		// Solution solution = solver.findSolution();
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solution;
	}

	public List<Solution> getAllSolution() {
		long start = System.currentTimeMillis();
		Solver solver = model.getSolver();
		IntVar[] vars = new IntVar[this.variables.size()];
		int i = 0;
		for (BoolVar boolVar : this.variables.values()) {
			vars[i] = boolVar;
			i++;
		}
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();

		solver.setSearch(activityBasedSearch(vars), minDomUBSearch(vars));

		conflictOrderingSearch(solver.getSearch());
		// try {
		// solver.propagate();
		// } catch (ContradictionException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		List<Solution> solutions = solver.findAllSolutions();// findAllOptimalSolutions(obj,
																// Model.MINIMIZE);//
		int nS = 0;
		// while (model.getSolver().solve() && nS<3) {
		// solutions.add(new Solution(model).record());
		// nS++;
		// }
		System.out.println(nS);
		solver.printStatistics();
		long end = System.currentTimeMillis();
		System.out.println(" get all solution in  MS" + (end - start));
		return solutions;
	}

	private void addObjectiveFunction() {
		// time diff as intVar
		System.out.println("adding objective condition");
		List<BoolVar> endVars = new CopyOnWriteArrayList<>();
		List<Integer> timeDiff = new CopyOnWriteArrayList<>();
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());
		startIndices.parallelStream().forEach(eS -> {
			AtomicInteger cId = new AtomicInteger(startIndices.indexOf(eS));
			List<Integer> endIndices = new CopyOnWriteArrayList<>(logManager.getEndEvents());

			endIndices.parallelStream().forEach(eE -> {
				int diff = this.logManager.timeDifference(eS, eE);
				int index = cId.get() + endIndices.indexOf(eE);
				if (this.variables.containsKey("c." + cId.get() + "_e" + eE)) {
					timeDiff.add(diff);
					endVars.add(this.variables.get("c." + cId.get() + "_e" + eE));
				}
			});
		});

		BoolVar[] endVarsA = new BoolVar[endVars.size()];
		int[] diffsA = new int[timeDiff.size()];
		for (int i = 0; i < endVars.size(); i++) {
			endVarsA[i] = endVars.get(i);
			diffsA[i] = timeDiff.get(i);
		}

		obj = this.model.intVar("Objective CT--H", 0, 10000, true);
		this.model.scalar(endVarsA, diffsA, "=", obj);
		// this.model.setObjective(Model.MINIMIZE, obj);
	}

	/***
	 * Add a boolean variable to the model per each event and case nCr with only
	 * the opened cases
	 */
	public void addModelVariables() {
		List<Event> events = new CopyOnWriteArrayList<>(logManager.getUnlabeledLog().getEvents());
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());

		variables = new ConcurrentHashMap<>(events.size() * startIndices.size());

		events.stream().forEach(e -> {
			int index = startIndices.indexOf(e.getId());

			// start event [ as fixed variables]
			if (index != -1) {
				BoolVar v = model.boolVar("c." + index + "_e" + e.getId(), true);
				variables.put("c." + index + "_e" + e.getId(), v);
			}
			// all other events
			else {
				AtomicInteger eIndex = new AtomicInteger(e.getId());
				int largestCId = Collections.binarySearch(startIndices, e.getId());
				int insertion_point = -(largestCId + 2);
				if (insertion_point > 0)
					IntStream.range(0, insertion_point + 1).sequential().forEach(cId -> {
						BoolVar v = model.boolVar("c." + cId + "_e" + eIndex.get());
						variables.put("c." + cId + "_e" + e.getId(), v);
					});
				else {
					BoolVar v = model.boolVar("c." + insertion_point + "_e" + eIndex.get(), true);
					variables.put("c." + insertion_point + "_e" + e.getId(), v);
				}
			}
		});
		boolVars = this.model.retrieveBoolVars();
	}

	/***
	 * This function at the constraints related to sum conditions, i.e.,
	 * assigning to 1 case only, handling xor relation, exact one occurrence of
	 * an activity within a case, at least one occurrence if there is a loop, at
	 * most one occurrence if there is an xor activities
	 */
	public void addSumConstraints() {
		System.out.println("Add sum constraints");
		// An event belongs to 1 case only
		List<BoolVar> handledVar = new ArrayList<>();
		for (int i = 0; i < boolVars.length; i++) {
			BoolVar var1 = boolVars[i];
			// System.out.println(var1.getName());
			List<BoolVar> temp = new ArrayList<>();
			if (!handledVar.contains(var1)) {
				for (int j = i + 1; j < boolVars.length; j++) {
					BoolVar var2 = boolVars[j];
					if (var1 != var2) {
						String e1 = var1.getName().split("_")[1];
						String e2 = var2.getName().split("_")[1];
						if (e1.equals(e2))
							temp.add(var2);
					}
					if (temp.size() == this.logManager.getNcases() - 1)
						break;
				}
				if (!temp.isEmpty()) {
					temp.add(var1);
					BoolVar[] boolVarTemp = new BoolVar[temp.size()];
					for (int k = 0; k < temp.size(); k++) {
						boolVarTemp[k] = temp.get(k);
					}
					this.model.sum(boolVarTemp, "=", 1).post();
					handledVar.addAll(temp);
				}
				// else {
				// this.model.arithm(var1, "=", 1).post();
				// }
			}
		}

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

	public void addSturctureConstraints() {
		System.out.println("successors");
		int reifyVarCounter = 0;
		// add successors
		ConcurrentHashMap<String, List<String>> directSeq = this.logManager.getDirectSucEvents();
		if (directSeq != null && !directSeq.isEmpty()) {
			for (int i = 0; i < boolVars.length; i++) {
				BoolVar var1 = boolVars[i];
				// System.out.println(var1.getName());
				String e1 = var1.getName().split("_")[1];
				String cId1 = var1.getName().split("_")[0];

				if (directSeq.containsKey(e1)) {

					List<String> seqEvents = directSeq.get(e1);
					List<BoolVar> temp = new ArrayList<>();
					for (String e2 : seqEvents) {
						String varName = cId1 + "_" + e2;
						if (variables.containsKey(varName))
							temp.add(this.variables.get(varName));

					}
					if (!temp.isEmpty()) {
						BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						for (int k = 0; k < temp.size(); k++) {
							BoolVar[] bool_ = new BoolVar[2];
							boolVarTemp[k] = model.boolVar(temp.get(k).getName() + "ORCont_" + reifyVarCounter);
							reifyVarCounter++;
							// System.out.println(var1.toString() + "=" +
							// temp.get(k).toString());
							// model.arithm(var1, "=",
							// temp.get(k)).reifyWith(boolVarTemp[k]);
							bool_[0] = var1;
							bool_[1] = temp.get(k);
							model.allEqual(bool_).reifyWith(boolVarTemp[k]);

						}
						// System.out.println("Or the above condition");

						// this.model.sum(boolVarTemp, ">=",0).post();
						// var1.getValue()).reify();
						this.model.or(boolVarTemp).post();
						// IntVar sum = model.intVar(0, boolVarTemp.length,
						// true);
						// model.sum(boolVarTemp, "=", sum).post();
						// model.arithm(sum, ">=", 0);
					}
				}
			}
		}

		// add predecessors
		System.out.println("pred");
		ConcurrentHashMap<String, List<String>> directpred = this.logManager.getDirectPredEvents();
		if (directpred != null && !directpred.isEmpty()) {
			for (int i = 0; i < boolVars.length; i++) {
				BoolVar var1 = boolVars[i];
				// System.out.println(var1.getName());
				String[] arrs = var1.getName().split("_");
				String e1 = arrs[1];
				String cId1 = arrs[0];
				int e1Index = Integer.parseInt(e1.split("e")[1]);
				if (directpred.containsKey(e1)) {

					List<String> predEvents = directpred.get(e1);
					BoolVar[] boolVarTemp = new BoolVar[predEvents.size()];
					List<BoolVar> boolVarTempL = new ArrayList<>();
					// int pIndex = 0;
					// boolean isConstrainted = false;
					List<BoolVar> temp2 = new ArrayList<>();
					for (String e2 : predEvents) {
						String varName = cId1 + "_" + e2;
						if (variables.containsKey(varName))
							temp2.add(this.variables.get(varName));
					}
					if (!temp2.isEmpty()) {
						// if (temp2.size() > 1) {
						// BoolVar[] btempIn = new BoolVar[temp2.size()];
						for (int k = 0; k < temp2.size(); k++) {
							//
							// BoolVar[] bool_ = new BoolVar[2];
							// btempIn[k] = model.boolVar(
							// var1.getName() + "_" + temp2.get(k).getName() +
							// "AndCont_" + reifyVarCounter);
							// reifyVarCounter++;
							// bool_[0] = var1;
							// bool_[1] = temp2.get(k);
							// model.allEqual(bool_).reifyWith(btempIn[k]);
							// }
							// boolVarTempL.add(model.and(btempIn).reify());
							// } else {
							BoolVar[] bool_ = new BoolVar[2];
							BoolVar x = model.boolVar(
									var1.getName() + "_" + temp2.get(k).getName() + "PCont_" + reifyVarCounter);
							reifyVarCounter++;
							bool_[0] = var1;
							bool_[1] = temp2.get(k);
							model.allEqual(bool_).reifyWith(x);
							boolVarTempL.add(x);
						}
					}
					// pIndex++;
					if (boolVarTempL.size() > 0) {
						boolVarTemp = new BoolVar[boolVarTempL.size()];
						for (int k = 0; k < boolVarTempL.size(); k++) {
							boolVarTemp[k] = boolVarTempL.get(k);
						}
						 this.model.or(boolVarTemp).post();
//						List<Integer> bound = this.logManager.getnActpredBoundaries(e1Index);
//						if (bound.get(0) != bound.get(1)) {
//							this.model.sum(boolVarTemp, ">=", bound.get(0)).post();
//							this.model.sum(boolVarTemp, "<", bound.get(1) + 1).post();
//						} else {
//							this.model.sum(boolVarTemp, "<=", bound.get(0)).post();// as
//
//						} // it
							// could
							// be
							// 0

					}
				}
			}
		}

		// XOR
		System.out.println("Xor");

		ConcurrentHashMap<String, List<String>> xors = this.logManager.getXorEvents();
		List<BoolVar> xorEvents_ = new CopyOnWriteArrayList<>();
		if (xors != null && !xors.isEmpty()) {
			for (int i = boolVars.length - 1; i >= 0; i--) {
				BoolVar var1 = boolVars[i];
				// System.out.println(var1.getName());
				String e1 = var1.getName().split("_")[1];
				String cId1 = var1.getName().split("_")[0];

				if (xors.containsKey(e1)) {

					List<String> xorEvents = xors.get(e1);
					List<BoolVar> temp = new ArrayList<>();
					for (String e2 : xorEvents) {
						String varName = cId1 + "_" + e2;
						if (variables.containsKey(varName))
							temp.add(this.variables.get(varName));
					}
					if (!temp.isEmpty() && !xorEvents_.containsAll(temp)) {
						xorEvents_.add(var1);
						temp.add(var1);
						BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						for (int k = 0; k < temp.size(); k++) {
							boolVarTemp[k] = temp.get(k);
							xorEvents_.add(temp.get(k));
							// System.out.print(boolVarTemp[k].toString());

						}
						// System.out.println("sum to one");

						this.model.sum(boolVarTemp, "<=", 1).post();
					}
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

	public int getNvar() {
		return this.model.getNbBoolVar();
	}

	public void printVariables() {

		for (BoolVar var : this.model.retrieveBoolVars()) {
			System.out.println(var.getName() + " -  " + var.getDomainSize());
		}

	}

	public void printConstraints() {

		for (Constraint var : this.model.getCstrs()) {
			System.out.println(var.getName() + " -  " + var.toString());
		}

	}

	public void updateEventsCIds() {
		List<String> vars = new CopyOnWriteArrayList<>();
		for (BoolVar var : this.variables.values()) {
			if (var.getValue() == 1)
				vars.add(var.getName());
		}
		this.logManager.updateEventsCIds(vars);

	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}
}
