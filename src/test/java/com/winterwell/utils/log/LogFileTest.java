package com.winterwell.utils.log;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.Dep;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.threads.ATask;
import com.winterwell.utils.threads.TaskRunner;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.StopWatch;
import com.winterwell.utils.time.TUnit;

public class LogFileTest {

	@Test public void testLogFile() {
		File f = new File("test-output/test1.txt");
		FileUtils.delete(f);
		LogFile lf = new LogFile(f);
		Log.report("Hello 1");
		Log.report("Hello 2");
		Utils.sleep(100);
		String log = FileUtils.read(f);
		Printer.out(log);
		Log.report("Hello 3");
		Utils.sleep(1000);
		log = FileUtils.read(f);
		Printer.out(log);
		lf.close();
	}



	@Ignore("Slow test")
	@Test
	public void testContention() {
		File f = new File("test-output/test-contention.txt");
		FileUtils.delete(f);
		LogFile lf = new LogFile(f);
		Log.report("Hello 1");
		TaskRunner tr = new TaskRunner(100);
		StopWatch sw = new StopWatch();
		for(int i=0; i< 1000; i++) {
			tr.submit(new ATask("t"+i) {
				@Override
				protected Object run() throws Exception {
					for(int j=0; j<1000; j++) {
						Log.i(this.getName(), "j "+j);
					}
					return null;
				}
			});
		}
		tr.shutdown();
//		Utils.sleep(10000);
		tr.awaitTermination();
//		Utils.sleep(1000);
		lf.close();
		sw.stop();
		sw.print();
		Dt dt = sw.getDt();
		String logged = FileUtils.read(f);
		for(int i=0; i< 1000; i++) {
			assert logged.contains("t"+i) : "t"+i;
		}
	}

	@Ignore("Broken test")
	@Test public void testLogFileSizeViaConfig() {
		ConfigBuilder cb = new ConfigBuilder(new LogConfig());
		cb.setFromMain("-fileMaxSize 1k".split(" "));
		LogConfig lc = cb.get();
		Dep.set(LogConfig.class, lc);
		
		File f = new File("test-output/testLogFileSizeViaConfig.txt");
		FileUtils.delete(f);
		LogFile lf = new LogFile(f);
		for (int i=0; i<100; i++) {
			Log.i("Hello "+i);
		}
		Utils.sleep(1000);
		String log = FileUtils.read(f);
		Printer.out(log);		
		lf.close();
		assert log.contains("Hello 1");
		assert log.contains("file too big");
	}

	@Ignore("Broken test")
	@Test public void testLogError() {
		File f = new File("test-output/test-error.txt");
		FileUtils.delete(f);
		LogFile lf = new LogFile(f);
		Exception es = new Exception("base");
		RuntimeException ex2 = new RuntimeException(es);
		Log.e("test", ex2);		
		Utils.sleep(1000);
		String log = FileUtils.read(f);
		Printer.out(log);
		lf.close();
	}

	@Ignore("Broken test")
	@Test public void testLogAutoTag() {
		File f = new File("test-output/testLogAutoTag.txt");
		FileUtils.delete(f);
		LogFile lf = new LogFile(f);
		Log.d("Hello 1");
		Log.d("Hello 2");
		Utils.sleep(1000);
		String log = FileUtils.read(f);
		Printer.out(log);
		assert log.contains("Hello 2");
		lf.close();
	}

	@Ignore("Slow test")
	@Test
	public void testRotation() {
		{
			File f = new File("test-output/rotate-test.txt");
			FileUtils.delete(f);
			LogFile lf = new LogFile(f);
			lf.setLogRotation(TUnit.SECOND.getDt(), 10);
			for (int i = 0; i < 5; i++) {
				Log.report("Hello " + i);
				Utils.sleep(750);
			}
			// check what happened
			Utils.sleep(1000);
			String log = FileUtils.read(f);
			Printer.out(log);
			assert log.contains("Hello 4") : log;
			assert !log.contains("Hello 1") : log;
			Printer.out(f.getAbsoluteFile().getParentFile().list());
			lf.close();
		}
		{
			File f = new File("test-output/rotate-test2.txt");
			FileUtils.delete(f);
			LogFile lf = new LogFile(f);
			lf.setLogRotation(TUnit.SECOND.getDt(), 10);
			for (int i = 0; i < 20; i++) {
				Log.report("Hello " + i);
				Utils.sleep(750);
			}
			// check what happened
			Utils.sleep(1000);
			String log = FileUtils.read(f);
			Printer.out(log);
			assert log.contains("Hello 19") : log;
			assert !log.contains("Hello 11") : log;
			Printer.out(f.getAbsoluteFile().getParentFile().list());
			lf.close();
		}
	}
}
