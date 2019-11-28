package correlation;

import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.ParallelPortfolio;
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
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.util.tools.StringUtils;

import preprocessing.Event;
import preprocessing.LogPreprocessingManager;
import static org.chocosolver.solver.search.strategy.Search.*;

public class ECmodelCreatorIntPortfolio {

	Model model = new Model("event correlation");
	IntVar[] eventVars;
	LogPreprocessingManager logManager;
	ConcurrentHashMap<String, IntVar> variables;
	public IntVar obj;

	public ECmodelCreatorIntPortfolio(LogPreprocessingManager logManager) {
		super();
		this.logManager = logManager;
		long start = System.currentTimeMillis();
		addModelVariables();
		// printVariables();
		// addSumConstraints();
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
		List<IntVar> intvar2domain = new CopyOnWriteArrayList();
		List<IntVar> intvar1domain = new CopyOnWriteArrayList();
		IntVar[] varsAll = new IntVar[this.variables.size()];

		int i = 0;
		this.variables.values().parallelStream().forEach(boolVar -> {
			// if (boolVar.getDomainSize() ==this.logManager.getNcases()) {
			if (!boolVar.isAConstant()) {
				intvar2domain.add(boolVar);

			}
		});
		i = 0;
		intvar2domain.add(obj);
		IntVar[] vars = new IntVar[intvar2domain.size()];
		for (IntVar boolVar : intvar2domain) {
			vars[i] = boolVar;
			i++;
		}

		long start = System.currentTimeMillis();
		Solver solver = model.getSolver();
		int ns = 0;
		List<Solution> solutions = new ArrayList<>();
		solver.setLNS(INeighborFactory.random(vars));
		solver.setNoGoodRecordingFromRestarts();

		solver.setSearch(activityBasedSearch(vars), intVarSearch(vars), inputOrderLBSearch(vars));
		conflictOrderingSearch(solver.getSearch());

		while (solver.solve() && ns < nSol) {

			// solution.add(solver.findOptimalSolution(obj, Model.MINIMIZE));
			Solution s = new Solution(this.model, vars);
			s.record();
			// System.out.println(s.toString());
			solutions.add(s);
			ns++;
			System.out.println("ns");
		}

		// solution=solver.findAllOptimalSolutions(obj, Model.MINIMIZE);
		solver.printStatistics();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solutions;
	}
	
	public void buildModelUsingportfolio(int nSol){
		ParallelPortfolio portfolio = new ParallelPortfolio();
       
		
	}
	
	

	private void addObjectiveFunction() {
		// time diff as intVar
		System.out.println("adding objective condition");
		List<BoolVar> boolEndConstVars = new CopyOnWriteArrayList<>();
		List<Integer> timeDiff = new CopyOnWriteArrayList<>();
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());
		AtomicInteger reifyVarCounter = new AtomicInteger(0);
		startIndices.stream().forEach(eS -> {
			AtomicInteger cId = new AtomicInteger(startIndices.indexOf(eS));
			List<Integer> endIndices = new CopyOnWriteArrayList<>(logManager.getEndEvents());

			endIndices.stream().forEach(eE -> {
				int diff = this.logManager.timeDifference(eS, eE);
				timeDiff.add(diff);
				BoolVar objC = model.boolVar(eS + "-" + eE + "objCont_");
				model.arithm(variables.get("e" + eS), "=", variables.get("e" + eE)).reifyWith(objC);
				boolEndConstVars.add(objC);
			});
		});

		BoolVar[] endVarsA = new BoolVar[boolEndConstVars.size()];
		int[] diffsA = new int[timeDiff.size()];
		for (int i = 0; i < boolEndConstVars.size(); i++) {
			endVarsA[i] = boolEndConstVars.get(i);
			diffsA[i] = timeDiff.get(i);
		}

		obj = this.model.intVar("Objective CT--H", 0, 10000, true);
		this.model.scalar(endVarsA, diffsA, "=", obj);
		this.model.setObjective(Model.MINIMIZE, obj);
	}

	/***
	 * Add a boolean variable to the model per each event and case nCr with only
	 * the opened cases
	 */
	public void addModelVariables() {
		List<Event> events = new CopyOnWriteArrayList<>(logManager.getUnlabeledLog().getEvents());
		List<Integer> startIndices = new CopyOnWriteArrayList<>(logManager.getStartEvents());

		variables = new ConcurrentHashMap<>(events.size());

		events.stream().forEach(e -> {
			int index = startIndices.indexOf(e.getId());

			// start event [ as fixed variables]
			if (index != -1) {
				IntVar v = model.intVar("e" + e.getId(), index);
				variables.put(v.getName(), v);
			}
			// all other events
			else {
				AtomicInteger eIndex = new AtomicInteger(e.getId());
				int largestCId = Collections.binarySearch(startIndices, e.getId());
				int insertion_point = -(largestCId + 2);
				if (insertion_point > 0)
				// IntStream.range(0, insertion_point +
				// 1).sequential().forEach(cId -> {
				// BoolVar v = model.boolVar("c." + cId + "_e" + eIndex.get());
				// variables.put("c." + cId + "_e" + e.getId(), v);
				// });
				{
					IntVar v = model.intVar("e" + e.getId(), 0, insertion_point);
					variables.put(v.getName(), v);

				} else {
					IntVar v = model.intVar("e" + e.getId(), insertion_point);
					variables.put(v.getName(), v);
				}
			}
		});
		eventVars = this.model.retrieveIntVars(false);
	}

	public void addSturctureConstraints() {
		int reifyVarCounter = 0;
		List<IntVar> eventVars = new CopyOnWriteArrayList<>(this.variables.values());
		// XOR
		System.out.println("Xor");

		ConcurrentHashMap<String, List<String>> xorsDict = this.logManager.getXorEvents();
		List<IntVar> xorEvents_ = new CopyOnWriteArrayList<>();
		if (xorsDict != null && !xorsDict.isEmpty()) {
			for (int i = 0; i < eventVars.size(); i++) {
				IntVar var1 = eventVars.get(i);
				String e1 = var1.getName();
				if (xorsDict.containsKey(e1)) {

					List<String> xorEvents = xorsDict.get(e1);
					List<IntVar> temp = new ArrayList<>();
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
						Constraint c = this.model.or(boolVarTemp);// .post();
						c.post();
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
					this.model.or(boolVarTemp).post();
					// List<Integer> bound =
					// this.logManager.getnActpredBoundaries(e1Index);
					// if (bound.get(0) != bound.get(1)) {
					// this.model.sum(boolVarTemp, ">=",
					// bound.get(0)).post();
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
		ConcurrentHashMap<String, Integer> vars = new ConcurrentHashMap<>(this.variables.size() + 10);
		for (IntVar var : this.variables.values()) {
			vars.put(var.getName(), var.getValue());
			// System.out.println(var.getName() + " - " + var.getValue());

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
