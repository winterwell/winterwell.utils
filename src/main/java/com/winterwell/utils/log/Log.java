package com.winterwell.utils.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.winterwell.datalog.Rate;
import com.winterwell.utils.Environment;
import com.winterwell.utils.IFilter;
import com.winterwell.utils.IFn;
import com.winterwell.utils.Key;
import com.winterwell.utils.Printer;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Cache;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.time.RateCounter;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

/**
 * Yet another logging system. We use Android LogCat style commands, e.g.
 * <code>Log.e(tag, message)</code> to report an error.
 * <p>
 * Why?<br>
 * Simpler than Log4J, but without features such as "crashes when it fails to
 * find it's config file" or "classpath settings can cause versions to conflict and kill your JVM".
 * It's not that this is better than log4j / slf4j / java logging / etc., it's that it's small, simple and
 * never breaks.
 *
 * @testedby  LogFileTest
 * @author daniel
 */
public class Log {
	
	/**
	 * tag = calling class's name
	 * @param msg
	 */
	public static void d(Object msg) {
		String cn = ReflectionUtils.getCallingClassSimpleName(1);
		d(cn, msg);
	}
	/**
	 * tag = calling class's name
	 * @param msg
	 */
	public static void i(Object msg) {
		String cn = ReflectionUtils.getCallingClassSimpleName(1);
		i(cn, msg);
	}
	
	/**
	 * An extra string which can be accessed by log listeners. Added to
	 * {@link LogFile}s. Usage: e.g. a high-level process wishes to include info
	 * in low-level reports.
	 */
	private static final Key<String> ENV_CONTEXT_MESSAGE = new Key<String>(
			"Log.context");

	private static Map<String,Level> sensitiveTags = new HashMap();

	private static ILogListener[] listeners = new ILogListener[0];

	/**
	 * Maximum length (in chars) of a single log report: 4k
	 */
	public static final int MAX_LENGTH = 1048 * 4;

	/**
	 * An important OK :)
	 */
	public static final Level SUCCESS = new Level("SUCCESS", Level.WARNING.intValue()-1) {};
	public static final Level WARNING = Level.WARNING;
	public static final Level ERROR = Level.SEVERE;
	public static final Level DEBUG = Level.FINE;
	public static final Level INFO = Level.INFO;
	public static final Level VERBOSE = Level.FINEST;
	public static final Level OFF = Level.OFF;	
	
	private static Level MIN_LEVEL = DEBUG;

	static {
		stdInit();
	}

	/**
	 * Listen for log reports
	 *
	 * @param listener
	 */
	public static synchronized void addListener(ILogListener listener) {
		assert listener != null;
		for (ILogListener l : listeners) {
			if (l.equals(listener))
				return;
		}
		listeners = Arrays.copyOf(listeners, listeners.length + 1);
		listeners[listeners.length - 1] = listener;
	}

	private static void stdInit() {
		// switch off logging during log init
		Level minLevel = MIN_LEVEL;
		setMinLevel(OFF);
		// Add a simple console output listener
		addListener(new SystemOutLogListener());

		// config
		try {
			config = ConfigFactory.get().getConfig(LogConfig.class);
			setConfig(config);
		} catch(Throwable ex) {
			// How can we report this bad config issue? Only to std-error :(
			System.err.println(ex);
		} finally {
			setMinLevel(minLevel);
		}
	}
	
	/**
	 * NB: using ConfigFactory here causes an error :( Dec 2021
	 */
	static LogConfig config = new LogConfig();

	private static IFilter<String> excludeFilter;

	/**
	 * Allows a config file change to downgrade severe reports (to stop unwanted alerts)
	 * @see LogConfig#downgrade
	 */
	private static IFilter<String> downgradeFilter;
	
