package com.winterwell.utils.time;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.winterwell.utils.MathUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;

/**
 * String -> Time code.
 * 
 * TODO refactor this, with options and stuff. Maybe even support other languages.
 * 
 * @author daniel
 * @testedby {@link TimeParserTest}
 */
public class TimeParser {	
	
	boolean preferEnd;
	
	public TimeParser setPreferEnd(boolean preferEnd) {
		this.preferEnd = preferEnd;
		return this;
	}
	
	/**
	 * Parse a string representing a time/date. Uses the
	 * {@link SimpleDateFormat} format.
	 * <p>
	 * If the pattern does *not* contain a timezone (Z), this method will
	 * enforce GMT. Otherwise Java can get cute and use summer time.
	 * <p>
	 * If the pattern does *not* contain a year (y), this method will set the current year.
	 * <p>
	 * If calling this method a lot, you may want to use
	 * {@link SimpleDateFormat} directly to avoid reparsing the pattern.
	 * 
	 * @param string
	 * @param format
	 *            E.g. "HH:mm:ss dd/MM/yyyy" See {@link SimpleDateFormat} for
	 *            details.
	 *            <table border=0 cellspacing=3 cellpadding=0 summary= * *
	 *            "Chart shows pattern letters, date/time component, presentation, and examples."
	 *            * * >
	 *            <tr bgcolor="#ccccff">
	 *            <th align=left>Letter <th align=left>Date or Time Component
	 *            <th align=left>Presentation <th align=left>Examples
	 *            <tr>
	 *            <td><code>G</code> <td>Era designator <td>Text <td><code>AD
	 *            </code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>y</code> <td>Year <td>Year <td><code>1996</code>;
	 *            <code>96</code>
	 *            <tr>
	 *            <td><code>M</code> <td>Month in year <td>Month <td><code>July
	 *            </code>; <code>Jul</code>; <code>07</code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>w</code> <td>Week in year <td>Number <td><code>27
	 *            </code>
	 *            <tr>
	 *            <td><code>W</code> <td>Week in month <td>Number <td><code>2
	 *            </code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>D</code> <td>Day in year <td>Number <td><code>189
	 *            </code>
	 *            <tr>
	 *            <td><code>d</code> <td>Day in month <td>Number <td><code>10
	 *            </code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>F</code> <td>Day of week in month <td>Number <td>
	 *            <code>2</code>
	 *            <tr>
	 *            <td><code>E</code> <td>Day in week <td>Text <td><code>Tuesday
	 *            </code>; <code>Tue</code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>a</code> <td>Am/pm marker <td>Text <td><code>PM
	 *            </code>
	 *            <tr>
	 *            <td><code>H</code> <td>Hour in day (0-23) <td>Number <td>
	 *            <code>0</code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>k</code> <td>Hour in day (1-24) <td>Number <td>
	 *            <code>24</code>
	 *            <tr>
	 *            <td><code>K</code> <td>Hour in am/pm (0-11) <td>Number <td>
	 *            <code>0</code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>h</code> <td>Hour in am/pm (1-12) <td>Number <td>
	 *            <code>12</code>
	 *            <tr>
	 *            <td><code>m</code> <td>Minute in hour <td>Number <td><code>30
	 *            </code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>s</code> <td>Second in minute <td>Number <td><code>
	 *            55</code>
	 *            <tr>
	 *            <td><code>S</code> <td>Millisecond <td>Number <td><code>978
	 *            </code>
	 *            <tr bgcolor="#eeeeff">
	 *            <td><code>z</code> <td>Time zone <td>General time zone <td>
	 *            <code>Pacific Standard Time</code>; <code>PST</code>; <code>
	 *            GMT-08:00</code>
	 *            <tr>
	 *            <td><code>Z</code> <td>Time zone <td>RFC 822 time zone <td>
	 *            <code>-0800</code>
	 *            </table>
	 * @return
	 * @tesedby {@link TimeUtilsTest}
	 */
	public Time parse(String string, String pattern) {
//		if (pattern.contains("h")) {
//			throw new IllegalArgumentException("h is a tricksy bastard - you probably want H in "+ pattern);
//		}
		try {
			SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
			TimeZone zone = TimeZone.getTimeZone("GMT");
			format.setTimeZone(zone);
			Date date = format.parse(string);
			Time t = new Time(date);
			// handle patterns without a year -- assume this year
			if ( ! pattern.contains("y")) {
				GregorianCalendar cal = t.getCalendar();
				cal.set(GregorianCalendar.YEAR, new Time().getYear());
				Time t2 = new Time(cal);
				t = t2;
			}
			return t;
		} catch (ParseException e) {
			throw new IllegalArgumentException(string
					+ " did not match pattern " + pattern, e);
		}
	}

