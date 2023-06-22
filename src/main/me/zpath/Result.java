package me.zpath;

import java.util.*;

/**
 * A Result is returned from {@link ZPath#eval} - it is simply a container
 * for a list of found objects, with some additional information and helper methods
 */
public class Result {

    private final ZPath zpath;
    private final List<Object> result;
    private final EvalContext context;

    Result(ZPath zpath, List<Object> result, EvalContext context) {
        this.zpath = zpath;
        this.result = result;
        this.context = context;
    }

    /**
     * Return the full list of objects from this Result
     * @return the objects
     */
    public List<Object> all() {
        return result;
    }

    /**
     * If the Result mactched one or or more objects return the first, otherwise return null
     * @return the object
     */
    public Object first() {
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Convert the objects in this Result to a simpler representation (for example, converting DOM "Attr" or a JSON "string" to a Java String).
     * Simply calls {@link EvalContext#unwrap} method for each object in the Result
     * @return this
     */
    public Result unwrap() {
        for (int i=0;i<result.size();i++) {
            result.set(i, context.unwrap(result.get(i)));
        }
        return this;
    }

    /**
     * Return the {@link EvalContext} that was used to evaluate this Result
     */
    public EvalContext context() {
        return context;
    }

    /**
     * Return the {@link ZPath} that was evaluated to create this Result
     */
    public ZPath zpath() {
        return zpath;
    }

}
