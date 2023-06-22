package me.zpath;

import java.util.*;

/**
 * <p>
 * An EvalContext represents the current state of the evaluation, but is primarily how
 * the structure is navigated.
 * A particular implementations for a data model will implement this interface.
 * </p>
 */
public interface EvalContext {

    /**
     * This value will be passed in to {@link #get} to represents the wildcard key
     */
    public static final Object WILDCARD = "*";

    /**
     * If the node is not the root element of the structure, return its parent, otherwise return null
     * @param o the node
     * @return the parent node, or <code>null</code> if none exists
     */
    public Object parent(Object o);

    /**
     * Return the value of the node when evaluated in an expression expecting a String.
     * The exact meaning of this will depend on the Node language model, but in JSON it's expected
     * that the native "string" type will return a value, and everything else will return null.
     * In XML, Text or Attr nodes will return a value and Elements return null.
     * @param o the node
     * @return the value as a String, or null if this node does not evaluate to a String
     */
    public String stringValue(Object o);

    /**
     * Return the value of the node when evaluated in an expression expecting a Number.
     * The exact meaning of this will depend on the Node language model, but in JSON it's expected
     * that the native "number" type will return a value, and everything else will return NaN.
     * In XML there is no number type, so everything returns NaN.
     * @param o the node
     * @return the value as a double, or Double.NaN if this node does not evaluate to a double
     */
    public double doubleValue(Object o);

    /**
     * Return the value of the node when evaluated in a boolean expression.
     * The exact meaning of this will depend on the Node language model, but in JSON it's
     * expected that everythins other than <code>false</code> or <code>null</code> would return true.
     * In XML, everything returns true.
     * @param o the node
     * @return the value as a boolean
     */
    public boolean booleanValue(Object o);

    /**
     * Return the first child of this Node that is stored with the specified key,
     * or an empty iterator if this Node has no matching child, or no concept of children stored against a key
     * (i.e. it's not a map).
     * @param o the node
     * @param key the key, which will never be null and may be WILDCARD, a String, and Integer, or (theoretically) some other type.
     * If the model chooses to interpret a String value like "@*" as some sort of wildcard match that's OK.
     * @return the set of nodes matching that value, or an empty collection
     */
    public Iterable<? extends Object> get(Object o, Object key);

    /**
     * Return the type of this Node. The values depend on the source language but we suggest at least
     * <code>string</code>, <code>map</code>, <code>list</code>, <code>number</code>, <code>boolean</code> or <code>null</code>
     * are used as base types for JSON, and <code>attr</code> or <code>tag</code> are used when they apply.
     * This value is only used by the <code>type()</code> function.
     * @param o the node
     * @return the type
     */
    public String type(Object o);

    /**
     * If this node can be retrieved by its {@link #parent parent} element by a key passed to {@link #get(String)},
     * return the value of this key.
     * @param o the node
     * @return the key by which this node is retrieved from its parent, or <code>null</code> if it can't be determined
     */
    public Object key(Object o);

    /**
     * If this node can be retrieved from its parent via an integer index into {@link #get} and that integer <i>really does</i>
     * represent an index rather than just a numeric map key, return the index, otherwise return -1
     * @param o the node
     * @return the index into this node's parent to retrieve this node, or -1
     */
    public int index(Object o);

    /**
     * Unwrap the specified object to a more primitive type. This is a convenience
     * method - it's called from the {@link Result#unwrap} method. For example, 
     * this method would convert a DOM Text or Attr object to a String, and a JSON
     * string, number, boolean or null to Java String, Number, Boolean or null
     * @param o the object
     * @return the unwrapped object, which may be "o" if its unchanged
     */
    public Object unwrap(Object o);

    /**
     * Return the Function matching this name in this context, or null if there's no match
     * @param name the function name
     * @return the Function or null
     */
    public Function getFunction(String name);

    /**
     * Return the Configuration that created this EvalContext
     * @param name the function name
     * @return the Function or null
     */
    public Configuration getConfiguration();

    /**
     * Return the Logger to use with this EvalContext, or null if not set
     * @return the Logger
     */
    public Configuration.Logger getLogger();

    /**
     * Set the current "context" for this EvalContext.
     * @param index if nodeset is not null, the index of the current node into that nodeset
     * @param nodeset the current nodeset, which may be null
     */
    public void setContext(int index, List<Object> nodeset);

    /**
     * Return the index passed into {@link #setContext}
     * @return the index
     */
    public int getContextIndex();

    /**
     * Return the nodeset passed into {@link #setContext}
     * @return the nodeset
     */
    public List<Object> getContext();

}
