package me.zpath;

import java.util.*;

class FunctionIntersection implements Function {

    public String getName() {
        return "intersection";
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
        List<Set<Node>> l = new ArrayList<Set<Node>>();
        for (int i=0;i<arguments.size();i++) {
            l.add(new HashSet<Node>());
            for (Term t : arguments) {
                t.eval(in, l.get(i), config.debugIndent());
            }
        }
        for (int i=1;i<arguments.size();i++) {
            l.get(0).retainAll(l.get(i));
        }
        for (Node node : l.get(0)) {
            out.add(node);
        }
    }

}
