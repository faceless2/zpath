package me.zpath;

import java.util.*;

class FunctionAxis extends Term implements Axis {

    final Function function;
    final List<Term> args;

    FunctionAxis(Function function, List<Term> args) {
        this.function = function;
        this.args = args;
    }

    @Override public String toString() {
        return function.getName() + "(" + args + ")";
    }

    @Override public void dump(Configuration config) {
        config.debug(toString());
    }

    @Override public boolean isFunction() {
        return true;
    }

    @Override public void eval(final Collection<Node> in, final Collection<Node> out, Configuration config) {
        function.eval(args, in, out, config);
    }

    static class Dynamic implements Function {
        final String name;
        Function function;

        Dynamic(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean verify(List<Term> arguments) {
            return true; // never called
        }

        public void eval(List<Term> arguments, Collection<Node> in, Collection<Node> out, Configuration config) {
            synchronized(this) {
                if (function == null) {
                    function = config.getFunction(name);
                    if (function == null) {
                        throw new IllegalStateException("No such function " + getName() + "()");
                    }
                }
            }
            function.eval(arguments, in, out, config);
        }

    }

}
