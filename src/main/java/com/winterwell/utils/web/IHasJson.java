package com.winterwell.utils.web;

import java.util.Collection;
import java.util.Map;

import com.winterwell.utils.IFn;
import com.winterwell.utils.containers.Containers;

/**
 * I can has dynamic ajax action! Call toJson() to get a JSON String
 * @author daniel
 */
public interface IHasJson {	
	
	default void appendJson(StringBuilder sb) {
		sb.append(toJSONString());
	}
	
	/**
	 * Matches JSONString#toJSONString()
	 * 
	 * Recommended: Override to use Gson (where we do most of our json setup).
	 * Otherwise this default version uses SimpleJson.
	 *  
	 * @return A JSON String
	 */
	default String toJSONString() {
		return new SimpleJson().toJson(toJson2());
	}
	
	/**
	 * @return An object suitable for conversion to JSON using a standard library (such as Jetty's JSON class).
	 * Probably a Map, with nested Maps, Lists, and primitives/Strings. 
	 * Can use `return Containers.objectAsMap(this);`
	 * 
	 * @throws UnsupportedOperationException
	 */
	default Object toJson2() {
		return Containers.objectAsMap(this);
	}


	/**
	 * Use this with Containers.applyToValues()
	 */
	public static final IFn RECURSE_TOJSON2 = new IFn() {
		@Override
		public Object apply(Object value) {
			if (value==null) return null;
			if (value instanceof com.winterwell.utils.web.IHasJson) {
				return ((com.winterwell.utils.web.IHasJson) value).toJson2();
			}
			if (value instanceof Collection) {
				return Containers.apply((Collection)value, this);
			}
			if (value instanceof Map) {
				return Containers.applyToValues(this, (Map)value);
			}
			// leave as is and hope
			return value;
		}
	};
}
