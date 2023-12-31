package me.zpath;

import java.util.*;

class FunctionAxis extends Term implements Axis {

    private final Function function;
    private final String name;
    private final List<Term> args;
    private final boolean path;

    FunctionAxis(Function function, String name, List<Term> args, boolean path) {
        this.function = function;
        this.name = name;
        this.args = args;
        this.path = path;
    }

    @Override public String toString() {
        return name + "(" + args + ")";
    }

    @Override public void log(Configuration.Logger logger) {
        logger.log("axis-function(" + this + ")");
    }

    @Override public boolean isFunction() {
        return true;
    }

    private Function getFunction(EvalContext context) {
        Function function = this.function;
        if (function == null) {
            function = context.getFunction(name);
            if (function == null) {
                throw new IllegalStateException("No such function " + name + "()");
            }
        }
        return function;
    }

    @Override public List<Object> eval(final List<Object> in, final List<Object> out, EvalContext context) {
        Function function = getFunction(context);
        if (path) {
            function.eval(name, args, in, out, context);
        } else {
            function.eval(name, args, in, out, context);
        }
        return out;
    }

}
