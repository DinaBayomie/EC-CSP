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
import org.chocosolver.solver.variables.BoolVar;

import preprocessing.Event;
import preprocessing.LogPreprocessingManager;

public class ECmodelCreator2 {

	Model model = new Model("event correlation");
	BoolVar[] boolVars;
	LogPreprocessingManager logManager;
	ConcurrentHashMap<String, BoolVar> variables;

	public ECmodelCreator2(LogPreprocessingManager logManager) {
		super();
		this.logManager = logManager;
		long start = System.currentTimeMillis();
		addModelVariables();
		addSumConstraints();
		addSturctureConstraints();
		long end = System.currentTimeMillis();

		System.out.println(" build model in MS " + (end - start));
		System.out.println("nVar = " + this.getNvar());
		System.out.println("nConstraints = " + this.getModel().getNbCstrs());
	}

	public Solution buildandRunModel() {
		Solver solver = model.getSolver();
		long start = System.currentTimeMillis();
		Solution solution = solver.findSolution();

		long end = System.currentTimeMillis();
		System.out.println("get solution in " + (end - start));
		return solution;
	}

	public List<Solution> getAllSolution() {
		long start = System.currentTimeMillis();
		Solver solver = model.getSolver();
		List<Solution> solutions = solver.findAllSolutions();
		long end = System.currentTimeMillis();
		System.out.println(" get all solution in  MS" + (end - start));
		return solutions;
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
				int insertion_point = -(largestCId + 1);
				IntStream.range(0, insertion_point).sequential().forEach(cId -> {
					BoolVar v = model.boolVar("c." + cId + "_e" + eIndex.get());
					variables.put("c." + cId + "_e" + e.getId(), v);
				});
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
		// An event belongs to 1 case only

		for (int i = 0; i < boolVars.length - 1; i++) {
			BoolVar var1 = boolVars[i];
			List<BoolVar> temp = new ArrayList<>();
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
			} else {
				this.model.arithm(var1, "=", 1).post();
			}
		}

		// Xor constraints
		// ConcurrentHashMap<String, List<String>> xors =
		// this.logManager.getXorEvents();
		// if (xors != null && !xors.isEmpty()) {
		// for (int i = 0; i < boolVars.length - 1; i++) {
		// BoolVar var1 = boolVars[i];
		// List<BoolVar> temp = new ArrayList<>();
		// for (int j = i + 1; j < boolVars.length; j++) {
		// BoolVar var2 = boolVars[j];
		// String cId1 = var1.getName().split("_")[0];
		// String cId2 = var2.getName().split("_")[0];
		// if (cId1.equals(cId2)) {
		// String e1 = var1.getName().split("_")[1];
		// String e2 = var2.getName().split("_")[1];
		// if ((xors.containsKey(e1) && xors.get(e1).contains(e2)))
		// temp.add(var2);
		// if (xors.containsKey(e1) && temp.size() == xors.get(e1).size())
		// break;
		// }
		//
		// }
		// if (!temp.isEmpty()) {
		// temp.add(var1);
		// // BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
		// BoolVar[] boolVarTemp = new BoolVar[temp.size()];
		// for (int k = 0; k < temp.size(); k++) {
		// boolVarTemp[k] = temp.get(k);
		// }
		// this.model.sum(boolVarTemp, "=", 1).reify();
		// }
		// }
		// }

		// exact occurrence of an activity
		ConcurrentHashMap<String, List<String>> exactOne = this.logManager.getExactOneEvents();
		if (exactOne != null && !exactOne.isEmpty()) {
			for (String act : exactOne.keySet()) {
				List<String> events = exactOne.get(act);
				for (int i = 0; i < this.logManager.getNcases(); i++) {
					List<BoolVar> temp = new ArrayList<>();
					for (String e : events) {
						String varName = "c." + i + "_" + e;
						if (variables.containsKey(varName))
							temp.add(this.variables.get(varName));
					}
					if (!temp.isEmpty()) {
						// BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
						BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						for (int k = 0; k < temp.size(); k++) {
							boolVarTemp[k] = temp.get(k);
						}
						this.model.sum(boolVarTemp, "=", 1).post();
					}
				}
			}
		}

