package me.zpath;

import java.util.*;

/**
 * A EvalFactory creates an EvalContext that can be used to evaluate an object.
 * Multiple EvalFactories are registered with a Configuration - when {@link ZPath#eval}
 * is called, they are evaluated in turn to find one capable of traversing the object.
 * @see Configuration#getFactories
 */
public interface EvalFactory {

    /**
     * Create a new EvalContext, or return <code>null</code> if this factory doesn't apply to this type of object
     * @param o the object that will be parsed by the returned {@link EvalContext}
     * @param config the Configuration
     * @return the EvalContext or null if the factory doesn't apply
     */
    public EvalContext create(Object o, Configuration config);
}
