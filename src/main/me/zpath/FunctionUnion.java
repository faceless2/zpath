package me.zpath;

import java.util.*;

class FunctionUnion implements Function {

    public String getName() {
        return "union";
    }

    public boolean verify(List<Term> arguments) {
        return true;
    }

    public String toString() {
        return getName() + "()";
    }

    public void eval(final List<Term> arguments, final Collection<Node> in, final Collection<Node> out, final Configuration config) {
        double max = Double.NaN;
        if (config.isDebug()) {
            config.debug(this + " TEST: args=" + arguments + " ctx=" + in);
        }
        Set<Node> nodes = new HashSet<Node>();
        for (Term t : arguments) {
            t.eval(in, nodes, config.debugIndent());
        }
        for (Node node : nodes) {
            out.add(node);
        }
    }

}