		// AtLeast once
		ConcurrentHashMap<String, List<String>> atleastOnce = this.logManager.getAtLeastOneEvents();
		if (atleastOnce != null && !atleastOnce.isEmpty()) {
			for (String act : atleastOnce.keySet()) {
				List<String> events = atleastOnce.get(act);
				for (int i = 0; i < this.logManager.getNcases(); i++) {
					List<BoolVar> temp = new ArrayList<>();
					for (String e : events) {
						String varName = "c." + i + "_" + e;
						if (variables.containsKey(varName))
							temp.add(this.variables.get(varName));
					}
					if (!temp.isEmpty()) {
						// BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
						BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						for (int k = 0; k < temp.size(); k++) {
							boolVarTemp[k] = temp.get(k);
						}
						this.model.sum(boolVarTemp, ">=", 1).post();
					}
				}
			}
		}

		// At most once [ to represent the occurrence of the xor in a case
		ConcurrentHashMap<String, List<String>> atmostOnce = this.logManager.getAtMostOneEvents();
		if (atmostOnce != null && !atmostOnce.isEmpty()) {
			for (String act : atmostOnce.keySet()) {
				List<String> events = atmostOnce.get(act);
				for (int i = 0; i < this.logManager.getNcases(); i++) {
					List<BoolVar> temp = new ArrayList<>();
					for (String e : events) {
						String varName = "c." + i + "_" + e;
						if (variables.containsKey(varName))
							temp.add(this.variables.get(varName));
					}
					if (!temp.isEmpty()) {
						// BoolVar[] boolVarTemp = (BoolVar[]) temp.toArray();
						BoolVar[] boolVarTemp = new BoolVar[temp.size()];
						for (int k = 0; k < temp.size(); k++) {
							boolVarTemp[k] = temp.get(k);
						}
						this.model.sum(boolVarTemp, "<=", 1).post();
					}
				}
			}
		}
	}

	public void addSturctureConstraints() {

		// add successors
		ConcurrentHashMap<String, List<String>> directSeq = this.logManager.getDirectSucEvents();
		if (directSeq != null && !directSeq.isEmpty()) {
			for (int i = 0; i < boolVars.length - 1; i++) {
				BoolVar var1 = boolVars[i];
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
							boolVarTemp[k] = model.arithm(var1, "=", temp.get(k)).reify();
							;
						}
						// this.model.sum(boolVarTemp, ">=",
						// var1.getValue()).post();
						this.model.or(boolVarTemp).post();
					}
				}
			}
		}

//		// add predecessors
//		System.out.println("pred");
//		ConcurrentHashMap<String, List<List<String>>> directpred = this.logManager.getDirectPredEvents();
//		if (directpred != null && !directpred.isEmpty()) {
//			for (int i = 0; i < boolVars.length; i++) {
//				BoolVar var1 = boolVars[i];
//				// System.out.println(var1.getName());
//				String e1 = var1.getName().split("_")[1];
//				String cId1 = var1.getName().split("_")[0];
//
//				if (directpred.containsKey(e1)) {
//
//					List<List<String>> predEvents = directpred.get(e1);
//					BoolVar[] boolVarTemp = new BoolVar[predEvents.size()];
//					List<BoolVar> boolVarTempL = new ArrayList<>();
//					int pIndex = 0;
//					boolean isConstrainted = false;
//					for (List<String> plist : predEvents) {
//						List<BoolVar> temp2 = new ArrayList<>();
//						for (String e2 : plist) {
//							String varName = cId1 + "_" + e2;
//							if (variables.containsKey(varName))
//								temp2.add(this.variables.get(varName));
//						}
//						if (!temp2.isEmpty()) {
//							if (temp2.size() > 1) {
//								BoolVar[] btempIn = new BoolVar[temp2.size()];
//								for (int k = 0; k < temp2.size(); k++) {
//
//									BoolVar[] bool_ = new BoolVar[2];
//									btempIn[k] = model.boolVar(var1.getName() + "_" + temp2.get(k).getName()
//											+ "AndCont_" + reifyVarCounter);
//									reifyVarCounter++;
//									// System.out.println(var1.toString() + "="
//									// + temp2.get(k).toString());
//									// model.arithm(var1, "=",
//									// temp.get(k)).reifyWith(boolVarTemp[k]);
//									bool_[0] = var1;
//									bool_[1] = temp2.get(k);
//									model.allEqual(bool_).reifyWith(btempIn[k]);
//
//									// btempIn[k] = model.arithm(var1, "=",
//									// temp2.get(k)).reify();
//								}
//								// if (temp2.size() > 1) {
//								// boolVarTemp[pIndex] =
//								// model.and(btempIn).reify();
//								// isConstrainted = true;
//								boolVarTempL.add(model.and(btempIn).reify());
//								// }
//							} else {
//								// boolVarTemp[pIndex] = model.arithm(var1, "=",
//								// temp2.get(0)).reify();
//								// isConstrainted = true;
//								// boolVarTempL.add(model.arithm(var1, "=",
//								// temp2.get(0)).reify());
//
//								BoolVar[] bool_ = new BoolVar[2];
//								BoolVar x = model.boolVar(
//										var1.getName() + "_" + temp2.get(0).getName() + "PCont_" + reifyVarCounter);
//								reifyVarCounter++;
//								// System.out.println(var1.toString() + "=" +
//								// temp2.get(0).toString());
//								// model.arithm(var1, "=",
//								// temp.get(k)).reifyWith(boolVarTemp[k]);
//								bool_[0] = var1;
//								bool_[1] = temp2.get(0);
//								model.allEqual(bool_).reifyWith(x);
//
//							}
//						}
//						pIndex++;
//					}
//					if (boolVarTempL.size() > 0) {
//						boolVarTemp = new BoolVar[boolVarTempL.size()];
//						for (int k = 0; k < boolVarTempL.size(); k++) {
//							boolVarTemp[k] = boolVarTempL.get(k);
//						}
//						this.model.or(boolVarTemp).post();
//					}
//				}
//			}
//		}
//

