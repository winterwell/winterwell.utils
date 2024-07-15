package com.winterwell.utils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.log.Log;

/**
 * Dependency: Store common dependencies, like settings, database access objects, etc.
 * Stuff you might otherwise put in a static field somewhere.
 * A simple form of dependency injection - without forcing your code into a framework.
 * 
 * @see Environment ?? Should we merge these two classes?
 * @author daniel
 * @testedby DepTest
 */
public final class Dep {

	/**
	 * TODO clean up after a DepContext close 
	 *  -- probably by having stash on DepContext with a cross-thread root DepContext 
	 */
	static ConcurrentHashMap<DKey, Object> stash = new ConcurrentHashMap<>();
	
	static ConcurrentHashMap<DKey, Supplier> factory = new ConcurrentHashMap<>();
	
	public static <X> void setSupplier(Class<X> klass, boolean singleton, Supplier<X> supplier) {
		Log.d("dep", "set "+klass+" = <supplier> "+ReflectionUtils.getSomeStack(6, Dep.class.getName()));
		DepContext ctxt = getContext();
		if (singleton) {
			Supplier supplier2 = () -> {
				X x = supplier.get();
				stash.put(key(klass, ctxt), x);
				return x;
			};
			factory.put(key(klass, ctxt), supplier2);
		}
		factory.put(key(klass, ctxt), supplier);
	}

	
	
	private static DKey key(Class klass, DepContext ctxt) {
		return new DKey(klass, ctxt);
	}

	/**
	 * For use in try-with blocks, e.g.
	 * <code>
	 * try (DepContext context = Dep.with(MyThing.class, myThing)) {
	 * 
	 * }
	 * </code>
	 * @param klass
	 * @param value
	 * @return
	 */
	public static <X> DepContext with(Class<X> klass, X value) {
		DepContext cntxt = Dep.setContext(Utils.getRandomString(6));
		Dep.set(klass, value);		
		return cntxt;
	}
	
	/**
	 * NB: Very similar to {@link #getWithDefault(Class, Object)}
	 * NB: not thread-safe
	 * @param klass
	 * @param value
	 * @return the value held/set
	 */
	public static <X> X setIfAbsent(Class<X> klass, X value) {
		X got = get2(klass);
		if (got!=null) {
			return got;	
		}
		set(klass, value);
		return value;
	}


	/**
	 * NB: Very similar to {@link #setIfAbsent(Class, Object)} but does not set
	 * @param <X>
	 * @param klass
	 * @param defaultValue Can be null if you want, for a version of get() that is lenient about unset.
	 * @return the value held / the default
	 */
	public static <X> X getWithDefault(Class<X> klass, X defaultValue) {
		X got = get2(klass);
		if (got != null) {
			return got;	
		}
		return defaultValue;
	}
	

	/**
	 * Convenience for using Dep.get() with {@link ConfigFactory} to load if unset
	 * @param <X>
	 * @param klass
	 * @return config
	 */
	public static <X> X getWithConfigFactory(Class<X> klass) {
		X got = get2(klass);
		if (got != null) {
			return got;	
		}
		ConfigFactory cf = ConfigFactory.get();
		X config = cf.getConfig(klass);
		assert config != null;
		assert has(klass) : klass;
		return config;
	}



	/**
	 * Set - can override
	 * @param klass
	 * @param value
	 * @return value
	 */
	public static <X> X set(Class<X> klass, X value) {
		Log.d("dep", "set "+klass+" = "+value+" "+ReflectionUtils.getSomeStack(6, Dep.class.getName()));
		DepContext ctxt = getContext();
		DKey key = key(klass, ctxt);
		Object old;
		if (value==null) {
			// null => remove
			old = stash.remove(key);
		} else {
			// normal
			old = stash.put(key, value);
		}
		// as a debug aid 
		key.stacktrace = ReflectionUtils.getSomeStack(12);
		if (old != null) {
			Log.w("dep", "set "+klass+" Override! "+old+" with "+value+" Best practice is NOT to override.");
		}
		return value;
	}
	

	/**
	 * This can create a new instance! Use #has() to test for whether we have one.
	 * @param class1
	 * @return
	 */
	public static <X> X get(Class<X> class1) throws DepNotSetException {
		DepContext ctxt = getContext();
		return get(class1, ctxt);
	}

	
	static <X> X get2(Class<X> class1) throws DepNotSetException {
		DepContext ctxt = getContext();
		return get2(class1, ctxt);
	}
	
