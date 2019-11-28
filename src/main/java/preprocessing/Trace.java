package preprocessing;

import java.util.ArrayList;

public class Trace {

	ArrayList<String> activities;
	int nCases;

	public Trace(ArrayList<String> activities, int nCases) {
		super();
		this.activities = activities;
		this.nCases = nCases;
	}

	/**
	 * @return the activities
	 */
	public ArrayList<String> getActivities() {
		return activities;
	}

	/**
	 * @param activities
	 *            the activities to set
	 */
	public void setActivities(ArrayList<String> activities) {
		this.activities = activities;
	}

	/**
	 * @return the nCases
	 */
	public int getnCases() {
		return nCases;
	}

	/**
	 * @param nCases
	 *            the nCases to set
	 */
	public void setnCases(int nCases) {
		this.nCases = nCases;
	}

	@Override
	public boolean equals(Object obj) {
		Trace t2 = (Trace) obj;
		if (this.nCases == t2.nCases && this.activities.equals(t2.activities))
			return true;

		return false;
	}

	public boolean SameTrace(Trace t2) {
		if (this.activities.equals(t2.activities))
			return true;

		return false;
	}
}
