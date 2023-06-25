package me.zpath;

import java.util.*;

/**
 * A Function interface which can be implemented to extend the API with custom functionality
 * @see Configuration#getFunctions
 */
public interface Function {

    /**
     * Return true if the specified function name is implemented by this class
     * @return the name
     */
    public boolean matches(String name);

    /**
     * Verify the list of arguments for this function, returning false if they're invalid.
     * As most arguments are resolved at runtime this method will often simply verify the size of the list
     * @param name the name of the function
     * @param arguments the arguments
     * @return true if the function is valid with these arguments
     */
    public boolean verify(String name, List<Term> arguments);

    /**
     * Evaluate the function. There are two possible ways a function can be called
     *  1. It's part of expr in the form path[expr]. If this is the case, "in" will be a list of one item, and context will have context/contextIndex set
     *  2. It's in the form path/function). If this is the case, "in" will be one or more items, and context will be null.
     * In the second case, the function must be careful to not add duplicate nodes to the output.
     *
     * @param name the name of the function
     * @param arguments the arguments to the function, which had previously been passed into {@link #verify}
     * @param in the Nodes in the <i>input</i> context - there will be at least one node.
     * @param out the Nodes which can be written to as the <i>output</i> context
     * @param config the configuration, which should only be used for debug output at this point
     */
    public void eval(String name, List<Term> arguments, List<Object> in, List<Object> out, EvalContext context);

}
