package me.zpath;

import java.util.*;

class FunctionCount implements Function {

    public String getName() {
        return "count";
    }

    public boolean verify(List<Term> arguments) {
        return true;
    }

    public String toString() {
        return getName() + "()";
    }

    public void eval(final List<Term> arguments, final Collection<Node> in, final Collection<Node> out, final Configuration config) {
        int count = 0;
        if (config.isDebug()) {
            config.debug(this + " " + arguments + " ctx=" + in);
        }
        Set<Node> nodes = new HashSet<Node>();
        for (Term t : arguments) {
            t.eval(in, nodes, config.debugIndent());
        }
        for (Node node : nodes) {
            Iterator<Node> i = node.children();
            if (i != null) {
                while (i.hasNext()) {
                    i.next();
                    count++;
                }
            }
        }
        out.add(Node.create(count));
    }

}