	public static void setConfig(LogConfig config) {
		Log.config = config;
		sensitiveTags.clear();
		if (config.ignoretags!=null) {
			for(String tag : config.ignoretags) {
				setMinLevel(tag, OFF);
			}
		}
		if (config.verbosetags!=null) {
			for(String tag : config.verbosetags) {
				setMinLevel(tag, VERBOSE);
			}
		}
		// global minlevel?
		if (config.minLevel != null) {
			try {
				Log.i("log", "set global minLevel "+config.minLevel);				
				setMinLevel(Level.parse(config.minLevel));
			} catch(Throwable ex) {
				Log.e("log.swallow", ex);
			}
		}
		// no blank patterns!
		config.exclude = Containers.filter(config.exclude, x -> ! Utils.isBlank(x));
		if (Utils.isEmpty(config.exclude)) {
			excludeFilter = null;
		} else {
			excludeFilter = new IFilter<String>() {
				@Override
				public boolean accept(String x) {
					if (Utils.isBlank(x)) return false;
					for(String s : config.exclude) {
						if (x.contains(s)) return true;
					}
					return false;
				}
			};
		}
		// no blank patterns!
		config.downgrade = Containers.filter(config.downgrade, x -> ! Utils.isBlank(x));
		if (Utils.isEmpty(config.downgrade)) {
			downgradeFilter = null;
		} else {
			downgradeFilter = new IFilter<String>() {
				@Override
				public boolean accept(String x) {
					if (Utils.isBlank(x)) return false;
					for(String s : config.downgrade) {
						if (x.contains(s)) return true;
					}
					return false;
				}
			};
		}
		
		// colour sysout?
		if (config.useColor) {
			SystemOutLogListener ll = Containers.firstClass(Log.getListeners(), SystemOutLogListener.class);
			if (ll != null) {
				ll.setUseColor(config.useColor);
			}
		}
		// throttle cache??
		
		// all set (let's log that)
		Log.i("log", "setConfig "+ReflectionUtils.getSomeStack(10));
	}

	/**
	 * @return extra contextual message, or "" if unset
	 */
	static String getContextMessage() {
		String cm = Environment.get().get(Log.ENV_CONTEXT_MESSAGE);
		return cm == null ? "" : cm;
	}

	/**
	 * Get the minimum level to report events. Events with this level are
	 * reported. Events below this level are ignored.<br>
	 * Default: ignore verbose
	 * @param tag Can be null. You can set some tags to be extra sensitive
	 */
	public static Level getMinLevel(String tag) {
		if (tag!=null) {
			Level ml = sensitiveTags.get(tag);
			if (ml!=null) return ml;
		}
		return MIN_LEVEL;
	}

	/**
	 * @param listener Can be null
	 */
	public static synchronized void removeListener(ILogListener listener) {
		if (listener==null) return;
		ArrayList<ILogListener> ls = new ArrayList(Arrays.asList(listeners));
		ls.remove(listener);
		listeners = ls.toArray(new ILogListener[0]);
	}

	public static List<ILogListener> getListeners() {
		return Arrays.asList(listeners);
	}
	
	@Deprecated
	public static void report(Object msg) {
		if (!(msg instanceof Throwable)) {
			report(msg, Level.WARNING);
		} else {
			report((Throwable) msg);
		}
	}

	@Deprecated
	public static void report(Object msg, Level error) {
		report(null, msg, error, null);
	}

	/**
	 * This is the "master" version of this method (to which the others delegate
	 * - so perhaps that makes it more the servant method?).
	 * <p>
	 * It should never throw an exception. Any exceptions will be swallowed.
	 *
	 * @param tag
	 *            Inspired by Android's LogCat. The tag is a rough
	 *            classification on the report, which allows for
	 *            simple-but-effective filtering. Can be null
	 * @param msg
	 * @param level
	 */
	static void report(String tag, Object msg, Level level, Throwable ex) {
		// Ignore?
		Level minLevel = getMinLevel(tag);
		if (minLevel.intValue() > level.intValue()) {
			return;
		}
		// stochastic (off by default)
		if (config.keep > 0 && config.keep < 1) {
			if ( ! Utils.getRandomChoice(config.keep)) return;
		}		
		// null tag? Put in the calling class.method
		if (tag == null) {
			StackTraceElement ste = ReflectionUtils.getCaller(Log.class
					.getName());
			tag = ' ' + ste.toString(); // add a space from the # to make these
										// clickable from the Eclipse console
		}
		// throttle?
		if (throttle(tag)) {
			return; // throttled!
		}
		
		// exclude or downgrade by tag? (before we do the work of making a report)
		if (excludeFilter!=null) {
			// tag or message ??should this be report.toString()
			if (excludeFilter.accept(tag)) {
				return;
			}
		}		
		if (downgradeFilter!=null && level.intValue() > Level.INFO.intValue()) {
			if (downgradeFilter.accept(tag)) {
				level = Level.INFO;
			}
		}
		
		// Message
		String msgText = Printer.toString(msg);
		String details = null;
		// Exception? Add in some stack
		if (msg instanceof Throwable) {
			details = Printer.getStackTrace((Throwable)msg);
			details = StrUtils.ellipsize(details, MAX_LENGTH);
			if (ex==null) ex = (Throwable) msg;
		}
		// Guard against giant objects getting put into log, which is almost
		// certainly a careless error
		if (msgText.length() > MAX_LENGTH) {
			msgText = msgText.substring(0, MAX_LENGTH - 100)
					+ "... (message is too long for Log!)";
		}
		
		// make a Report
		Report report = new Report(tag, msgText, level, details, ex);
		
		// exclude or downgrade by full report?
		String sreport = null;
		if (excludeFilter!=null) {
			sreport = report.toString();
			if (excludeFilter.accept(sreport)) {
				return;
			}
		}		
		if (downgradeFilter!=null && level.intValue() > Level.INFO.intValue()) {
			if (sreport != null) sreport = report.toString();
			if (downgradeFilter.accept(sreport)) {
				report = new Report(tag, msgText, Level.INFO, details, ex);
			}
		}
				
		// Note: using an array for listeners avoids any concurrent-mod
		// exceptions
		for (ILogListener listener : listeners) {
			try {
				listener.listen(report);
			} catch (Throwable ex2) {
				// swallow if something goes wrong
				ex2.printStackTrace();
			}
		}
		// HACK escalate on error + #escalate?
		if (level==Level.SEVERE && msgText.contains("#escalate")) {
			escalate(new WeirdException("Escalating "+msgText));
		}
	}
	
