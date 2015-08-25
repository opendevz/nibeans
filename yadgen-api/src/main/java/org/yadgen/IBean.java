package org.yadgen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a simple bean interface for which a default implementation can be generated.
 * <p/>
 * 
 * A default implementation is generated if the bean has matching property getters and setters with exactly
 * the same type, and no other methods. Ordering of methods is insignificant. Example:
 * 
 * <pre>
 * &#64;IBean
 * public interface Person {
 * 
 * 	String getName();
 * 
 * 	void setName(String v);
 * 
 * 	List&lt;Person&gt; getChildren();
 * 
 * 	void setChildren(List&lt;Person&gt; v);
 * }
 * </pre>
 * 
 * If the interface has any non-conforming methods, it is ignored by the generator.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IBean {
}
