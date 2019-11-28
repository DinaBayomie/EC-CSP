package preprocessing;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.opencsv.bean.CsvBindByName;

public class Event implements Comparable<Event> {

	@CsvBindByName(column = "Activity")
	String activity;
	@CsvBindByName(column = "Timestamp")
	String timestamp;
	// @CsvBindByName(column = "Event Id")
	int id;
	@CsvBindByName(column = "CaseId")
	String caseId;
	double time = -1;
	Date timestampD;

	public Event() {
	}

	public Event(int id, String activity, String timestamp) throws ParseException {
		this.activity = activity;
		this.timestamp = timestamp;
		this.id = id;
		this.caseId = "-1";
	}
	public Event(Event e2)  {
		this.activity = e2.activity;
		this.timestamp = e2.timestamp;
		this.id = e2.id;
		this.caseId = e2.caseId;
	}

	public Event(String activity, String timestamp) {
		this.activity = activity;
		this.timestamp = timestamp;
		// this.id = id;
		this.caseId = "-1";
	}

	public double getTime() {
		if (time == -1)
			time = Double.parseDouble(this.timestamp);
		return time;
	}

	public String getActivity() {
		return activity;
	}

	public void setActivity(String activity) {
		this.activity = activity;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public String getCaseId() {
		if(this.caseId==null)
			return "-1";
		return this.caseId;
	}

	public void setCaseId(String caseId) {
		this.caseId = caseId;
	}

	public Date getTimestampD() {
		if (timestampD == null)
			this.timestampD = this.formatTimestamp(this.timestamp);
		return timestampD;
	}

	/** Id acts as an index of the event */
	public int getId() {
		return id;
	}

	/** Id acts as an index of the event */
	public void setId(int id) {
		this.id = id;
	}

	public void printEvent() {
		if (this.caseId == null)
			System.out.println(this.id + ":" + this.timestamp.toString() + ":" + this.activity);
		else
			System.out.println(this.caseId + ":" + this.id + ":" + this.timestamp + ":" + this.activity);
	}

	@Override
	public boolean equals(Object e) {
		Event e2 = (Event) e;
		if (this.id == e2.id && this.timestamp.equals(e2.timestamp) && this.activity.equals(e2.activity))
			return true;
		else if (this.timestamp.equals(e2.timestamp) && this.activity.equals(e2.activity))
			return true;
		else if (this.activity.equals(e2.activity))
			return true;
		return false;
	}

	private Date formatTimestamp(String timestamp) {
		List<String> formatStrings = Arrays.asList("yyyy/m/d h:m:s", "yyyy/M/d H:m:s", "yyyy/M/D H:m:s",
				"yyyy/M/d HH:mm:ss", "d/m/yyyy HH:mm", "dd/mm/yyyy hh:mm", "dd/mm/yyyy hh:mm:ss", "mm/dd/yyyy HH:mm:ss",
				"m/d/yyyy HH:mm", "d/m/yyyy HH:mm:ss", "d/m/yyyy HH:mm", "m-d-yyyy HH:mm:ss", "yyyy-m-d HH:mm:ss",
				"m-d-yyyy HH:mm", "yyyy-m-d HH:mm", "yyyy/m/d HH:mm");

		for (String format : formatStrings) {
			try {
				Date temp = new SimpleDateFormat(format).parse(timestamp);
				return new SimpleDateFormat(format).parse(timestamp);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
			}
		}

		return null;
	}

	@Override
	public int compareTo(Event o) {
		// return this.getTimestampD().compareTo(o.getTimestampD());
		Integer i = new Integer(this.getId());
		Integer j = new Integer(o.getId());
		return i.compareTo(j);
	}

@Override
public String toString() {
StringBuilder e=new StringBuilder();
e.append(this.caseId+":");
e.append(this.id+":");
e.append(this.activity);

return e.toString();
}
}
