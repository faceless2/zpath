package me.zpath;

import java.util.*;

/**
 * A Function interface which can be implemented to extend the API with custom functionality
 * @see Configuration#registerFunction
 */
public interface Function {

    /**
     * Return the name of the function eg "max"
     * @return the name
     */
    public String getName();

    /**
     * Verify the list of arguments for this function, returning false if they're invalid.
     * As most arguments are resolved at runtime this method will often simply verify the size of the list
     * @param arguments the arguments
     * @return true if the function is valid with these arguments
     */
    public boolean verify(List<Term> arguments);

    /**
     * Evaluate the function
     * @param arguments the arguments to the function, which had previously been passed into {@link #verify}
     * @param in the Nodes in the <i>input</i> context - there will be at least one node.
     * @param out the Nodes which can be written to as the <i>output</i> context
     * @param config the configuration, which should only be used for debug output at this point
     */
    public void eval(List<Term> arguments, List<Node> in, List<Node> out, Configuration config);

}
