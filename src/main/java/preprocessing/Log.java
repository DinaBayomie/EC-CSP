package preprocessing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.opencsv.CSVWriter;
// for reading the csv
import com.opencsv.bean.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDateTime;

public class Log {

	List<Event> events;
	Map<String, List<Event>> actEvents;

	int nCases;
	HashSet<String> activities;
	boolean isLabeled = false;

	public Log(String logFilePath) {
		try {
			this.events = new CopyOnWriteArrayList<>(readLogCSV(logFilePath));
			actEvents = this.events.stream().collect(Collectors.groupingBy(e -> e.getActivity()));
			this.activities = getDActivities();
			Collections.sort(this.events);
			updateEventsId(events);

		} catch (IOException ex) {
			System.out.print(ex);
		}
	}

	public Log(String logFilePath, boolean labeled) {
		try {
			if (labeled)
				this.events = readLabeledLogCSV(logFilePath);
			else
				this.events = readLogCSV(logFilePath);
			Collections.sort(this.events);
			this.activities = getDActivities();
			this.isLabeled = true;
			updateEventsId(events);

		} catch (IOException ex) {
			System.out.print(ex);
		}
	}

	// read the log file in the main
	public Log(List<Event> events) {
		this.events = events;
	}

	public Log(Log orglabeledLog) {
		// TODO Auto-generated constructor stub
		// this.events = new ArrayList<>(orglabeledLog.getEvents());
		this.events = new CopyOnWriteArrayList<Event>();
		orglabeledLog.getEvents().parallelStream().forEach(e -> {

			this.events.add(new Event(e));
		});
		Collections.sort(this.events);
		this.activities = new HashSet<>(orglabeledLog.getActivities());
	}

	public List<Event> getEvents() {
		return events;
	}

	public void setEvents(List<Event> events) {
		this.events = events;
	}

	public List<Event> readLogCSV(String filename) throws IOException {
		List<Event> unlabeledEvents = null;
		// access file path
		// read each row
		// create event object
		// add event object to the output list
		try (Reader reader = Files.newBufferedReader(Paths.get(filename));) {
			ColumnPositionMappingStrategy strategy = new ColumnPositionMappingStrategy();
			strategy.setType(Event.class);
			String[] memberFieldsToBindTo = { "activity", "timestamp" };
			strategy.setColumnMapping(memberFieldsToBindTo);

			CsvToBean csvToBean = new CsvToBeanBuilder(reader).withMappingStrategy(strategy).withSkipLines(1)
					.withIgnoreLeadingWhiteSpace(true).build();

			unlabeledEvents = csvToBean.parse();
		}
		updateEventsId(unlabeledEvents);
		return unlabeledEvents;
	}

	public List<Event> readLabeledLogCSV(String filename) throws IOException {
		List<Event> labeledEvents = null;
		// access file path
		// read each row
		// create event object
		// add event object to the output list
		try (Reader reader = Files.newBufferedReader(Paths.get(filename));) {
			ColumnPositionMappingStrategy strategy = new ColumnPositionMappingStrategy();
			strategy.setType(Event.class);
			String[] memberFieldsToBindTo = { "caseId", "Activity", "Timestamp" };
			strategy.setColumnMapping(memberFieldsToBindTo);

			CsvToBean csvToBean = new CsvToBeanBuilder(reader).withMappingStrategy(strategy).withSkipLines(1)
					.withIgnoreLeadingWhiteSpace(true).build();

			labeledEvents = csvToBean.parse();
		}
		updateEventsId(labeledEvents);
		return labeledEvents;
	}

	private void updateEventsId(List<Event> events) {
		for (int i = 0; i < events.size(); i++) {
			events.get(i).setId(i);
		}
	}

	String outPutName;
	String outputFolderName;

	public void setOutputFileName(String fName, String folderName) {
		outputFolderName = folderName;
		outPutName = fName;
	}

	public Callable<Boolean> writeLogCSV = () -> {
		Writer csvWriter = null;
		try {
			String filePath = "./resources/output/" + this.outputFolderName + "/LabeledLog" + this.outPutName + ".csv";
			csvWriter = new FileWriter(filePath);

			ColumnPositionMappingStrategy<Event> strategy = new ColumnPositionMappingStrategy<Event>();
			strategy.setType(Event.class);
			String[] memberFieldsToBindTo = { "caseId", "activity", "timestamp" };
			strategy.setColumnMapping(memberFieldsToBindTo);

			// worst solution ever
			Event e = new Event("activity", "timestamp");
			e.setCaseId("caseId");

			StatefulBeanToCsvBuilder<Event> builder = new StatefulBeanToCsvBuilder<Event>(csvWriter);

			StatefulBeanToCsv<Event> beanToCsv = builder.withMappingStrategy(strategy).build();

			beanToCsv.write(e);
			for (Event event : this.events) {
				beanToCsv.write(event);
			}

		} catch (Exception ee) {
			ee.printStackTrace();
			return false;
		} finally {
			csvWriter.flush();
			csvWriter.close();
		}
		return true;
	};

