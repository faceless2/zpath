package me.zpath;

import java.util.*;

class FunctionAxis extends Term implements Axis {

    final Function function;
    final List<Term> args;
    final boolean path;

    FunctionAxis(Function function, List<Term> args, boolean path) {
        this.function = function;
        this.args = args;
        this.path = path;
    }

    @Override public String toString() {
        return function.getName() + "(" + args + ")";
    }

    @Override public void log(Configuration.Logger logger) {
        logger.log("axis-function(" + this + ")");
    }

    @Override public boolean isFunction() {
        return true;
    }

    @Override public List<Node> eval(final List<Node> in, final List<Node> out, Configuration config) {
        if (path) {
            for (int i=0;i<in.size();i++) {
                List<Node> tmp = new ArrayList<Node>();
                function.eval(args, Collections.<Node>singletonList(in.get(i)), tmp, config);
                out.addAll(tmp);
            }
        } else {
            function.eval(args, in, out, config);
        }
        return out;
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

        public void eval(List<Term> args, List<Node> in, List<Node> out, Configuration config) {
            synchronized(this) {
                if (function == null) {
                    function = config.getFunction(name);
                    if (function == null) {
                        throw new IllegalStateException("No such function " + getName() + "()");
                    }
                }
            }
            function.eval(args, in, out, config);
        }
    }

}