	/**
	 * 
	 * @param tag
	 * @return true to silently swallow this tag
	 * This is to protect against log file bloat
	 */
	private static boolean throttle(String tag) {
		if (config==null) return false;
		if (config.throttleWindow==null) {
			return false;
		}
		Rate throttleAt = config.getThrottleAt(tag);
		if (throttleAt==null) return false;
		RateCounter rc = throttle.get(tag);
		if (rc==null) {
			rc = new RateCounter(config.throttleWindow);
			rc.setFirstDtOverride(true);
			throttle.put(tag, rc);
			return false;
		}
		// shall we?
		rc.plus(1);
		if (throttleAt.isGreaterThan(rc.rate())) {
			return false;
		}
		// first time? Or first time today?
		Time tat = throttledAt.get(tag);
		if (tat==null || tat.isBefore(new Time().minus(TUnit.DAY))) {
			if ( ! "throttle".equals(tag)) {
				Log.i("throttle", "Throttle (skip) log reports for tag #"+tag+" which is running at "+rc);
			}
			throttledAt.put(tag, new Time());
		}
		return true;
	}
	

	/**
	 * Throttle frequent messages
	 */
	static final Cache<String, RateCounter> throttle = new Cache<>(1000);
	static final Cache<String, Time> throttledAt = new Cache<>(100);

	@Deprecated
	public static void report(String tag, Object msg, Level error) {
		report(tag,msg,error,null);
	}
	
	@Deprecated
	public static void report(Throwable ex) {
		report(Printer.toString(ex, true), Level.SEVERE);
	}

	public static void setContextMessage(String message) {
		Environment.get().put(Log.ENV_CONTEXT_MESSAGE, message);
	}

	/**
	 * Set *default* minimum level to report events. Applies across all threads.
	 *
	 * @param level
	 *            DEBUG by default. Use Level.ALL to show everything. Events equal to or above this are reported.
	 */
	public static void setMinLevel(Level level) {
		assert level != null;
		MIN_LEVEL = level;
	}


	/**
	 * For pain-level debugging.
	 * <p>
	 * This prints out (via .v()):<br>
	 * class.method(file:linenumber): objects<br>
	 * It does so in a format which can be copied-and-pasted into Eclipse's Java
	 * Stack Trace Console, where it will gain a link to the line of code.
	 * <p>
	 * Uses Level.FINEST -- which is ignored by default!!
	 *
	 * @param objects
	 *            Optional. These will be printed out. Can be empty.
	 */
	public static void trace(Object... objects) {
		if (MIN_LEVEL.intValue() > Level.FINEST.intValue())
			return;
		StackTraceElement caller = ReflectionUtils.getCaller();
		Log.v(caller.getClass().getSimpleName(), caller.getMethodName() + ": "
				+ Printer.toString(objects));
	}