	public boolean writeLogCSV(String fileName) {

		Writer csvWriter = null;
		try {
			String filePath = "./resources/output/LabeledLog" + fileName + ".csv";
			csvWriter = new FileWriter(filePath);// "./resources/LabeledLog.csv"

			ColumnPositionMappingStrategy<Event> strategy = new ColumnPositionMappingStrategy<Event>();
			strategy.setType(Event.class);
			String[] memberFieldsToBindTo = { "caseId", "activity", "timestamp" };
			strategy.setColumnMapping(memberFieldsToBindTo);

			// worst solution ever
			Event e = new Event("activity", "timestamp");
			e.setCaseId("caseId");

			StatefulBeanToCsvBuilder<Event> builder = new StatefulBeanToCsvBuilder<Event>(csvWriter);

			StatefulBeanToCsv<Event> beanToCsv = builder.withMappingStrategy(strategy).build();

			beanToCsv.write(e);
			for (Event event : this.events) {
				beanToCsv.write(event);
			}

			csvWriter.close();
		} catch (Exception ee) {
			ee.printStackTrace();
		}
		// finally
		// {
		// try
		// {
		// //closing the writer
		//// csvWriter.close();
		// }
		// catch(Exception ee)
		// {
		// ee.printStackTrace();
		// }
		// }

		return true;
	}

	public HashSet<Trace> getLogTracesobj() {
		if (!this.isLabeled)
			return null;

		HashSet<Trace> traces = new HashSet<Trace>();
		Hashtable<ArrayList<String>, Integer> tracesNo = new Hashtable<ArrayList<String>, Integer>(nCases);
		Map<String, List<Event>> cases = this.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		for (String cId : cases.keySet()) {
			List<String> actSeq = this.getEventNames(cases.get(cId));
			if (!tracesNo.containsKey(actSeq))
				tracesNo.put((ArrayList<String>) actSeq, 1);
			else {
				int nlCases = tracesNo.get(actSeq);
				nlCases = nlCases + 1;
			}
			// traces.add(caseEventNames);
		}
		for (ArrayList<String> trace : tracesNo.keySet()) {
			Trace t = new Trace(trace, tracesNo.get(trace));
			traces.add(t);
		}
		tracesNo = null;
		return traces;
	}

	public Hashtable<ArrayList<String>, Integer> getLogTraces() {
		if (!this.isLabeled)
			return null;

		HashSet<Trace> traces = new HashSet<Trace>();
		Hashtable<ArrayList<String>, Integer> tracesNo = new Hashtable<ArrayList<String>, Integer>(nCases);
		Map<String, List<Event>> cases = this.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		for (String cId : cases.keySet()) {
			List<String> actSeq = this.getEventNames(cases.get(cId));
			if (!tracesNo.containsKey(actSeq))
				tracesNo.put((ArrayList<String>) actSeq, 1);
			else {
				int nlCases = tracesNo.get(actSeq);
				nlCases = nlCases + 1;
			}
			// traces.add(caseEventNames);
		}

		return tracesNo;
	}

	public Hashtable<ArrayList<String>, ArrayList<String>> getTraces() {
		if (!this.isLabeled)
			return null;

		// HashSet<Trace> traces = new HashSet<Trace>();
		Hashtable<ArrayList<String>, ArrayList<String>> traces = new Hashtable<ArrayList<String>, ArrayList<String>>(
				nCases);
		Map<String, List<Event>> cases = this.getEvents().stream().collect(Collectors.groupingBy(e -> e.getCaseId()));
		for (String cId : cases.keySet()) {
			List<String> actSeq = this.getEventNames(cases.get(cId));
			if (!traces.containsKey(actSeq)) {
				ArrayList<String> casesIds = new ArrayList<>();
				casesIds.add(cId);
				traces.put((ArrayList<String>) actSeq, casesIds);
			} else {
				traces.get(actSeq).add(cId);
			}
		}

		return traces;
	}

	private List<String> getEventNames(List<Event> cEvents) {
		List<String> actSeq = new ArrayList<>();
		for (Event event : cEvents) {
			actSeq.add(event.activity);
		}
		return actSeq;
	}

