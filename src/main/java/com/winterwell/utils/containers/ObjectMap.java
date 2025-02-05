package com.winterwell.utils.containers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.log.Log;

/**
 * Treat the fields of an object like a map. One must question the sanity of
 * this, but it can be useful.
 * 
 * Skips static and transient fields.
 * @author daniel
 * 
 */
public final class ObjectMap extends AbstractMap2<String, Object> {

	private final Class klass;
	private final Object object;

	public ObjectMap(Object x) {
		this.object = x;
		klass = x.getClass();
	}

	@Override
	public Object get(Object key) {
		String k = (String) key;
		return ReflectionUtils.getPrivateField(object, k);
	}
	
	@Override
	public Object remove(Object key) {
		return put((String) key, null);
	}

	@Override
	public Set<String> keySet() throws UnsupportedOperationException {
		List<Field> fields = ReflectionUtils.getAllFields(klass); //klass.getFields();
		Set<String> keys = new HashSet<String>(fields.size());
		for (Field field : fields) {
			int mods = field.getModifiers();
			if (Modifier.isTransient(mods)) continue;
			if (Modifier.isStatic(mods)) continue;
			keys.add(field.getName());
		}
		return keys;
	}

	@Override
	public Object put(String key, Object value) {
		Object prev = get(key);
		ReflectionUtils.setPrivateField(object, key, value);
		return prev;
	}
}