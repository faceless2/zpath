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
 *     private final int index;
 *     private final String key;
 *     private final MyClass proxy;
 *  
 *     Proxy(MyClass proxy, String key, int index) {
 *       this.proxy = proxy;
 *       this.key = key;
 *       this.index = index;
 *     }
 *  
 *     public Node parent() {
 *       MyClass j = proxy.parent();
 *       return j == null ? null : new Proxy(proxy, null, -1);
 *     }
 *  
 *     public Node get(String key) {
 *       MyClass j = proxy.get(key);
 *       return j == null ? null : new Proxy(proxy, key, -1);
 *     }
 *  
 *     public String key() {
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
     * @param name the name, which will never be null and will always be at least one character long
     * @return the Node matching that value, or null if none found or it's not applicable
     */
    public Node get(String name);

    /**
     * Return the child of this Node that is stored with the specified index,
     * or <code>null</code> if this Node has no matching child, or no concept of children stored against a numeric index
     * (i.e. its not a list).
     * @param index the index, which may be any value
     * @return the Node matching that value, or null if the value is out of range or it's not applicable
     */
    public Node get(int index);

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
    public String key();

    /**
     * If this Node can be accessed by its {@link #parent parent} element by an index passed to {@link #get(int)},
     * return the value of this index. This method may return <code>-1</code> for nodes not originally accessed
     * from {@link #children} or {@link #get(String)}, such as nodes returned from {@link #parent}.
     * This value is only used by the "@" axis
     * @return the index by which this Node is retrieved from its parent, or <code>-1</code> if its not known or doesn't apply
     */
    public int index();

    /**
     * Return an Iterator listing all the children of this Node, in order.
     * If this node has no children because it is empty, return an empty iterator.
     * If this node has no children because it doesn't have that concept (it's a simple type), return null.
     * @return an iterator to access the children of this node, or <code>null</code> if this Node cannot have children
     */
    public Iterator<Node> children();

    /**
     * If this Node is a proxy for another object, return that object, otherwise return this.
     * Used to unwrap the return value from {@link ZPath#eval}.
     */
    public Object proxy();

    /**
     * Create a new Node representing a boolean constant
     * @param v the value
     * @return a new Node
     */
    public static Node create(final boolean v) {
        return new Node() {
            public Node parent() {
                return null;
            }
            public String stringValue() {
                return v ? "true" : "false";
            }
            public double doubleValue() {
                return Double.NaN;
            }
            public boolean booleanValue() {
                return v;
            }
            public Node get(int i) {
                return null;
            }
            public String key() {
                return null;
            }
            public int index() {
                return -1;
            }
            public String type() {
                return "boolean";
            }
            public Node get(String i) {
                return null;
            }
            public Iterator<Node> children() {
                return null;
            }
            public String toString() {
                return stringValue();
            }
            public int hashCode() {
                return v ? 1 : 0;
            }
            public boolean equals(Object o) {
                return o instanceof Node && booleanValue() == ((Node)o).booleanValue();
            }
            public Object proxy() {
                return v;
            }
        };
    }

    /**
     * Create a new Node representing a number constant
     * @param v the value
     * @return a new Node
     */
    public static Node create(final Number v) {
        if (v == null) {
            throw new IllegalArgumentException();
        }
        return new Node() {
            public Node parent() {
                return null;
            }
            public String stringValue() {
                return v.toString();
            }
            public double doubleValue() {
                return v.doubleValue();
            }
            public boolean booleanValue() {
                return false;
            }
            public Node get(int i) {
                return null;
            }
            public Node get(String i) {
                return null;
            }
            public String key() {
                return null;
            }
            public int index() {
                return -1;
            }
            public String type() {
                return "number";
            }
            public Iterator<Node> children() {
                return null;
            }
            public String toString() {
                return stringValue();
            }
            public int hashCode() {
                if (v.intValue() == v.doubleValue()) {
                    return v.intValue();
                } else {
                    return v.hashCode();
                }
            }
            public boolean equals(Object o) {
                return o instanceof Node && doubleValue() == ((Node)o).doubleValue();
            }
            public Object proxy() {
                return v;
            }
        };
    }

    /**
     * Create a new Node representing a string constant
     * @param v the value
     * @return a new Node
     */
    public static Node create(String v) {
        if (v == null) {
            throw new IllegalArgumentException();
        }
        return new Node() {
            public Node parent() {
                return null;
            }
            public String stringValue() {
                return v.toString();
            }
            public double doubleValue() {
                return Double.NaN;
            }
            public boolean booleanValue() {
                return false;
            }
            public Node get(int i) {
                return null;
            }
            public Node get(String i) {
                return null;
            }
            public String key() {
                return null;
            }
            public int index() {
                return -1;
            }
            public String type() {
                return "string";
            }
            public Iterator<Node> children() {
                return null;
            }
            public String toString() {
                return stringValue();
            }
            public int hashCode() {
                return v.hashCode();
            }
            public boolean equals(Object o) {
                return o instanceof Node && stringValue().equals(((Node)o).stringValue());
            }
            public Object proxy() {
                return v;
            }
        };
    }

}