	/**
	 * Does nothing. Provides an object if you need one - but all the methods
	 * are static.
	 */
	public Log() {
		// does nothing
	}

	/**
	 * Add a log message for a warning. Use Log.e for genuine errors.
	 * @param tag
	 * @param msg
	 */
	public static void w(String tag, Object msg) {
		report(tag, msg, Level.WARNING, null);
	}

	/**
	 * Add a Log message on error.
	 * @param tag
	 * @param msg - Note that msg here, can be a Throwable, and you'll get some stack
	 */
	public static void e(String tag, Object msg) {
		report(tag, msg, Level.SEVERE, null);
	}

	/**
	 * @deprecated
	 * This one logs the stack-trace too.
	 * @param tag
	 * @param msg
	 * @param t
	 */
	public static void st(String tag, Throwable t){
		report(tag + ".stacktracelog", Printer.toString(t, true), WARNING, t);
	}


	public static void i(String tag, Object msg) {
		report(tag, msg, INFO, null);
	}

	/**
	 * A debug report (uses Level.FINE)
	 *
	 * @param tag
	 * @param msg
	 */
	public static void d(String tag, Object msg) {
		report(tag, msg, DEBUG);
	}

	/**
	 * A verbose report (uses Level.FINEST -- which is ignored by default)
	 *
	 * @param tag
	 * @param msg
	 */
	public static void v(String tag, Object msg) {
		report(tag, msg, VERBOSE);
	}

	public static void v(String tag, Object... items) {
		report(tag, items, VERBOSE);
	}

	
	// use i()
	public static void info(String string) {
		i(null, string);
	}

	@Deprecated
	// use w()
	public static void warn(String string) {
		w(null, string);
	}

	public static String stackToString(Throwable throwable){
		StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
	}

	/**
	 * @deprecated
	 * Replace {class} and {method} with values obtained from reflection lookups.
	 * Convenience method for easy creation of log messages.
	 * <p>
	 * Note: This is not normally called by default (for performance & predictability).
	 *
	 * @param s Can be null (returns null)
	 * @return s'
	 */
	public static String format(String s) {
		if (s==null) return s;
		StackTraceElement c = ReflectionUtils.getCaller(Log.class.getName(), StrUtils.class.getName(), Printer.class.getName());
		String cn = c.getClassName();
		int i = cn.lastIndexOf('.');
		String sn = i==-1? cn : cn.substring(i+1);
		ArrayMap vars = new ArrayMap(
				"class", sn,
				"method", c.getMethodName());
		return Printer.format(s, vars);
	}

	/**
	 * By default, this throws the error!
	 * But you can override it to do something less drastic.
	 * <p>
	 * Example use-case: In development, you might throw errors, then in production you might handle things via logging/reporting.
	 * @param error
	 */
	public static void escalate(Throwable error) {
		if (error==null) return;
		try {
			ESCALATOR.apply(error);
		} catch (Exception e) {
			throw Utils.runtime(e);
		}
	}
	
	static IFn<Throwable,Object> ESCALATOR = new ThrowIt();

	/**
	 * Change how {@link #escalate(Throwable)} functions.
	 * @param escalator
	 */
	public static void setEscalator(IFn<Throwable, Object> escalator) {
		ESCALATOR = escalator;
	}

	public static void setMinLevel(String tag, Level level) {
		// thread safe put
		HashMap map = new HashMap(sensitiveTags);
		map.put(tag, level);
		sensitiveTags = map;
	}
	
	public static LogConfig getConfig() {
		return config;
	}
	public static void w(Object warning) {
		String cn = ReflectionUtils.getCallingClassSimpleName(1);
		w(cn, warning);
	}
	public static void e(Object warning) {
		String cn = ReflectionUtils.getCallingClassSimpleName(1);
		e(cn, warning);
	}
	/**
	 * @deprecated Prefer Log.e() This is here for drop-in compatability with SLF4J
	 * @param string
	 */
	public static void error(String msg, Object... objects) {
		if (objects.length > 0) {
			int i = msg.indexOf("{}");
			int j = 0;
			while(i != -1 && j < objects.length) {
				msg = msg.substring(0, i)+objects[j].toString()+msg.substring(i+2);	
				i = msg.indexOf("{}");
				j++;
			}			
		}
		e(msg);
	}
	

}

class ThrowIt implements IFn<Throwable,Object> {

	@Override
	public Object apply(Throwable value) {
		throw Utils.runtime(value);
	}
	
}
