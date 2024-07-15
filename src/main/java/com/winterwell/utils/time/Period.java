/**
 *
 */
package com.winterwell.utils.time;

import com.winterwell.utils.containers.Pair;

/**
 * A specific time-period - this is just a pair of Times with some convenience
 * methods. immutable
 * 
 * @testedby  PeriodTest}
 * @author daniel
 */
public final class Period extends Pair<Time> {
	private static final long serialVersionUID = 1L;

	/**
	 * @param dt
	 *            Must be +ive
	 * @return a period that starts now
	 */
	public static Period future(Dt dt) {
		Time now = new Time();
		assert dt.getMillisecs() >= 0;
		return new Period(now, now.plus(dt));
	}

	/**
	 * @param dt
	 *            Can be +ive of -ive -- the abs value is used.
	 * @return a period that ends now
	 */
	public static Period past(Dt dt) {
		Time now = new Time();
		Time then = new Time(now.getTime() - Math.abs(dt.getMillisecs()));
		return new Period(then, now);
	}

	public Period(Iterable<Time> period) {
		super(period);
		assert !first.isAfter(second) : first + " " + second;
	}

	public Period(String start, String end, String pattern) {
		this(TimeUtils.parse(start, pattern), TimeUtils.parse(end, pattern));
	}

	/**
	 * @param start If null, will use a WELL_OLD backstop
	 * @param end If null, will use a WELL_FUTURE backstop
	 */
	public Period(Time start, Time end) {
		super(start==null? TimeUtils.WELL_OLD : start, 
				end==null? TimeUtils.WELL_FUTURE : end);
		assert ! first.isAfter(second) : start + " " + end;
	}

	/**
	 * A 0-length period ie. a time.
	 * @param t
	 */
	public Period(Time t) {
		super(t, t);
	}

	/**
	 * @deprecated Confusing as it overlaps with the super Collection.contains(). Use within() instead.
	 * Is t inside this range? Includes the end values.
	 */
	public boolean contains(Time t) {
		return within(t);
	}
	
	/**
	 * Is t inside this range? Includes the end values.
	 */
	public boolean within(Time t) {
		return t.longValue() >= first.longValue()
				&& t.longValue() <= second.longValue();
	}

	/**
	 * the second Time
	 */
	public Time getEnd() {
		return second;
	}


	/**
	 * @return length start to end
	 * @see #getDt() which is equivalent but typed
	 */
	public long getMillisecs() {
		long ms = getEnd().getTime() - getStart().getTime();
		assert ms >= 0 : this;
		return ms;
	}
	
	/**
	 * @return length start to end
	 * @see #getMillisecs() which is equivalent but returns raw milliseconds
	 */
	public Dt getDt() {
		return getStart().dt(getEnd());
	}

	/**
	 * the first Time
	 */
	public Time getStart() {
		return first;
	}

	/**
	 * 
	 * @param period
	 * @return null if the intersection is empty. 
	 * Warning: Periods which only overlap at the start/end point (i.e. for an instant) will return a Period with start=end, dt=0! 
	 */
	public Period intersect(Period period) {
		Time s = first.isAfter(period.first) ? first : period.first;
		Time e = second.isBefore(period.second) ? second : period.second;
		if (s.isAfter(e)) {
			return null;
		}
		return new Period(s, e);
	}
	
	public boolean isWholeDay() {
		return ( first.diff(second) >= 86400000 ? true : false );
	}

	/**
	 * @return the length of this period in sensible units
	 */
	public Dt length() {
		return TimeUtils.fixUnits(first.diff(second, TUnit.MILLISECOND));
	}

	public double length(TUnit unit) {
		return first.diff(second, unit).getValue();
	}

	@Override
	public String toString() {
		String format1 = "d MMM y HH:mm";
		String format2 = "d MMM y HH:mm";
		if (first.getHour()==0 && first.getMinutes()==0) {
			format1 = "d MMM y";
		}
		if (second.getHour()==0 && second.getMinutes()==0) {
			format2 = "d MMM y";
		}
		if (length(TUnit.MINUTE) < 1) {
			format1 = "d MMM y HH:mm:ss";
			format2 = "HH:mm:ss";
		}		
		return first.format(format1) + " to " + second.format(format2);
	}

	public String toISOString() {
		return first.toISOString() + "/" + second.toISOString();
	}

	public Time getMiddle() {
		return new Time((getStart().getTime() + getEnd().getTime()) / 2);
	}

	/**
	 * Convenience for intersect != null && intersect.dt != 0
	 * @param slot
	 * @return true if they overlap
	 */
	public boolean intersects(Period slot) {
		Period i = intersect(slot);
		return i!=null && i.getMillisecs() > 0;
	}

}
