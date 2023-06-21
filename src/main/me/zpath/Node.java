package me.zpath;

import java.util.*;

/**
 * <p>
 * A Node represent an item in the data structure being queried.
 * It is a simple interface; it's expected that particular implementations for a data
 * model will implement this interface, either directly or as a proxy.
 * </p>
 * <p>
 * <b>Implementation Note</b>
 * If two Nodes
 * return the same <code>hashCode()</code> and return true from <code>equals()</code>, it doesn't
 * matter if they both represent the same underlying item. This makes wrapping to
 * another class easy to do, as a new Proxy can be returned each time - for example:
 * </p>
 * <pre><code class="language-java">
 * public class MyProxy implements NodeFactory {
 *   public Node create(Object o, Configuration config) {
 *     if (o instanceof MyClass) {
 *       return new Proxy((MyClass)o, null, -1);
 *     }
 *     return null;
 *   }
 *
 *   private static class Proxy implements Node {
 *     private final Object key;
 *     private final MyClass proxy;
 *  
 *     Proxy(MyClass proxy, Object key) {
 *       this.proxy = proxy;
 *       this.key = key;
 *     }
 *  
 *     public Node parent() {
 *       MyClass j = proxy.parent();
 *       return j == null ? null : new Proxy(proxy, null);
 *     }
 *  
 *     public Iterator<Node> get(Object key) {
 *       MyClass j = proxy.get(key);
 *       return j == null ? null : new Proxy(proxy, key);
 *     }
 *  
 *     public String name() {
 *  
 *     public String name() {
 *       return key;
 *     }
 *  
 *     public int hashCode() {
 *       return proxy.hashCode();
 *     }
 *  
 *     public boolean equals(Object o) {
 *       return o instanceof Proxy &amp;&amp; ((Proxy)o).proxy.equals(proxy);
 *     }
 *     ...
 *   }
 * }
 * </code></pre>
 */
public interface Node {

    public static final Object WILDCARD = "*";

    /**
     * If the Node in not the root element of the structure, return the parent, otherwise return null
     * @return the parent node, or <code>null</code> if none exists
     */
    public Node parent();

    /**
     * Return the value of the Node when evaluated in an expression expecing a String.
     * The exact meaning of this will depend on the Node language model, but in JSON it's expected
     * that the native "string" type will return a value, and everything else will return null.
     * @return the value as a String, or null if this node does not evaluate to a String
     */
    public String stringValue();

    /**
     * Return the value of the Node when evaluated in an expression expecting a Number.
     * The exact meaning of this will depend on the Node language model, but in JSON it's expected
     * that the native "number" type will return a value, and everything else will return null.
     * @return the value as a double, or Double.NaN if this node does not evaluate to a double
     */
    public double doubleValue();

    /**
     * Return the value of the Node when evaluated in a boolean expression.
     * The exact meaning of this will depend on the Node language model, but in JSON it's
     * expected that everythins other than <code>false</code> or <code>null</code> would return true.
     * @return the value as a boolean
     */
    public boolean booleanValue();

    /**
     * Return the first child of this Node that is stored with the specified key,
     * or <code>null</code> if this Node has no matching child, or no concept of children stored against a key
     * (i.e. it's not a map).
     * @param name the name, which will never be null and will always be at least one character long. It may be a special value, eg "*" or "@*",
     * the meaning of which is going to depend on the model, but is generally a wildcard.
     * @return the Node matching that value, or null if none found or it's not applicable
     */
    public Iterator<Node> get(Object key);

    /**
     * Return the type of this Node. The values depend on the source language but we suggest at least
     * <code>string</code>, <code>map</code>, <code>list</code>, <code>number</code>, <code>boolean</code> or <code>null</code>
     * are used as base types, and <code>attr</code> or <code>tag</code> are used when they apply.
     * This value is only used by the <code>type()</code> function.
     * @return the type
     */
    public String type();

    /**
     * If this Node can be accessed by its {@link #parent parent} element by a key passed to {@link #get(String)},
     * return the value of this key. This method may return <code>null</code> for nodes not originally accessed
     * from {@link #children} or {@link #get(String)}, such as nodes returned from {@link #parent}.
     * This value is only used by the "@" axis
     * @return the key by which this Node is retrieved from its parent, or <code>null</code> if its not known or doesn't apply
     */
    public Object key();

    /**
     * If this Node can be accessed via an index into its parent (ie. by passing an integer into get) and
     * that integer really does represents an index rather than a numeric map key, then return the index, otherwise return -1.
     * @return the index into this node's parent to retrieve this node, or -1
     */
    public int index();

    /**
     * If this Node is a proxy for another object, return that object, otherwise return this.
     * Used to unwrap the return value from {@link ZPath#eval}.
     */
    public Object proxy();

    /**
     * Create a new Node representing an immutable constant.
     * @param v the value, which would normally be a integer, double, boolean or String, but may be other types.
     * @return a new Node
     */
    public static Node create(Object v) {
        final Object fv;
        if (v instanceof Long && ((Long)v).intValue() == ((Long)v).longValue()) {
            fv = Integer.valueOf(((Long)v).intValue());
        } else if (v instanceof Character || v instanceof Short || v instanceof Byte) {
            fv = Integer.valueOf(((Number)v).intValue());
        } else if (v instanceof CharSequence) {
            fv = v.toString();
        } else if (v instanceof Float) {
            fv = Double.valueOf(((Float)v).floatValue());
        } else {
            fv = v;
        }
        return new Node() {
            @Override public Node parent() {
                return null;
            }
            @Override public String stringValue() {
                return fv.toString();
            }
            @Override public double doubleValue() {
                return fv instanceof Number ? ((Number)fv).doubleValue() : Double.NaN;
            }
            @Override public boolean booleanValue() {
                return fv instanceof Boolean ? ((Boolean)fv).booleanValue() : false;
            }
            @Override public Object key() {
                return null;
            }
            @Override public int index() {
                return -1;
            }
            @Override public String type() {
                if (fv instanceof Boolean) {
                    return "boolean";
                } else if (fv instanceof Number) {
                    return "number";
                } else if (fv instanceof String) {
                    return "string";
                } else {
                    return fv.getClass().getName();
                }
            }
            @Override public Iterator<Node> get(Object key) {
                return null;
            }
            @Override public String toString() {
                return stringValue();
            }
            @Override public int hashCode() {
                return fv.hashCode();
            }
            @Override public boolean equals(Object o) {
                return o instanceof Node && fv.equals(o);
            }
            @Override public Object proxy() {
                return fv;
            }
        };
    }

}