	public Event findEvent(Event e) {

		if (e != null) {
			List<Event> eventsToCheck = this.events;
			if (actEvents != null && e.getActivity() != null)
				eventsToCheck = actEvents.get(e.getActivity());
			if (eventsToCheck == null)
				eventsToCheck = this.events;
			for (Event event : eventsToCheck) {
				if (event.equals(e))
					return event;
			}
		} else {
			System.out.println("null event");
		}
		return null;
	}

	public Event findEventByIndex(Event e, int eindex) {
		if (eindex < this.events.size()) {
			Event e2 = this.events.get(eindex);
			if (e2.equals(e)) {
				return e2;
			} else
				return findEvent(e);
		} else
			return findEvent(e);
	}

	public int getEIndex(Event e) {
		return this.events.indexOf(e);
	}

	public Event findEventInCase(List<Event> caseEvents, Event e) {
		for (Event event : caseEvents) {
			if (event.equals(e))
				return event;
		}
		return null;
	}

	/**
	 * @return the nCases
	 */
	public int getnCases() {
		return nCases;
	}

	/**
	 * @return the number of events
	 */
	public int getnEvents() {
		return this.events.size();
	}

	/**
	 * @param nCases
	 *            the nCases to set
	 */
	public void setnCases(int nCases) {
		this.nCases = nCases;
	}

	public HashSet<String> getDActivities() {
		List<String> names = new CopyOnWriteArrayList<>();
		// for (Event e : this.events) {
		// activities.add(e.getActivity());
		// }

		this.events.parallelStream().forEach(e -> {
			names.add(e.getActivity());
		});
		HashSet<String> activities = new HashSet<>(names);
		return activities;
	}

	/**
	 * @return the activities
	 */
	public HashSet<String> getActivities() {
		return activities;
	}

	/**
	 * @param activities
	 *            the activities to set
	 */
	public void setActivities(HashSet<String> activities) {
		this.activities = activities;
	}

	/**
	 * @return the isLabeled
	 */
	public boolean isLabeled() {
		return isLabeled;
	}

	/**
	 * @param isLabeled
	 *            the isLabeled to set
	 */
	public void setLabeled(boolean isLabeled) {
		this.isLabeled = isLabeled;
	}

	@Override
	public String toString() {
		StringBuilder log = new StringBuilder();
		events.parallelStream().forEach(e -> {
			log.append(e.toString() + "--");
		});
		return log.toString();
	}

	public List<Event> getActEventsBackward(String act, int i) {
		List<Event> subEvents = new CopyOnWriteArrayList<>();

		IntStream.range(0, i).parallel().forEach(index -> {
			Event e = this.events.get(index);
			if (e.getActivity().toLowerCase().equals(act.toLowerCase()))
				subEvents.add(e);
		});

		return subEvents;
	}

	public List<Event> getActEvents(String act, int i) {
		List<Event> selEvents = new CopyOnWriteArrayList<>();
		List<Event> subEvents = new CopyOnWriteArrayList<>(this.events.subList(i, this.getnEvents()));
		// act = act.toLowerCase();
		// IntStream.range(i, this.getnEvents()).parallel().forEach(index -> {
		subEvents.parallelStream().forEach(e -> {
			// Event e = this.events.get(index);
			// System.out.print(act);
			// System.out.print(e.getActivity() + "-");

			if (e.getActivity().toLowerCase().equals(act.toLowerCase()))
				selEvents.add(e);
		});

		return selEvents;
	}

	public List<Event> getActEvents(List<String> endActs, int j, int i) {
		List<Event> selEvents = new CopyOnWriteArrayList<>();
		List<Event> subEvents = new CopyOnWriteArrayList<>(this.events.subList(j, i));
		subEvents.parallelStream().forEach(e -> {
			if (endActs.contains(e.getActivity()))
				selEvents.add(e);
		});
		return selEvents;
	}

	public List<Event> getActEvents(String act) {
		List<Event> subEvents = new CopyOnWriteArrayList<>();

		this.events.parallelStream().forEach(e -> {

			if (e.getActivity().toLowerCase().equals(act.toLowerCase()))
				subEvents.add(e);
		});

		return subEvents;
	}

	public void updateEventCIds(List<String> vars) {
		List<String> vars_ = new CopyOnWriteArrayList<>(vars);
		for (String v : vars_) {
			String[] arrs = v.split("_");
			int eIndex = Integer.parseInt(arrs[1].split("e")[1]);
			String cId1 = arrs[0].split("[.]")[1];
			this.events.get(eIndex).setCaseId(cId1);
		}
	}

	public void updateEventCIds(ConcurrentHashMap<String, Integer> vars) {
		// TODO Auto-generated method stub
		for (String v : vars.keySet()) {
			int eIndex = Integer.parseInt(v.split("e")[1]);
			this.events.get(eIndex).setCaseId(vars.get(v) + "");
		}
	}

}