	/**
	 * What bit of code made this object?
	 * @param class1
	 * @return stacktrace or null
	 */
	public static List<StackTraceElement> getStackTrace(Class class1) {
		DepContext ctxt = getContext();
		while(ctxt!=null) {		
			DKey key = key(class1, ctxt);
			if (stash.containsKey(key)) {
				DKey originalKey = Containers.first(stash.keySet(), k -> key.equals(k));
				return originalKey.stacktrace;
			}
			if (factory.containsKey(key)) {
				DKey originalKey = Containers.first(factory.keySet(), k -> key.equals(k));
				return originalKey.stacktrace;
			}
			ctxt = ctxt.parent;
		}
		return null;
	}
	
	public static <X> X get(Class<X> class1, DepContext ctxt) throws DepNotSetException {
		X x = get2(class1, ctxt);
		if (x != null) {
			return x;
		}
		// try a vanilla constructor? - no someone should knowingly set the value		
		// should we return null? Typical use case is for key config objects where a null might be problematic.
		throw new DepNotSetException(class1+" - No value set. Note: you can use Dep.has() to check.");
	}
	
	/**
	 * Unlike {@link #get(Class, DepContext)} this CAN return null
	 * @param <X>
	 * @param class1
	 * @param ctxt
	 * @return
	 */
	static <X> X get2(Class<X> class1, DepContext ctxt) {
		assert ! ctxt.closed;
		while(ctxt!=null) {			
			X x = (X) stash.get(key(class1, ctxt));
			if (x!=null) return x;
			Supplier<X> s = factory.get(key(class1, ctxt));
			if (s!=null) {
				x = s.get();
				// don't store factory output. make it fresh.
				return x;
			}
			ctxt = ctxt.parent;
		}
		return null;
	}
	
	public static final class DepNotSetException extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		public DepNotSetException(String string) {
			super(string);
		}
		
	}
	
	private static ThreadLocal<DepContext> context = new ThreadLocal<DepContext>();
	
	/**
	 * Create a new context for this thread. The context must be closed when finished.
	 * The new context has get() access to parent key/values as a fallback.
	 *  
	 * @param contextKey A non-null identifier for this context. E.g. a name.
	 * @return the new context (which can be passed between threads).
	 */
	public static DepContext setContext(String contextKey) {
		assert contextKey != null;
		DepContext ctxt = getContext();
		DepContext newContext = new DepContext(ctxt, contextKey);
		context.set(newContext);
		return newContext;
	}
	
	static final DepContext root = new DepContext(null, null);
	
	public static DepContext getContext() {
		DepContext ctxt = context.get();
		if (ctxt==null) return root;
		while(true) {			
			if ( ! ctxt.closed) return ctxt;
			ctxt = ctxt.parent;
			context.set(ctxt);
			// since the null-parent top contexts can't be closed, this should always finish
		}
	}
	
	/**
	 * 
	 * @param class1
	 * @return true if set, either by value or a supplier
	 */
	public static boolean has(Class class1) {
		// NB: don't just use get() to avoid calling on a supplier function
		// -- though do we really care if we do??
		DepContext ctxt = getContext();
		while(ctxt != null) {
			DKey klass = key(class1, ctxt);
			if (stash.containsKey(klass)) return true;
			if (factory.containsKey(klass)) return true;
			ctxt = ctxt.parent;
		}
		return false;
	}


	/**
	 * Convenience for {@link #set(Class, Object)}
	 */
	public static <X> X set(X thingy) {
		return (X) set((Class) thingy.getClass(), thingy);
	}




}

/**
 * Key on class+context
 * @author daniel
 */
final class DKey {

	private final DepContext context;
	private final Class klass;
	
	/**
	 * for debug use. NOT used in equals / hashcode
	 */
	transient List<StackTraceElement> stacktrace;
	
	public DKey(Class klass, DepContext context) {
		this.klass = klass;
		this.context = context;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((context == null) ? 0 : context.hashCode());
		result = prime * result + ((klass == null) ? 0 : klass.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "DKey [context=" + context + ", klass=" + klass + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DKey other = (DKey) obj;
		if (context == null) {
			if (other.context != null)
				return false;
		} else if (!context.equals(other.context))
			return false;
		if (klass == null) {
			if (other.klass != null)
				return false;
		} else if (!klass.equals(other.klass))
			return false;
		return true;
	}
	
}