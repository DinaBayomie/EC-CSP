package evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import preprocessing.CaseInstance;
import preprocessing.Event;
import preprocessing.Log;

public class EvaluationManager {

	Log originalLog;
	Log generatedLog;
	Map<String, List<Event>> originalCases;
	Map<String, List<Event>> generatedCases;
	ConcurrentHashMap<ArrayList<String>, Integer> Originaltraces;
	ConcurrentHashMap<ArrayList<String>, Integer> generatedtraces;
	ConcurrentHashMap<List<String>, List<Integer>> OriginaltracesMinDistance = new ConcurrentHashMap<>();
	ConcurrentHashMap<List<String>, ConcurrentHashMap<List<String>, Integer>> origGenTraceDistance = new ConcurrentHashMap<>();
	List<TraceDistance> origGenTraceDistanceList = new CopyOnWriteArrayList<TraceDistance>();
	List<CaseInstance> origCases;
	List<CaseInstance> genCases;
	int nCases;

	public EvaluationManager(Log originalLog, Log generatedLog) {
		super();
		this.originalLog = originalLog;
		this.generatedLog = generatedLog;
		originalCases = originalLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		origCases = formCaseList(originalCases);
		generatedCases = generatedLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		genCases = formCaseList(generatedCases);
		Originaltraces = new ConcurrentHashMap<>(originalLog.getLogTraces());
		generatedtraces = new ConcurrentHashMap<>(generatedLog.getLogTraces());
		nCases = generatedCases.size();
	}

	private List<CaseInstance> formCaseList(Map<String, List<Event>> casesInsMap) {
		List<CaseInstance> casesIns = new CopyOnWriteArrayList<>();
		for (Entry<String, List<Event>> caseInstance : casesInsMap.entrySet()) {
//			if (!caseInstance.getKey().equals("0"))
				casesIns.add(new CaseInstance(caseInstance.getValue(), caseInstance.getKey()));
		}
		return casesIns;
	}

	public EvaluationManager(Log originalLog) {
		super();
		this.originalLog = originalLog;
		originalCases = originalLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		this.originalLog.setLabeled(true);
		Originaltraces = new ConcurrentHashMap<>(originalLog.getLogTraces());
	}