	/**
	 * @param dt
	 *            e.g. 10 minutes. Ignores +/- indicators such as "ago" or
	 *            "hence".
	 * @return always positive
	 * @testedby  TimeUnitTest#testParseDt()}
	 */
	public Dt parseDt(String dt) throws IllegalArgumentException {
		// trim and lower case
		dt = dt.trim().toLowerCase();
		Pattern delay = Pattern
				.compile("(a|[\\d\\.]+)?\\s*\\b(year|month|week|day|hour|hr|minute|min|second|sec)s?\\b");
		String[] bits = StrUtils.find(delay, dt);
		if (bits == null)
			throw new IllegalArgumentException("Could not parse dt-spec: " + dt);
		Double val = bits[1] == null || "a".equals(bits[1]) ? 1 : Double
				.valueOf(bits[1]);
		String us = bits[2];
		TUnit unit = parseDt2_unit(us);
		return new Dt(val, unit);
	}

	private TUnit parseDt2_unit(String us) {
		// try the enum
		try {
			TUnit unit = TUnit.valueOf(us.toUpperCase());
			assert unit != null;
			return unit;
		} catch (IllegalArgumentException e) {
			// ignore
		}
		if (us.equals("min"))
			return TUnit.MINUTE;
		if (us.equals("sec"))
			return TUnit.SECOND;
		throw new IllegalArgumentException(us);
	}

	/**
	 * Experimental handling of time strings
	 * 
	 * @param s
	 * @return
	 * @testedby  TimeUtilsTest#testParseExperimental()}
	 */
	public Time parseExperimental(String s) throws IllegalArgumentException {
		return parseExperimental(s, null);
	}
	
	/**
	 * 
	 * @param s
	 * @param isRelative Can be null. If provided, will be set to true for relative times, such as "today" or "yesterday"
	 * @return
	 * 
	 * TODO it'd be nice to use TimeFragment here
	 */
	public Time parseExperimental(String s, AtomicBoolean isRelative) throws IllegalArgumentException {
		Period period = parsePeriod(s, isRelative);
		return period.first;
	}
	
	

	static final SimpleDateFormat[] formats = initFormats();
	static TUnit[] formats_dt;

