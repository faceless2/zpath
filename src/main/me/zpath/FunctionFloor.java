package me.zpath;

import java.util.*;

class FunctionFloor implements Function {

    public String getName() {
        return "floor";
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
            config.debug(this + " " + arguments + " ctx=" + in);
        }
        Set<Node> nodes = new HashSet<Node>();
        for (Term t : arguments) {
            t.eval(in, nodes, config.debugIndent());
        }
        for (Node node : nodes) {
            double d = node.doubleValue();
            if (d == d) {
                int i = (int)(getName().equals("floor") ? Math.floor(d) : getName().equals("ceil") ? Math.ceil(d) : Math.round(d));
                out.add(Node.create(i));
            }
        }
    }

}