//		ConcurrentHashMap<String, List<List<String>>> directpred = this.logManager.getDirectPredEvents();
//		if (directpred != null && !directpred.isEmpty()) {
//			for (int i = 0; i < boolVars.length - 1; i++) {
//				BoolVar var1 = boolVars[i];
//				String e1 = var1.getName().split("_")[1];
//				String cId1 = var1.getName().split("_")[0];
//
//				if (directpred.containsKey(e1)) {
//					List<List<String>> predEvents = directpred.get(e1);
//					BoolVar[] boolVarTemp = new BoolVar[predEvents.size()];
//					List<BoolVar> boolVarTempL = new ArrayList<>();
//					int pIndex = 0;
//					boolean isConstrainted = false;
//					for (List<String> plist : predEvents) {
//						List<BoolVar> temp2 = new ArrayList<>();
//						for (String e2 : plist) {
//							String varName = cId1 + "_" + e2;
//							if (variables.containsKey(varName))
//								temp2.add(this.variables.get(varName));
//						}
//						if (!temp2.isEmpty()) {
//							if (temp2.size() > 1) {
//								BoolVar[] btempIn = new BoolVar[temp2.size()];
//								for (int k = 0; k < temp2.size(); k++) {
//									btempIn[k] = model.arithm(var1, "=", temp2.get(k)).reify();
//								}
//								if (temp2.size() > 0) {
//									// boolVarTemp[pIndex] =
//									// model.and(btempIn).reify();
//									// isConstrainted = true;
//									boolVarTempL.add(model.and(btempIn).reify());
//								}
//							} else {
//								// boolVarTemp[pIndex] = model.arithm(var1, "=",
//								// temp2.get(0)).reify();
//								// isConstrainted = true;
//								boolVarTempL.add(model.arithm(var1, "=", temp2.get(0)).reify());
//							}
//						}
//						pIndex++;
//					}
//					if (boolVarTempL.size() > 0) {
//						boolVarTemp =   new BoolVar[boolVarTempL.size()];
//						for (int k = 0; k < boolVarTempL.size(); k++) {
//							boolVarTemp[k] = boolVarTempL.get(k);
//						}				
//						this.model.or(boolVarTemp).post();
//					}
//				}
//			}
//		}

		ConcurrentHashMap<String, List<String>> xors = this.logManager.getXorEvents();
		List<BoolVar> xorEvents_ = new CopyOnWriteArrayList<>();
		if (xors != null && !xors.isEmpty()) {
			for (int i = boolVars.length - 1; i >= 0; i--) {
				BoolVar var1 = boolVars[i];
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
						}
						this.model.sum(boolVarTemp, "=", 1).post();
					}
				}
			}
		}

	}

	public int getNvar() {
		return this.model.getNbBoolVar();
	}

	public void printVariables() {

		for (BoolVar var : this.model.retrieveBoolVars()) {
			System.out.println(var.getName() + " -  " + var.getDomainSize());
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