	private static SimpleDateFormat[] initFormats() {
		List<SimpleDateFormat> _formats = new ArrayList();
		List<TUnit> _formats_dt = new ArrayList();
		
		// formats[0] -- the "canonical" format
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssZ")); // ISO 8601, with milliseconds.
		_formats_dt.add(TUnit.MILLISECOND);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'")); // WTF, Java doesn't parse Z properly?
		_formats_dt.add(TUnit.MILLISECOND);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss Z")); // ISO 8601, with milliseconds.
		_formats_dt.add(TUnit.MILLISECOND);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")); // ISO 8601, with seconds.
		_formats_dt.add(TUnit.SECOND);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")); // WTF, Java doesn't parse Z properly?
		_formats_dt.add(TUnit.MINUTE);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ")); // ISO 8601, without seconds.
		_formats_dt.add(TUnit.MINUTE);		
		_formats.add(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss Z"));
		_formats_dt.add(TUnit.SECOND);
		_formats.add(new SimpleDateFormat("dd/MM/yyyy HH:mm Z"));
		_formats_dt.add(TUnit.MINUTE);
		try {
			Class<?> mdf = Class.forName("jakarta.mail.internet.MailDateFormat");
			Object mdfi = mdf.newInstance();
			_formats.add((SimpleDateFormat) mdfi);
			_formats_dt.add(TUnit.SECOND); // pattern is EEE, d MMM yyyy HH:mm:ss Z (z)
		} catch (Exception ex) {
			// oh well
		}
		// This is for YahooSearch
		// UK standard format (2-digit year first, otherwise they get misread)
		_formats.add(new SimpleDateFormat("dd/MM/yy"));
		_formats_dt.add(TUnit.DAY);
		_formats.add(new SimpleDateFormat("dd/MM/yyyy"));
		_formats_dt.add(TUnit.DAY);
		// US standard
		_formats.add(new SimpleDateFormat("MM/dd/yy"));
		_formats_dt.add(TUnit.DAY);
		// year first "computer friendly" 
		_formats.add(new SimpleDateFormat("yyyy/MM/dd"));
		_formats_dt.add(TUnit.DAY);
		_formats.add(new SimpleDateFormat("MMM yyyy"));
		_formats_dt.add(TUnit.MONTH);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
		_formats_dt.add(TUnit.SECOND);
		_formats.add(new SimpleDateFormat("yyyy-MM-dd")); // that's a shit format (homeserve "rss" feed)
		_formats_dt.add(TUnit.DAY);
		
		// assume GMT! And 2000!
		TimeZone zone = TimeZone.getTimeZone("GMT");
		for (SimpleDateFormat df : _formats) {
			try {
				df.setTimeZone(zone);
				// 21st century
				Date c = df.get2DigitYearStart();
				df.set2DigitYearStart(new Time(2000,1,1).getDate());
			} catch(Exception ex) {
				// MDF throws an exception if 2digityearstart is touched!
			}
		}
		formats_dt = _formats_dt.toArray(new TUnit[0]);
		assert formats_dt.length == _formats.size();
		return _formats.toArray(new SimpleDateFormat[0]);
	}

	Time now = new Time();
	
	public void setNow(Time now) {
		this.now = now;
	}
	
	String timezone;
	
	/**
	 * yyyy-MM-dd -- be careful to sanity check values
	 */
	static final Pattern pISODATE = Pattern.compile("^(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)");
	/**
	 * lowercase 3 letter month codes
	 */
	public static final Pattern pMONTH = Pattern.compile("jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec");
	static final Pattern pDAY = Pattern.compile("sun|mon|tue|wed|thu|fri|sat");
	/**
	 * TODO break this out into a TimeParser class so we can have language & timezone support.
	 * WARNING: will return a point time (a 0-length period) for many cases
	 * @param s a period or a time. Anything really, but only English.
	 * @param isRelative
	 * @return
	 * @throws IllegalArgumentException
	 */
	public Period parsePeriod(String s, AtomicBoolean isRelative) throws IllegalArgumentException {
		// split?
		if (s.contains(" to ")) {
			String[] bits = s.split(" to ");
			Time t0 = parseExperimental(bits[0], isRelative);
			Time t1 = parseExperimental(bits[1], isRelative);
			return new Period(t0, t1);
		}
				
		s = s.trim().toLowerCase();
		// standard?
		try {
			Time t = new Time(s);
			
			// HACK: was it a day without a time?
			if (parsePeriod2_isWholeDay(s, t)) {
				return new Period(t, TimeUtils.getEndOfDay(t));
			}
			
			return new Period(t);
		} catch (Exception e) {
			// oh well
		}
		
		// TODO use this more
		TimeFragment tf = new TimeFragment();
		
		{	// Do we have an ISO date? (but a non-ISO time) e.g. "yyyy-MM-dd HH:mm" or "yyyy-MM-dd HH:mm:ss"		
			Matcher m = pISODATE.matcher(s);
			if (m.find()) {				
				int y = Integer.valueOf(m.group(1));
				int mm = Integer.valueOf(m.group(2));
				int d = Integer.valueOf(m.group(3));
				if (mm>0 && mm<13 && d>0 && d<32) {
					Time date = new Time(y,mm,d);
					tf.setDate(date);
				}
			}
		}

		// Use regexs to pick out markers for day, month, hour, dt
		// - build a time object based on what we find

		int year = -1;
		{ // year
			Matcher m = TimeUtils.YEAR.matcher(s);
			if (m.find()) {
				year = Integer.valueOf(m.group(1));
				// BC?
				if (m.group().contains("b")) {
					year = -year;
				}
				tf.put(Calendar.YEAR, year);
			} else {
				// maybe a short year?
				Matcher sm = Pattern.compile("\\d\\d$").matcher(s);
				if (sm.find()) {
					year = 2000 + Integer.valueOf(sm.group());
					if (year > new Time().getYear() + 15) {
						year -= 1900; // 99 = 1999
					}
					tf.put(Calendar.YEAR, year);
				}
			}
		}

		String month = null;
		{ // month markers -- 3 letters is enough to id a month
			Matcher m = pMONTH.matcher(s);
			if (m.find()) {
				month = m.group();
				int mi = pMONTH.pattern().indexOf(month);
				int mii = mi / 4;
				tf.setMonth(mii+1); // 1=jan
			}
		}

		String day = null;
		{ // day of week			
			Matcher m = pDAY.matcher(s);
			if (m.find()) {
				day = m.group();
				// guard against mon = month false match
				if ("mon".equals(day) && s.contains("month")) {
					day = null;
					if (m.find()) {
						day = m.group();
					}
				}
				// to index
				if (day!=null) {
					int idow = pDAY.pattern().indexOf(day);
					int dow = idow / 4;
					// dow: sun = 0 
					// cal: sunday = 1, saturday=7
					tf.put(Calendar.DAY_OF_WEEK, dow+1);
				}
			}
		}
		
		Integer hour = null;
		{ // TODO hour:minute
			Pattern HOUR = Pattern.compile("(\\d\\d):(\\d\\d)|(\\d\\d?)am|(\\d\\d?)pm");
			Matcher m = HOUR.matcher(s);
			if (m.find()) {
				String hourMin = m.group();
				String g1 = m.group(1);
				String g2 = m.group(2);
				String g3 = m.group(3);
				String g4 = m.group(4);
				hour = Integer.valueOf(Utils.or(g1,g3,g4)); 
				if (g4!=null) {	// pm?
					hour += 12;					
				}
				tf.put(Calendar.HOUR_OF_DAY, hour);
			}
		}
		
		// put together a date
		if (month != null) {
			if (year==-1) {
				year = now.getYear();
				tf.setYear(year);
			}
			// look for a day of month
			Matcher m = Pattern.compile("\\d+").matcher(s);
			while (m.find()) {
				int dayMonth = Integer.parseInt(m.group());
				if (dayMonth == 0 || dayMonth > 31) {
					continue;
				}
				tf.put(Calendar.DAY_OF_MONTH, dayMonth);
				Time hm = tf.getTime();
				if (hm!=null) {
					if (tf.hasTime()) {
						return new Period(hm);
					}
					Time eod = TimeUtils.getEndOfDay(hm);
					return new Period(hm, eod);
				}
			}			
			if (day==null) {
				tf.put(Calendar.DAY_OF_MONTH, 1);
				Time t = tf.getTime();
				if (t != null) {
					Time eom = TimeUtils.getEndOfMonth(t.plus(TUnit.DAY));
					return new Period(t, eom);
				}
			}
		}

		// special strings
		if (s.equals("now")) {
			if (isRelative!=null) isRelative.set(true);
			return new Period(now);
		}
		if (s.equals("today")) {
			if (isRelative!=null) isRelative.set(true);
			return new Period(TimeUtils.getStartOfDay(now), TimeUtils.getEndOfDay(now));
		}
		if (s.equals("yesterday")) {
			if (isRelative!=null) isRelative.set(true);
			s = "1 day ago";
		}
		if (s.equals("tomorrow")) {
			if (isRelative!=null) isRelative.set(true);
			s = "1 day from now";
		}
		
		// HACK "start/end"
		Pattern p = Pattern.compile("^(start|end)?([\\- ]of[\\- ])?");
		Matcher m = p.matcher(s);
		String startEnd = null;
		if (m.find()) {
			startEnd = m.group(1);
			s = s.substring(m.end());
		}
		s = s.trim(); //paranoia
		// HACK last next
		if (s.startsWith("last")) {
			if (isRelative!=null) isRelative.set(true);
			if (day!=null) {
				Time lastDay = now;
				for(int i=0; i<7; i++) {
					lastDay = lastDay.minus(TUnit.DAY);
					String lday = lastDay.format("EEE");
					if (lday.toLowerCase().startsWith(day)) {
						return new Period(TimeUtils.getStartOfDay(lastDay), TimeUtils.getEndOfDay(lastDay));
					}
				}				
				return new Period(TimeUtils.getStartOfDay(lastDay), TimeUtils.getEndOfDay(lastDay));
			}
			// NB this handles "last week" and "last-week"
			s = "1 "+s.substring(5)+" ago";
		}
		if (s.startsWith("next")) {
			s = "1 "+s.substring(5)+" from now";
		}
		// a step spec, e.g. 1 week ago?
		try {
			Dt dt = parseDt(s);
			if (isRelative!=null) isRelative.set(true);
			Time t;
			if (s.contains("ago")) {				
				t = now.minus(dt);
			} else if (s.contains("this") || s.equals("month")) {
				// HACK test for "this month" or "end-of-week"
				// no-op
				t = now;
			} else if (s.equals("week")) {
				// HACK
				t = now;
				KDay dow = TimeUtils.getDayOfWeek(t);
				int dn = dow.ordinal();
				Time t2;
				if ("start".equals(startEnd)) {
					t2 = TimeUtils.getStartOfDay(t.minus(dn, TUnit.DAY));
				} else {
					t2 = TimeUtils.getEndOfDay(t.plus(7 - dn, TUnit.DAY));			
				}
				return new Period(t2);
			} else {
				t = now.plus(dt);
			}
			if (startEnd==null) return new Period(t);
			// TODO don't assume month -- also handle "start of last week"
			Time t2 = TimeUtils.getStartOfMonth(t);
			if ("start".equals(startEnd)) {
				return new Period(t2);
			} else {
				Time t3 = t2.plus(TUnit.MONTH).minus(TUnit.MILLISECOND);
				return new Period(t3);
			}
		} catch (Exception e) {
			// oh well
		}		
			
		// a time? e.g. "7pm", "7pm today"??
		// TODO an actual time description, like "Monday 1st, December 1968"

		// what do we have?
		if (tf.numset() > 1) {
			Time tft = tf.getTime();
			if (tft!=null) return new Period(tft);
		}

		// parse failed
		throw new IllegalArgumentException(s);
	}

	private boolean parsePeriod2_isWholeDay(String s, Time t) {
		if ( ! t.equals(TimeUtils.getStartOfDay(t))) {
			return false;
		}
		if (MathUtils.isNumber(s)) return false;
		// HACK: do we have an hour:minute part or other time marker?
		if (s.contains(":") || s.contains("am") || s.contains("pm")) {
			return false;
		}
		// HACK: ad hoc markers
		if (s.contains("start")) return false;
		// no time - so treat as whole day
		return true;
	}

	public Time parse(String v) {
		return parse2(v, null);
	}
	
	/**
	 * Attempts to parse a string to a date/time as several standard formats, returns null for specific malformed strings similar to "+0000", and finally attempts natural-language parsing.
	 * @param v A String which should represent a date/time.
	 * @param isRelative True if the String represents a time relative to now
	 * @return A Time object on successful parsing, or null for strings similar to "+0000"
	 */
	public Time parse2(String v, AtomicBoolean isRelative) {
		assert isRelative==null || ! isRelative.get() : v;
		// UTC milliseconds code?
		if (StrUtils.isInteger(v)) {
			return new Time(Long.parseLong(v));
		}
		for(int i=0; i<formats.length; i++) { 
			try {
				SimpleDateFormat df = formats[i];
				// NOTE: SimpleDateFormat.parse and SimpleDateFormat.format
				// are not thread safe... hence the .clone
				// (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4228335)
				SimpleDateFormat df2 = (SimpleDateFormat) df.clone();
				String patternForDebug = df2.toPattern();
				// Don't use heuristics to interpret inputs that don't exactly match the format.
				df2.setLenient(false);
				
				Date date = df2.parse(v);
				if (date==null) continue; // WTF?! Happens.
				// NB: includes timezone				
								
				Time t = new Time(date);

				// Is this a date-only format, and do we have preferEnd set??
				if (preferEnd) {
					TUnit dt = formats_dt[i];
					if (dt.millisecs > TUnit.HOUR.millisecs) {
						Time endt = t.plus(dt);
						return endt;
					}
				}
				
				return t;
			} catch (Exception e) {
				// oh well - try something else
			}
		}

		// catch malformed strings with a time zone and no date/time & return null
		if(v.matches("^[+-]\\d\\d\\d\\d\\W*$")) {
			return null;
		}
		
		// support for e.g. "yesterday"
		Period t = TimeUtils.parsePeriod(v, isRelative);
		// start/end of month
		return preferEnd? t.getEnd() : t.getStart();
	}



}
