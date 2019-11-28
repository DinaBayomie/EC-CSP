package evaluation;

import java.util.List;

import preprocessing.*;

public class TraceDistance implements Comparable<TraceDistance> {

	int distance;
	List<String> origTrace;
	List<String> gTrace;
	int tracesLengths;
	int optCost = Integer.MAX_VALUE;
	int commonCases;

	String caseIdO;
	String caseIdG;

	public TraceDistance(int distance, List<String> orgiTrace, List<String> gTrace) {
		super();
		this.distance = distance;
		this.origTrace = orgiTrace;
		this.gTrace = gTrace;
		this.tracesLengths = Math.max(this.origTrace.size(), this.gTrace.size());
	}

	public TraceDistance(int distance, String oCId, String gCId, List<String> orgiTrace, List<String> gTrace) {
		super();
		this.distance = distance;
		this.origTrace = orgiTrace;
		this.gTrace = gTrace;
		this.caseIdO = oCId;
		this.caseIdG = gCId;
		this.tracesLengths = Math.max(this.origTrace.size(), this.gTrace.size());
	}

	public TraceDistance(int distance, List<String> orgiTrace, List<String> gTrace, int commonCases) {
		super();
		this.distance = distance;
		this.origTrace = orgiTrace;
		this.gTrace = gTrace;
		this.tracesLengths = this.origTrace.size() + this.gTrace.size();
		this.commonCases = commonCases;
		this.optCost = commonCases * this.distance;
	}

	public int tracesLength() {
		return this.tracesLengths;
	}

	/**
	 * @return the distance
	 */
	public int getDistance() {
		return distance;
	}

	/**
	 * @param distance
	 *            the distance to set
	 */
	public void setDistance(int distance) {
		this.distance = distance;
	}

	/**
	 * @return the origTrace
	 */
	public List<String> getOrigTrace() {
		return origTrace;
	}

	/**
	 * @param origTrace
	 *            the origTrace to set
	 */
	public void setOrigTrace(List<String> orgiTrace) {
		this.origTrace = orgiTrace;
	}

	/**
	 * @return the gTrace
	 */
	public List<String> getgTrace() {
		return gTrace;
	}

	/**
	 * @param gTrace
	 *            the gTrace to set
	 */
	public void setgTrace(List<String> gTrace) {
		this.gTrace = gTrace;
	}

	@Override
	public int compareTo(TraceDistance td2) {
		if (this.optCost < td2.optCost)
			return -1;
		else if (this.optCost == td2.optCost)
			return 0;
		else
			return 1;
	}
	// public int compareTo(TraceDistance td2) {
	// if (this.distance < td2.distance)
	// return -1;
	// else if (this.distance == td2.distance)
	// return 0;
	// else
	// return 1;
	// }

	@Override
	public boolean equals(Object e) {
		TraceDistance td2 = (TraceDistance) e;
		if (this.origTrace.equals(td2.getOrigTrace()) && this.gTrace.equals(td2.getgTrace())
				&& this.distance == td2.distance)
			return true;
		return false;
	}
}
