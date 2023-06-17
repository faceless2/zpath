package me.zpath;

import java.util.*;

class FunctionNumber implements Function {

    public String getName() {
        return "number";
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
            String s = node.stringValue();
            if (s != null) {
                try {
                    double d = Double.parseDouble(s);
                    out.add(Node.create(d));
                } catch (Exception e) { }
            }
        }
    }

}
