package com.winterwell.utils;

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.DoFast;
import com.winterwell.utils.time.Dt;

/**
 * An interrupter to interrupt the thread that starts this. This typically leads
 * to an {@link InterruptedException} being thrown. Use to set a timeout on
 * blocking operations.
 * <p>
 * Typical usage:
 * 
 * <pre>
 * <code>
 * TimeOut timeOut = new TimeOut(millisecs);
 * try {
 * 	// do stuff
 * } finally {
 * 	timeOut.cancel();
 * }
 * </code>
 * </pre>
 * 
 * Limitations: Some IO operations don't respond to interrupts! 
 * Also, it is possible for a thread to catch and ignore an interrupt.
 * 
 * @see DoFast
 * @testedby  TimeOutTest}
 * @author daniel
 */
public final class TimeOut implements Closeable {

	private static Timer timer = newTimer();
	private TimerTask task;
	private final long timeout;
	/**
	 * paranoia - a flag to make sure cancelled TOs don't do anything
	 */
	private volatile boolean cancelled;

	/**
	 * Create <i>and start</i> an interrupter to interrupt the thread that
	 * starts this. This typically leads to an {@link InterruptedException}
	 * being thrown. Use to set a timeout on blocking operations (which must be
	 * cancelled to avoid the interruption).
	 * 
	 * @param timeout
	 *            In milliseconds.
	 */
	public TimeOut(long timeout) {
		this.timeout = timeout;
		start();
	}

	/**
	 * Create <i>and start</i> an interrupter to interrupt the thread that
	 * starts this. This typically leads to an {@link InterruptedException}
	 * being thrown. Use to set a timeout on blocking operations (which must be
	 * cancelled to avoid the interruption).
	 */
	public TimeOut(Dt timeout) {
		this(timeout.getMillisecs());
	}

	/**
	 * Best practice is to use the static method, which allows for paranoid usage around null.
	 * @see TimerTask#cancel() Can be called repeatedly without harm.
	 */
	public void cancel() {
		cancelled = true; // paranoia
		task.cancel();		
		open.decrementAndGet();
	}

	/**
	 * A silly no-op method. This keeps Eclipse happy if you want to catch
	 * InterruptedException.
	 * 
	 * @throws InterruptedException
	 */
	public static void canThrow() throws InterruptedException {

	}
	
	static final AtomicInteger open = new AtomicInteger();

	private void start() {
		if (task != null)
			return; // already started
		final Thread target = Thread.currentThread();
		task = new TimerTask() {
			@Override
			public void run() {
				if (cancelled) {
					Log.w("TimeOut", "run but cancelled?! Leaving alone " + target.getName());
					return;
				}
				// log that we did indeed timeout
				Log.w("TimeOut", "Interrupting " + target.getName());
				target.interrupt();
			}
		};
		try {
			timer.schedule(task, timeout);
			open.incrementAndGet();
		} catch (Exception ex) {
			// This shouldn't ever happen -- but it was observed on teleton by
			// Joe, 1st June 2012
			timer = newTimer();
		}
	}

	private synchronized static Timer newTimer() {
		return new Timer("TimeOuter", true);
	}

	/**
	 * @param timeout Can be null
	 */
	public static void cancel(TimeOut timeout) {
		if (timeout==null) return;
		timeout.cancel();		
	}

	/**
	 * same as cancel()
	 */
	@Override
	public void close() {
		cancel();
	}

}