	public EvaluationManager(String originalLogFilePath, String generatedLogFilePath) {
		super();
		this.originalLog = new Log(originalLogFilePath, true);
		this.generatedLog = new Log(generatedLogFilePath, true);

		originalCases = originalLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		generatedCases = generatedLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));

		this.originalLog.setLabeled(true);
		this.generatedLog.setLabeled(true);
		Originaltraces = new ConcurrentHashMap<>(originalLog.getLogTraces());
		generatedtraces = new ConcurrentHashMap<>(generatedLog.getLogTraces());
		nCases = generatedCases.size();
	}

	public void initObjects(Log generatedLog) {
		this.generatedLog = generatedLog;
		generatedCases = generatedLog.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		this.generatedLog.setLabeled(true);
		generatedtraces = new ConcurrentHashMap<>(generatedLog.getLogTraces());
		nCases = generatedCases.size();

	}

	/*** Time computation **/
	// latest version of time variance metric Check this again
	public Hashtable<String, Double> computeSMAPE(StringBuilder consoleText) {

		double ctSMAPE = 0;
		double etSMAPE = 0;
		double n = 0;
		// double count = 0;
		// double avgMAD = 0;

		for (String cId : originalCases.keySet()) {
			// double cMAD = 0;
			List<Event> originalCase = this.originalCases.get(cId);
			Event oEstart = originalCase.get(0);
			Event oEend = originalCase.get(originalCase.size() - 1);
			double actualFT = (oEend.getTimestampD().getTime() / 3600000)
					- (oEstart.getTimestampD().getTime() / 3600000); // in hours
			int seoIndex = this.originalLog.getEIndex(oEstart);
			Event gEstart = generatedLog.findEventByIndex(oEstart, seoIndex);
			if (gEstart == null)
				continue;
			List<Event> generatedCase = generatedCases.get(gEstart.getCaseId());
			Event gEend = generatedCase.get(generatedCase.size() - 1);
			double predicatedFT = (gEend.getTimestampD().getTime() / 3600000)
					- (gEstart.getTimestampD().getTime() / 3600000);

			double diff = Math.abs(actualFT - predicatedFT);
			double sum = Math.abs(actualFT) + Math.abs(predicatedFT);
			double frac = 0;
			if (sum != 0) {
				frac = diff / sum;
				ctSMAPE += frac;
			}
			for (int oIndex = 1; oIndex < originalCase.size(); oIndex++) {
				Event oE = originalCase.get(oIndex);
				Event oEp = originalCase.get(oIndex - 1);
				actualFT = (oE.getTimestampD().getTime() / 3600000) - (oEp.getTimestampD().getTime() / 3600000); // in
																													// hours
				int oeIndex = this.originalLog.getEIndex(oE);
				Event gE = generatedLog.findEventByIndex(oE, oeIndex);
				if (gE == null)
					continue;
				generatedCase = generatedCases.get(gEstart.getCaseId());
				int gIndex = generatedCase.lastIndexOf(gE);
				if (gIndex <= 0)
					continue;
				Event gEp = generatedCase.get(gIndex - 1);
				predicatedFT = (gE.getTimestampD().getTime() / 3600000) - (gEp.getTimestampD().getTime() / 3600000); // in
																														// hours

				diff = Math.abs(actualFT - predicatedFT);
				sum = Math.abs(actualFT) + Math.abs(predicatedFT);
				if (sum != 0) {
					frac = diff / sum;
					etSMAPE += frac;
				}
			}

		}

		ctSMAPE = ctSMAPE / (double) (originalCases.size());
		int nEvents = (this.originalLog.getnEvents() - this.originalCases.size());
		etSMAPE = etSMAPE / (double) (nEvents);
		Hashtable<String, Double> results = new Hashtable<>();
		results.put("cyclic time SMAPE", ctSMAPE);
		results.put("Event time SMAPE", etSMAPE);
		System.out.println("cyclic time SMAPE" + ctSMAPE);
		System.out.println("Event time SMAPE" + etSMAPE);
		consoleText.append("cyclic time SMAPE" + ctSMAPE + ";");
		consoleText.append("Event time SMAPE" + etSMAPE + ";");

		return results;

	}

	// event time SMAPE
	// public Double computeETSMAPE(StringBuilder consoleText) {
	Callable<Double> computeETSMAPE = () -> {

		// double ctSMAPE = 0;
		double etSMAPE = 0;
		double n = 0;
		// double count = 0;
		// double avgMAD = 0;
		double diff = 0;
		double predicatedFT = 0;
		double actualFT = 0;
		double sum = 0;
		double frac = 0;
		for (String cId : originalCases.keySet()) {
			// double cMAD = 0;
			List<Event> originalCase = this.originalCases.get(cId);
			Event oEstart = originalCase.get(0);
			Event oEend = originalCase.get(originalCase.size() - 1);
			int seoIndex = this.originalLog.getEIndex(oEstart);
			Event gEstart = generatedLog.findEventByIndex(oEstart, seoIndex);
			if (gEstart == null)
				continue;
			List<Event> generatedCase = generatedCases.get(gEstart.getCaseId());
			for (int oIndex = 1; oIndex < originalCase.size(); oIndex++) {
				Event oE = originalCase.get(oIndex);
				Event oEp = originalCase.get(oIndex - 1);
				actualFT = (oE.getTime() - oEp.getTime()) / 3600000.0;
				// (oE.getTimestampD().getTime() / 3600000) -
				// (oEp.getTimestampD().getTime() / 3600000); // in hours
				int oeIndex = this.originalLog.getEIndex(oE);
				Event gE = generatedLog.findEventByIndex(oE, oeIndex);
				if (gE == null)
					continue;
				generatedCase = generatedCases.get(gEstart.getCaseId());
				int gIndex = generatedCase.lastIndexOf(gE);
				if (gIndex <= 0)
					continue;
				Event gEp = generatedCase.get(gIndex - 1);
				predicatedFT = (gE.getTime() - gEp.getTime()) / 3600000.0;
				// predicatedFT = (gE.getTimestampD().getTime() / 3600000) -
				// (gEp.getTimestampD().getTime() / 3600000); // in hours

				diff = Math.abs(actualFT - predicatedFT);
				sum = Math.abs(actualFT) + Math.abs(predicatedFT);
				if (sum != 0) {
					frac = diff / (double) sum;
					etSMAPE += frac;
					n++;
				}
			}

		}

		etSMAPE = etSMAPE / (this.generatedLog.getnEvents() - this.generatedLog.getnCases());

		return etSMAPE;

	};
	Callable<Double> computeETMAD = () -> {

		// double ctSMAPE = 0;
		double MAD = 0;
		double n = 0;
		// double count = 0;
		// double avgMAD = 0;
		double diff = 0;
		double predicatedFT = 0;
		double actualFT = 0;
		double sum = 0;
		double frac = 0;
		for (String cId : originalCases.keySet()) {
			// double cMAD = 0;
			List<Event> originalCase = this.originalCases.get(cId);
			Event oEstart = originalCase.get(0);
			Event oEend = originalCase.get(originalCase.size() - 1);
			int seoIndex = this.originalLog.getEIndex(oEstart);
			Event gEstart = generatedLog.findEventByIndex(oEstart, seoIndex);
			if (gEstart == null)
				continue;
			List<Event> generatedCase = generatedCases.get(gEstart.getCaseId());
			for (int oIndex = 1; oIndex < originalCase.size(); oIndex++) {
				Event oE = originalCase.get(oIndex);
				Event oEp = originalCase.get(oIndex - 1);
				actualFT = (oE.getTime() - oEp.getTime()) / 3600000.0;
				// actualFT = (oE.getTimestampD().getTime() / 3600000) -
				// (oEp.getTimestampD().getTime() / 3600000); // in hours
				int oeIndex = this.originalLog.getEIndex(oE);
				Event gE = generatedLog.findEventByIndex(oE, oeIndex);
				if (gE == null)
					continue;
				generatedCase = generatedCases.get(gEstart.getCaseId());
				int gIndex = generatedCase.lastIndexOf(gE);
				if (gIndex <= 0)
					continue;
				Event gEp = generatedCase.get(gIndex - 1);
				predicatedFT = (gE.getTime() - gEp.getTime()) / 3600000.0;
				// predicatedFT = (gE.getTimestampD().getTime() / 3600000) -
				// (gEp.getTimestampD().getTime() / 3600000); // in hours

				diff = Math.abs(actualFT - predicatedFT);
				MAD += diff;
			}

		}

		MAD = MAD / (double) (this.generatedLog.getnEvents() - this.generatedLog.getnCases());

		return MAD;

	};

	Callable<BigDecimal> computeETMAD2 = () -> {

		// double ctSMAPE = 0;
		BigDecimal MAD = new BigDecimal(0);
		double n = 0;
		// double count = 0;
		// double avgMAD = 0;
		double diff = 0;
		double predicatedFT = 0;
		double actualFT = 0;
		double sum = 0;
		double frac = 0;
		for (String cId : originalCases.keySet()) {
			// double cMAD = 0;
			List<Event> originalCase = this.originalCases.get(cId);
			Event oEstart = originalCase.get(0);
			Event oEend = originalCase.get(originalCase.size() - 1);
			int seoIndex = this.originalLog.getEIndex(oEstart);
			Event gEstart = generatedLog.findEventByIndex(oEstart, seoIndex);
			if (gEstart == null)
				continue;
			List<Event> generatedCase = generatedCases.get(gEstart.getCaseId());
			for (int oIndex = 1; oIndex < originalCase.size(); oIndex++) {
				Event oE = originalCase.get(oIndex);
				Event oEp = originalCase.get(oIndex - 1);
				actualFT = (oE.getTime() - oEp.getTime()) / 3600000.0;
				// actualFT = (oE.getTimestampD().getTime() / 3600000) -
				// (oEp.getTimestampD().getTime() / 3600000); // in hours
				int oeIndex = this.originalLog.getEIndex(oE);
				Event gE = generatedLog.findEventByIndex(oE, oeIndex);
				if (gE == null)
					continue;
				generatedCase = generatedCases.get(gEstart.getCaseId());
				int gIndex = generatedCase.lastIndexOf(gE);
				if (gIndex <= 0)
					continue;
				Event gEp = generatedCase.get(gIndex - 1);
				predicatedFT = (gE.getTime() - gEp.getTime()) / 3600000.0;
				// predicatedFT = (gE.getTimestampD().getTime() / 3600000) -
				// (gEp.getTimestampD().getTime() / 3600000); // in hours

				diff = Math.abs(actualFT - predicatedFT);
				MAD = MAD.add(BigDecimal.valueOf(diff));
			}

		}

		MAD = MAD.divide(BigDecimal.valueOf((this.generatedLog.getnEvents() - this.generatedLog.getnCases())), 2,
				RoundingMode.HALF_UP);

		return MAD;

	};
	Callable<Double[]> computeAvgTime = () -> {
		Hashtable<String, double[]> actExecDetails = new Hashtable<String, double[]>();
		double AllMeanE = 0;
		int nE = 0;
		for (String cId : originalCases.keySet()) {
			List<Event> originalCase = this.originalCases.get(cId);
			for (int oIndex = 1; oIndex < originalCase.size(); oIndex++) {
				Event oE = originalCase.get(oIndex);
				Event oEp = originalCase.get(oIndex - 1);
				if (!actExecDetails.containsKey(oE.getActivity()))
					actExecDetails.put(oE.getActivity(), new double[2]);
				actExecDetails.get(oE.getActivity())[0] += ((oE.getTime() - oEp.getTime()) / 3600000.0);
				// ((oE.getTimestampD().getTime() / 3600000)
				// - (oEp.getTimestampD().getTime() / 3600000)); // in hours
				actExecDetails.get(oE.getActivity())[1]++;
				AllMeanE += ((oE.getTime() - oEp.getTime()) / 3600000.0);
				// ((oE.getTimestampD().getTime() / 3600000) -
				// (oEp.getTimestampD().getTime() / 3600000));
				nE++;
			}
		}
		double allMean = 0;
		for (String act : actExecDetails.keySet()) {
			allMean += (actExecDetails.get(act)[0] / actExecDetails.get(act)[1]);
		}
		allMean = allMean / (double) actExecDetails.size();
		Double[] results = new Double[2];
		results[0] = allMean;
		results[1] = (AllMeanE / (double) nE);
		return results;
	};

	/*** Fitness compuation \similarity **/
	///////////////////////////// Log-Log fitness\accuracy measures based on the
	///////////////////////////// string-edit distance
	/*** Compute the string edit distance between 2 traces */
	private int computeEditDistance(List<String> trace1, List<String> trace2) {
		int distance = 0;
		List<String> longer = null;
		List<String> shorter = null;
		if (trace1.size() >= trace2.size()) {
			longer = new ArrayList<String>(trace1);
			shorter = new ArrayList<String>(trace2);
		} else {
			longer = new ArrayList<String>(trace2);
			shorter = new ArrayList<String>(trace1);
		}
		List<String> subGTrace = longer.subList(0, longer.size());

		for (String oAct : shorter) {
			// System.out.println("gTrace size:1st and last acts " +
			// gTrace.size() + " " + gTrace.get(0) + " :: "
			// + gTrace.get(gTrace.size() - 1) + " \n oAct : distance" + oAct +
			// ": " + distance);
			// System.out.print("oAct : distance" + oAct + ": " + distance);
			if (subGTrace.contains(oAct)) {
				int index = subGTrace.indexOf(oAct);
				distance += index; // ABC Vs AXYBC so when the oAct is B and
									// subGTrace is XYBC then it should remove
									// X,Y then its fine with B so the the no of
									// index as index is 2 which is 2 deletions
				subGTrace = subGTrace.subList(index + 1, subGTrace.size());
			} else {
				distance = distance + 1;// ABC vs ABB so when the oAct is C and
										// the subGTrace is B then it should
										// remove B and insert C so its two
										// mistake
			}
		}
		if (subGTrace.size() > 0) {
			distance += subGTrace.size();
		}
		return distance;
	}

	/***
	 * This function calculate the string edit distance with searching for
	 * (semi\full)matching for the original trace w.r.t the generated traces
	 * just calculate the distance between a trace and the other traces now i
	 * need to keep track to all the distance and return min distance, so it has
	 * to be sequential
	 */
	private List<Integer> calculateTraceDistanceL(ArrayList<String> origTrace,
			ConcurrentHashMap<ArrayList<String>, Integer> generatedtraces2) {
		// ConcurrentHashMap<List<String>, Integer> gTraceDistace = new
		// ConcurrentHashMap<>();
		AtomicInteger minDistance = new AtomicInteger(Integer.MAX_VALUE);
		AtomicInteger minTraceLength = new AtomicInteger(0);
		generatedtraces2.keySet().forEach(gTrace -> {

			// List<String> tempOrigTrace= new ArrayList<>(origTrace); // to
			// make hard copy and dont miss with the original while substring
			int distance = this.computeEditDistance(origTrace, gTrace);
			origGenTraceDistanceList.add(new TraceDistance(distance, origTrace, gTrace));
			// gTraceDistace.put(gTrace, distance);
			if (distance < minDistance.get()) {
				minDistance.set(distance);
				minTraceLength.set(gTrace.size());
			}
		});
		// }
		// origGenTraceDistance.put(origTrace, gTraceDistace);
		List<Integer> minValues = new ArrayList<>();

		minValues.add(0, minDistance.get());
		minValues.add(1, minTraceLength.get());

		return minValues;
	}

	/***
	 * This function calculate the string edit distance with searching for
	 * (semi\full)matching a case w.r.t list of cases distance = distance/max
	 * length
	 * 
	 * @return list of string-edit distance cost between the case1 and the list
	 *         of cases
	 */
	private List<Double> calculateCaseDistanceL(CaseInstance case1, List<CaseInstance> caseINSs) {
		List<Double> disValues = new CopyOnWriteArrayList();
		AtomicInteger case1Size = new AtomicInteger(case1.getActivities().size());
		caseINSs.stream().forEach(c -> {
			double distance = this.computeEditDistance(case1.getActivities(), c.getActivities());
			// distance = distance / (double) (Math.max(case1Size.get(),
			// c.getActivities().size()));
			distance = distance / (double) (case1Size.get() + c.getActivities().size());
			disValues.add(distance);
		});
		return disValues;
	}

	/***
	 * Build matrix of generated diff
	 */
	public List<List<Double>> computeDistanceGenCasesSimilarityL() {
		List<List<Double>> distances = new CopyOnWriteArrayList<>();
		this.genCases.parallelStream().forEach(gC -> {
			distances.add(this.calculateCaseDistanceL(gC, this.origCases));
		});
		return distances;
	}

	Callable<Double> computeLog2LogCasesSimiliarity = () -> {
		List<List<Double>> distanceList = computeDistanceGenCasesSimilarityL();

		// It creates a Stream<List<Double>> from your list of lists, then from
		// that uses map to replace each of the lists with an array of doubles
		// which results in a Stream<Double[]>, then finally calls toArray(with
		// a generator function, instead of the no-parameter version) on that to
		// produce the Double[][].
		Double[][] distanceMatrix = distanceList.stream().map(l -> l.stream().toArray(Double[]::new))
				.toArray(Double[][]::new);
		HungarianAlgorithm ha = new HungarianAlgorithm(distanceMatrix);

		int[][] alignmentArr = ha.findOptimalAssignment();
		double cost = 0;
		for (int i = 0; i < alignmentArr.length; i++) {
			
			cost += distanceList.get(alignmentArr[i][1]).get(alignmentArr[i][0]);
			
		}

		return cost / (double) this.nCases;
	};

	/***
	 * This function calculate the string edit distance with searching for
	 * (semi\full)matching for the original trace w.r.t the generated traces
	 * just calculate the distance between a trace and the other traces now i
	 * need to keep track to all the distance
	 */
	private void calculateTraceDistanceP(ArrayList<String> origTrace,
			ConcurrentHashMap<ArrayList<String>, Integer> generatedtraces2) {
		// ConcurrentHashMap<List<String>, Integer> gTraceDistace = new
		// ConcurrentHashMap<>();
		AtomicInteger minDistance = new AtomicInteger(Integer.MAX_VALUE);
		AtomicInteger minTraceLength = new AtomicInteger(0);
		generatedtraces2.keySet().parallelStream().forEach(gTrace -> {
			int distance = this.computeEditDistance(origTrace, gTrace);
			int min = Math.min(generatedtraces2.get(gTrace), this.Originaltraces.get(origTrace));
			origGenTraceDistanceList.add(new TraceDistance(distance, origTrace, gTrace, min));
		});
	}

	/***
	 * Build up the ConcurrentHashMap of the distance for each original trace
	 */
	public void computeDistanceLog2Fitness() {
		// for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
		this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
			List<Integer> minValues = calculateTraceDistanceL(origTrace, this.generatedtraces);
			OriginaltracesMinDistance.put(origTrace, minValues);
		});
		Collections.sort(this.origGenTraceDistanceList);
	}

	/***
	 * Build up the ConcurrentHashMap of the distance for each generated case
	 */
	public void computeDistanceLog2CasesFitness() {
		// for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
		this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
			List<Integer> minValues = calculateTraceDistanceL(origTrace, this.generatedtraces);
			OriginaltracesMinDistance.put(origTrace, minValues);
		});
		Collections.sort(this.origGenTraceDistanceList);
	}

	/***
	 * Build up the ConcurrentHashMap of the distance for each original trace
	 */
	public void computeDistanceLog2FitnessP() {
		this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
			calculateTraceDistanceP(origTrace, this.generatedtraces);
		});
		Collections.sort(this.origGenTraceDistanceList);
	}

	/***
	 * Compute the trace to trace fitness without considering the cases
	 * frequency of the trace; in other word consider the log as a set of traces
	 * not multi-set sum(minDistance/(|oTrace|+|minGtrace|))
	 * 
	 * @return fitness
	 */
	public double computeLog2FitnessSet() {

		AtomicInteger nem = new AtomicInteger(0);
		AtomicInteger dem = new AtomicInteger(0);
		this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
			List<Integer> minValues = OriginaltracesMinDistance.get(origTrace);// 0-distance
																				// 1-length
			nem.addAndGet(minValues.get(0));
			// minValues.get(1) is the gTrace.size
			dem.addAndGet((minValues.get(1) + origTrace.size()));
		});
		System.out.print("nem: " + nem.get());
		System.out.print("dem: " + dem.get());
		if (dem.get() == 0) {
			return -1;
		}
		double log2FitnessT = (double) nem.get() / (double) dem.get();
		return (1 - log2FitnessT);
	}

	/***
	 * Compute the trace to trace fitness with considering the cases frequency
	 * of the trace; we move based on the min values and remove it one by one
	 * And should keep track to the values of the gTraceCases that are used
	 * already AtomicInteger nem = new AtomicInteger(0); AtomicInteger dem = new
	 * AtomicInteger(0); // i will update the ncases of the used traces once
	 * they reached 0 i will remove it ConcurrentHashMap<List<String>, Integer>
	 * genTracesWnCases = new ConcurrentHashMap<>(this.generatedtraces);
	 * 
	 * this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
	 * ConcurrentHashMap<List<String>, Integer> gTracesDistances =
	 * this.origGenTraceDistance.get(origTrace); HashMap<List<String>, Integer>
	 * temp = new HashMap<>(gTracesDistances);
	 * temp.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(java.util.stream.Collectors
	 * .toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
	 * LinkedHashMap::new));
	 * 
	 * });
	 * 
	 * double log2FitnessT = (double) nem.get() / (double) dem.get(); return
	 * log2FitnessT;
	 * 
	 * @return fitness
	 */
	public double computeLog2FitnessMultiSet() {
		double fitness = 0;
		// AtomicInteger nem = new AtomicInteger(0);
		// AtomicInteger dem = new AtomicInteger(0);
		int nem = 0;
		int dem = 0;
		// first prove it with sequential then lets see how to transfer it in to
		// parallel
		Hashtable<List<String>, Integer> gtModifiy = new Hashtable<>(this.generatedtraces);
		Hashtable<List<String>, Integer> otModifiy = new Hashtable<>(this.Originaltraces);
		// CopyOnWriteArrayList<List<String>> toBeRemoved = new
		// CopyOnWriteArrayList<>();
		// CopyOnWriteArrayList<List<String>> toBeRemovedGen = new
		// CopyOnWriteArrayList<>();
		CopyOnWriteArrayList<TraceDistance> toBeRemovedTD = new CopyOnWriteArrayList<>();
		// Map<List<String>, List<TraceDistance>> originalTracedistanceGrp =
		// origGenTraceDistanceList.stream()
		// .collect(Collectors.groupingBy(td -> td.origTrace));
		// origGenTraceDistanceList.forEach(td -> {
		List<TraceDistance> origGenTraceDistanceList = new ArrayList<>(this.origGenTraceDistanceList);
		while (!origGenTraceDistanceList.isEmpty()) {
			for (TraceDistance td : origGenTraceDistanceList) {
				if (!toBeRemovedTD.contains(td)) {
					if (otModifiy != null && gtModifiy != null && !otModifiy.isEmpty() && !gtModifiy.isEmpty()) {

						if (otModifiy.get(td.getOrigTrace()) <= 0) {
							otModifiy.remove(td.getOrigTrace());
							toBeRemovedTD
									.addAll(this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));
						}
						if (gtModifiy.get(td.getgTrace()) <= 0) {
							gtModifiy.remove(td.getgTrace());
							toBeRemovedTD.addAll(this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
						}

						if (gtModifiy.get(td.getgTrace()) > 0 && otModifiy.get(td.getOrigTrace()) > 0) {
							// calculate fitness of this trace
							// min no of cases [ common cases]
							int min = td.optCost;
							if (min == Integer.MAX_VALUE) {
								min = Math.min(gtModifiy.get(td.getgTrace()), otModifiy.get(td.getOrigTrace()));
								nem += (min * td.getDistance());
							} else {
								nem += td.optCost;
							}
							// nem.addAndGet((min * td.getDistance()));
							// dem.addAndGet(min * (td.getgTrace().size() +
							// td.getOrigTrace().size()));

							dem += (min * td.tracesLength());// (td.getgTrace().size()
																// +
																// td.getOrigTrace().size()));
							// remove info from list
							int nOncases = otModifiy.get(td.getOrigTrace()) - min;
							otModifiy.put(td.getOrigTrace(), nOncases);
							int nGncases = gtModifiy.get(td.getgTrace()) - min;
							gtModifiy.put(td.getgTrace(), nGncases);
							if (nOncases == 0) {
								otModifiy.remove(td.getOrigTrace());
								toBeRemovedTD
										.addAll(this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));
								// break;
							}
							if (nGncases == 0) {
								gtModifiy.remove(td.getgTrace());
								toBeRemovedTD
										.addAll(this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
								// break;
							}
						}
					}
				}
			} // );

			origGenTraceDistanceList.removeAll(toBeRemovedTD);
			Collections.sort(origGenTraceDistanceList);
		}
		// if (origGenTraceDistanceList.isEmpty()) {
		// fitness = ((double) nem.get() / (double) dem.get());
		if (dem == 0)
			return -1;
		fitness = ((double) nem / (double) dem);
		return (1 - fitness);
		// }
		// else {
		// Collections.sort(origGenTraceDistanceList);
		// fitness = computeLog2FitnessMultiSet(gtModifiy, otModifiy, nem, dem,
		// origGenTraceDistanceList);
		// if (fitness == -1)
		// return fitness;
		// return (1 - fitness);
		// }
	}

	public double computeLog2FitnessMultiSet(Hashtable<List<String>, Integer> gtModifiy,
			Hashtable<List<String>, Integer> otModifiy, int nem, int dem,
			List<TraceDistance> origGenTraceDistanceList) {

		// first prove it with sequential then lets see how to transfer it in to
		// parallel
		List<TraceDistance> toBeRemovedTD = new ArrayList<>();// CopyOnWriteArrayList<>();

		// origGenTraceDistanceList.stream().forEach(td -> {
		for (TraceDistance td : origGenTraceDistanceList) {

			if (!toBeRemovedTD.contains(td)) {
				if (otModifiy != null && gtModifiy != null && !otModifiy.isEmpty() && !gtModifiy.isEmpty()) {
					if (otModifiy.get(td.getOrigTrace()) <= 0) {
						otModifiy.remove(td.getOrigTrace());
						toBeRemovedTD.addAll(this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));
						// break;
					}
					if (gtModifiy.get(td.getgTrace()) <= 0) {
						gtModifiy.remove(td.getgTrace());
						toBeRemovedTD.addAll(this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
						// break;
					}

					if (gtModifiy.get(td.getgTrace()) > 0 && otModifiy.get(td.getOrigTrace()) > 0) {
						// calculate fitness of this trace
						// nem.addAndGet((min * td.getDistance()));
						// dem.addAndGet(min * (td.getgTrace().size() +
						// td.getOrigTrace().size()));
						int min = td.optCost;
						if (min == Integer.MAX_VALUE) {
							min = Math.min(gtModifiy.get(td.getgTrace()), otModifiy.get(td.getOrigTrace()));
							nem += (min * td.getDistance());
						} else {
							nem += td.optCost;
						}
						// nem.addAndGet((min * td.getDistance()));
						// dem.addAndGet(min * (td.getgTrace().size() +
						// td.getOrigTrace().size()));

						dem += (min * td.tracesLength());// (td.getgTrace().size()
															// +
															// td.getOrigTrace().size()));
						toBeRemovedTD.add(td);
						// remove info from list
						int nOncases = otModifiy.get(td.getOrigTrace()) - min;
						otModifiy.put(td.getOrigTrace(), nOncases);
						int nGncases = gtModifiy.get(td.getgTrace()) - min;
						gtModifiy.put(td.getgTrace(), nGncases);
						if (nOncases == 0) {
							otModifiy.remove(td.getOrigTrace());
							toBeRemovedTD
									.addAll(this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));
							// break;
						}
						if (nGncases == 0) {
							gtModifiy.remove(td.getgTrace());
							toBeRemovedTD.addAll(this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
							break;
						}

					}
				}
			}
		} // );
		origGenTraceDistanceList.removeAll(toBeRemovedTD);
		if (origGenTraceDistanceList.isEmpty()) {
			if (dem == 0)
				return -1;

			return ((double) nem / (double) dem);
		} else {
			Collections.sort(origGenTraceDistanceList);
			return computeLog2FitnessMultiSet(gtModifiy, otModifiy, nem, dem, origGenTraceDistanceList);
		}
	}

	private List<TraceDistance> getUnneededTDs(List<String> trace, List<TraceDistance> origGenTraceDistanceList,
			boolean isOriginalTrace) {
		List<TraceDistance> unneeded = new CopyOnWriteArrayList<>();
		if (isOriginalTrace) {
			origGenTraceDistanceList.parallelStream().forEach(td -> {
				if (td.getOrigTrace().equals(trace))
					unneeded.add(td);
			});
		} else {
			origGenTraceDistanceList.parallelStream().forEach(td -> {
				if (td.getgTrace().equals(trace))
					unneeded.add(td);
			});
		}
		return unneeded;
	}

	// to use it in future executive service

	Callable<Double> computeLog2FitnessMultiSet = () -> {
		this.computeDistanceLog2Fitness();
		double fitness = 0;
		// AtomicInteger nem = new AtomicInteger(0);
		// AtomicInteger dem = new AtomicInteger(0);
		int nem = 0;
		int dem = 0;
		// first prove it with sequential then lets see how to transfer it in to
		// parallel
		Hashtable<List<String>, Integer> gtModifiy = new Hashtable<>(this.generatedtraces);
		Hashtable<List<String>, Integer> otModifiy = new Hashtable<>(this.Originaltraces);
		// CopyOnWriteArrayList<List<String>> toBeRemoved = new
		// CopyOnWriteArrayList<>();
		// CopyOnWriteArrayList<List<String>> toBeRemovedGen = new
		// CopyOnWriteArrayList<>();
		CopyOnWriteArrayList<TraceDistance> toBeRemovedTD = new CopyOnWriteArrayList<>();
		// Map<List<String>, List<TraceDistance>> originalTracedistanceGrp =
		// origGenTraceDistanceList.stream()
		// .collect(Collectors.groupingBy(td -> td.origTrace));
		// origGenTraceDistanceList.forEach(td -> {
		List<TraceDistance> origGenTraceDistanceList = new ArrayList<>(this.origGenTraceDistanceList);
		while (!origGenTraceDistanceList.isEmpty()) {

			for (TraceDistance td : origGenTraceDistanceList) {
				if (!toBeRemovedTD.contains(td)) {
					if (otModifiy != null && gtModifiy != null && !otModifiy.isEmpty() && !gtModifiy.isEmpty()) {

						if (otModifiy.get(td.getOrigTrace()) <= 0) {
							otModifiy.remove(td.getOrigTrace());
							toBeRemovedTD
									.addAll(this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));

						}
						if (gtModifiy.get(td.getgTrace()) <= 0) {
							gtModifiy.remove(td.getgTrace());
							toBeRemovedTD.addAll(this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
						}
						if (gtModifiy.containsKey(td.getgTrace()) && otModifiy.containsKey(td.getOrigTrace())) {
							if (gtModifiy.get(td.getgTrace()) > 0 && otModifiy.get(td.getOrigTrace()) > 0) {
								// calculate fitness of this trace
								int minCases = td.optCost;
								if (minCases == Integer.MAX_VALUE) {
									minCases = Math.min(gtModifiy.get(td.getgTrace()),
											otModifiy.get(td.getOrigTrace()));
									nem += (minCases * td.getDistance());
								} else {
									nem += td.optCost;
								}

								dem += (minCases * td.tracesLength());// (td.getgTrace().size()
																		// +
																		// td.getOrigTrace().size()));
								// remove info from list
								int nOncases = otModifiy.get(td.getOrigTrace()) - minCases;
								otModifiy.put(td.getOrigTrace(), nOncases);
								int nGncases = gtModifiy.get(td.getgTrace()) - minCases;
								gtModifiy.put(td.getgTrace(), nGncases);
								if (nOncases == 0) {
									otModifiy.remove(td.getOrigTrace());
									toBeRemovedTD.addAll(
											this.getUnneededTDs(td.getOrigTrace(), origGenTraceDistanceList, true));
									break;
								}
								if (nGncases == 0) {
									gtModifiy.remove(td.getgTrace());
									toBeRemovedTD.addAll(
											this.getUnneededTDs(td.getgTrace(), origGenTraceDistanceList, false));
									break;
								}
							}
						}
					}
				}
			} // );

			origGenTraceDistanceList.removeAll(toBeRemovedTD);
			Collections.sort(origGenTraceDistanceList);
		}
		// if (origGenTraceDistanceList.isEmpty()) {
		// // fitness = ((double) nem.get() / (double) dem.get());
		fitness = ((double) nem / (double) dem);
		if (dem == 0)
			return (double) -1;

		return (1 - fitness);
		// } else {
		// Collections.sort(origGenTraceDistanceList);
		// fitness = computeLog2FitnessMultiSet(gtModifiy, otModifiy, nem, dem,
		// origGenTraceDistanceList);
		// if (fitness == -1)
		// return fitness;
		// return (1 - fitness);
		// }
	};

	Callable<Double> computeLog2LogTraces = () -> {
		AtomicInteger nem = new AtomicInteger(0);
		AtomicInteger dem = new AtomicInteger(0);
		this.Originaltraces.keySet().parallelStream().forEach(origTrace -> {
			if (OriginaltracesMinDistance.containsKey(origTrace)) {
				List<Integer> minValues = OriginaltracesMinDistance.get(origTrace);// 0-distance
																					// 1-length
				if (minValues != null && !minValues.isEmpty()) {
					nem.addAndGet(minValues.get(0));
					// minValues.get(1) is the gTrace.size
					dem.addAndGet((minValues.get(1) + origTrace.size()));
				}
			}
		});
		// System.out.print("nem: " + nem.get());
		// System.out.print("dem: " + dem.get());
		if (dem.get() == 0) {
			// return -1.0;
			dem.set(this.originalLog.getnEvents());
		}
		double log2FitnessT = (double) nem.get() / (double) dem.get();
		return (1 - log2FitnessT);

	};

	/*** Compute the proportion of the wrong events to total events */

	/***
	 * This function calculate the string edit distance with searching for
	 * (semi\full)matching for the original trace w.r.t the generated traces
	 * just calculate the distance between a trace and the other traces now i
	 * need to keep track to all the distance
	 */
	private int calculateTraceDistance(ArrayList<String> origTrace,
			ConcurrentHashMap<ArrayList<String>, Integer> generatedtraces2) {
		ConcurrentHashMap<List<String>, Integer> gTraceDistace = new ConcurrentHashMap<>();
		AtomicInteger minDistance = new AtomicInteger(Integer.MAX_VALUE);

		// for (ArrayList<String> gTrace : generatedtraces2.keySet()) {
		generatedtraces2.keySet().parallelStream().forEach(gTrace -> {
			List<String> subGTrace = gTrace.subList(0, gTrace.size());
			int distance = 0;
			for (String oAct : origTrace) {
				if (subGTrace.contains(oAct)) {
					int index = subGTrace.indexOf(oAct);
					distance += index; // ABC Vs AXYBC so when the oAct is B and
										// subGTrace is XYBC then it should
										// remove X,Y then its fine with B so
										// the the no of index as index is 2
										// which is 2 deletions
					subGTrace = subGTrace.subList(index + 1, subGTrace.size());
				} else {
					distance = distance + 2;// ABC vs ABB so when the oAct is C
											// and the subGTrace is B then it
											// should remove B and insert C so
											// its two mistake
				}
			}
			if (distance < (Math.abs(gTrace.size() - origTrace.size())))
				distance += Math.abs(gTrace.size() - origTrace.size());

			gTraceDistace.put(gTrace, distance);
			if (distance < minDistance.get()) {
				minDistance.set(distance);
			}
		});

		return minDistance.get();
	}

	public void correctTracesPercentageDistanceCalculation() {
		float per = 0;
		int existInGenerate = 0;
		int notExits = 0;
		double ratio = 0;
		double ratioWo = 0;
		int distance = 0;
		int distanceWoC = 0;
		for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
			if (this.generatedtraces.containsKey(origTrace)) {
				existInGenerate++;
				// OriginaltracesMinDistance.put(origTrace, 0);
			} else {
				notExits++;
				int mindistance = calculateTraceDistance(origTrace, this.generatedtraces);
				distance += (mindistance * this.Originaltraces.get(origTrace));
				distanceWoC += mindistance;
				ratio += ((double) (distance / ((double) origTrace.size() * this.Originaltraces.get(origTrace))));
				ratioWo += ((double) (mindistance / ((double) origTrace.size())));
				// OriginaltracesMinDistance.put(origTrace, mindistance);
			}
		}

		System.out.println("Alignment raw cost * nCases " + distance);
		// System.out.println("Alignment raw cost " + distanceWoC);

		per = (float) existInGenerate / this.Originaltraces.size();
		System.out.println("Percentage of original Traces that exist in the generated log" + per);
		per = (float) notExits / this.Originaltraces.size();
		System.out.println("Percentage of original Traces that do not exist in the generated log" + per);

		// double avg = 0;
		// for (int value : OriginaltracesMinDistance.values()) {
		// avg += value;
		// }
		// avg = avg / (double) origGenTraceDistance.values().size();
		// System.out.println("The average distance of the non generated traces
		// is " + avg);
		// System.out.println("Proportion of wrong events to correct " + ratio);
		// System.out.println("Proportion of edit act within the traces" +
		// ratioWo);
		System.out.println("Avg Proportion of wrong events to correct over traces "
				+ (ratioWo / (double) this.Originaltraces.size()));
		double wrongEvents = (distance / (double) this.originalLog.getnEvents());
		System.out.println("wrong events over all the events (1-Fitness)" + wrongEvents);
		System.out.println("Correct events (fitness)" + (1 - wrongEvents));
	}

	public void correctTracesPercentageDistanceCalculation(StringBuilder consoleText) {
		float per = 0;
		int existInGenerate = 0;
		int notExits = 0;
		double ratio = 0;
		double ratioWo = 0;
		int distance = 0;
		int distanceWoC = 0;
		for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
			if (this.generatedtraces.containsKey(origTrace)) {
				existInGenerate++;
				// OriginaltracesMinDistance.put(origTrace, 0);
			} else {
				notExits++;
				int mindistance = calculateTraceDistance(origTrace, this.generatedtraces);
				distance += (mindistance * this.Originaltraces.get(origTrace));
				distanceWoC += mindistance;
				ratio += ((double) (distance / ((double) origTrace.size() * this.Originaltraces.get(origTrace))));
				ratioWo += ((double) (mindistance / ((double) origTrace.size())));
				// OriginaltracesMinDistance.put(origTrace, mindistance);
			}
		}

		consoleText.append("Alignment raw cost * nCases " + distance);
		consoleText.append(System.getProperty("line.separator"));
		// consoleText.append("Alignment raw cost " + distanceWoC);

		per = (float) existInGenerate / this.Originaltraces.size();
		consoleText.append("Percentage of original Traces that exist in the generated log" + per);
		consoleText.append(System.getProperty("line.separator"));
		per = (float) notExits / this.Originaltraces.size();
		consoleText.append("Percentage of original Traces that do not exist in the generated log" + per);
		consoleText.append(System.getProperty("line.separator"));

		// double avg = 0;
		// for (int value : OriginaltracesMinDistance.values()) {
		// avg += value;
		// }
		// avg = avg / (double) origGenTraceDistance.values().size();
		// consoleText.append("The average distance of the non generated traces
		// is " + avg);
		// consoleText.append("Proportion of wrong events to correct " + ratio);
		// consoleText.append("Proportion of edit act within the traces" +
		// ratioWo);
		consoleText.append("Avg Proportion of wrong events to correct over traces "
				+ (ratioWo / (double) this.Originaltraces.size()));
		consoleText.append(System.getProperty("line.separator"));
		double wrongEvents = (distance / (double) this.originalLog.getnEvents());
		consoleText.append("wrong events over all the events (1-Fitness)" + wrongEvents);
		consoleText.append(System.getProperty("line.separator"));
		consoleText.append("Correct events (fitness)" + (1 - wrongEvents));
		consoleText.append(System.getProperty("line.separator"));
	}

	public void computeLog2FitnessSeqFreq(StringBuilder consoleText) {
		float per = 0;
		int existInGenerate = 0;
		int notExits = 0;
		double ratio = 0;
		double ratioWo = 0;
		int distance = 0;
		int distanceWoC = 0;
		for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
			if (this.generatedtraces.containsKey(origTrace)) {
				existInGenerate++;
				// OriginaltracesMinDistance.put(origTrace, 0);
			} else {
				notExits++;
				int mindistance = calculateTraceDistance(origTrace, this.generatedtraces);
				distance += (mindistance * this.Originaltraces.get(origTrace));
				distanceWoC += mindistance;
				ratio += ((double) (distance / ((double) origTrace.size() * this.Originaltraces.get(origTrace))));
				ratioWo += ((double) (mindistance / ((double) origTrace.size())));
				// OriginaltracesMinDistance.put(origTrace, mindistance);
			}
		}

		consoleText.append("Alignment raw cost * nCases " + distance);
		consoleText.append(System.getProperty("line.separator"));
		// consoleText.append("Alignment raw cost " + distanceWoC);

		per = (float) existInGenerate / this.Originaltraces.size();
		consoleText.append("Percentage of original Traces that exist in the generated log" + per);
		consoleText.append(System.getProperty("line.separator"));
		per = (float) notExits / this.Originaltraces.size();
		consoleText.append("Percentage of original Traces that do not exist in the generated log" + per);
		consoleText.append(System.getProperty("line.separator"));

		// double avg = 0;
		// for (int value : OriginaltracesMinDistance.values()) {
		// avg += value;
		// }
		// avg = avg / (double) origGenTraceDistance.values().size();
		// consoleText.append("The average distance of the non generated traces
		// is " + avg);
		// consoleText.append("Proportion of wrong events to correct " + ratio);
		// consoleText.append("Proportion of edit act within the traces" +
		// ratioWo);
		consoleText.append("Avg Proportion of wrong events to correct over traces "
				+ (ratioWo / (double) this.Originaltraces.size()));
		consoleText.append(System.getProperty("line.separator"));
		double wrongEvents = (distance / (double) this.originalLog.getnEvents());
		consoleText.append("wrong events over all the events (1-Fitness)" + wrongEvents);
		consoleText.append(System.getProperty("line.separator"));
		consoleText.append("Correct events (fitness)" + (1 - wrongEvents));
		consoleText.append(System.getProperty("line.separator"));
	}

	Callable<Double> computeAvgEventTraces = () -> {
		float per = 0;
		int existInGenerate = 0;
		int notExits = 0;
		double ratio = 0;
		double ratioWo = 0;
		int distance = 0;
		int distanceWoC = 0;
		for (ArrayList<String> origTrace : this.Originaltraces.keySet()) {
			if (this.generatedtraces.containsKey(origTrace)) {
				existInGenerate++;
				// OriginaltracesMinDistance.put(origTrace, 0);
			} else {
				notExits++;
				int mindistance = calculateTraceDistance(origTrace, this.generatedtraces);
				distance += (mindistance * this.Originaltraces.get(origTrace));
				distanceWoC += mindistance;
				ratio += ((double) (distance / ((double) origTrace.size() * this.Originaltraces.get(origTrace))));
				ratioWo += ((double) (mindistance / ((double) origTrace.size())));
				// OriginaltracesMinDistance.put(origTrace, mindistance);
			}
		}
		double wrongEvents = (distance / (double) this.originalLog.getnEvents());
		return wrongEvents;

	};

	/**
	 * @return the generatedLog
	 */
	public Log getGeneratedLog() {
		return generatedLog;
	}

	/**
	 * @param generatedLog
	 *            the generatedLog to set
	 */
	public void setGeneratedLog(Log generatedLog) {
		this.generatedLog = generatedLog;
	}

	public void execute(StringBuilder consoleText) throws Exception {
		ExecutorService executorService = Executors.newCachedThreadPool();
		Future<Double> future12 = executorService.submit(computeLog2LogTraces);
		Future<Double> future1 = executorService.submit(computeLog2FitnessMultiSet);
		Future<Double> future2 = executorService.submit(computeETSMAPE);
		Future<Double> future4MAD = executorService.submit(computeETMAD);
		Future<Double[]> future5avg = executorService.submit(computeAvgTime);

		// Future<Boolean> future3Wr =
		// executorService.submit(this.generatedLog.writeLogCSV);
		// wait until result will be ready
		Double LtoL = future1.get();
		Double LtoLtraces = future12.get();
		// wait only certain timeout otherwise throw an exception
		Double etSMAPE = future2.get();

		Double MAD = future4MAD.get();

		Double avg[] = future5avg.get();
		consoleText.append("Trace to trace Fitness[Multiset] = " + LtoL + ";");
		consoleText.append("Trace to trace Fitness[Traces] = " + LtoLtraces + ";");
		consoleText.append("Event time SMAPE = " + etSMAPE + ";");
		consoleText.append("Event time MAD = " + MAD + ";");
		consoleText.append("Original Activity average time= " + avg[0] + ";");
		consoleText.append("Original Event average time= " + avg[1] + ";");

		// Boolean written = future3Wr.get();
		// consoleText.append("written");

		executorService.shutdown();
	}

	public void executeW(StringBuilder consoleText) throws Exception {
		ExecutorService executorService = Executors.newCachedThreadPool();
		Future<Double> future12 = executorService.submit(computeLog2LogTraces);
		Future<Double> future1 = executorService.submit(computeLog2FitnessMultiSet);
		Future<Double> future2 = executorService.submit(computeETSMAPE);
		Future<Double> future3 = executorService.submit(computeAvgEventTraces);
		// wait until result will be ready
		Double LtoL = future1.get();
		Double LtoLtraces = future12.get();
		// wait only certain timeout otherwise throw an exception
		Double etSMAPE = future2.get();

		Double eventPer = future3.get();
		consoleText.append("Trace to trace Fitness[Multiset] = " + LtoL + ";");
		consoleText.append("Trace to trace Fitness[Traces] = " + LtoLtraces + ";");
		consoleText.append("Event time SMAPE = " + etSMAPE + ";");
		executorService.shutdown();
	}

	public void execute2(StringBuilder consoleText) throws Exception {
		ExecutorService executorService = Executors.newCachedThreadPool();

//		Future<Double> future1 = executorService.submit(computeLog2FitnessMultiSet);
		Future<Double> futureSim = executorService.submit(computeLog2LogCasesSimiliarity);

		Future<Double> future2 = executorService.submit(computeETSMAPE);
		Future<Double> future4MAD = executorService.submit(computeETMAD);
		Future<Double[]> future5avg = executorService.submit(computeAvgTime);
		// Future<BigDecimal> future3MADBD =
		// executorService.submit(computeETMAD2);
		// wait until result will be ready
//		Double LtoL = future1.get();
		Double sim = futureSim.get();
		// wait only certain timeout otherwise throw an exception
		Double etSMAPE = future2.get();

		Double MAD = future4MAD.get();
		// BigDecimal MADbd = future3MADBD.get();
		Double avg[] = future5avg.get();
//		consoleText.append("Trace to trace Fitness[Multiset] = " + LtoL + ";");
		consoleText.append("case to case difference = " + sim + "; ");
		consoleText.append("case to case similarity 1-diff = " + (1 - sim) + "; ");
		consoleText.append("Event time SMAPE = " + etSMAPE + "; ");
		consoleText.append("Event time MAD = " + MAD + "; ");
		// consoleText.append("Event time MAD-BD = " + MADbd.toString() + ";");
		consoleText.append("Original Activity average time= " + avg[0] + "; ");
		consoleText.append("Original Event average time= " + avg[1] + "; ");

		executorService.shutdown();
	}
}