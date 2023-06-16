package me.zpath;

import java.util.*;

class Path extends Term {

    final List<Axis> path;

    Path(List<Axis> path) {
        this.path = path;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<path.size();i++) {
            Axis axis = path.get(i);
            String t = axis.toString();
            if (i > 0 && path.get(0) != Axis.ROOT && t.charAt(0) != '[') {
                sb.append('/');
            }
            sb.append(t);
        }
        return sb.toString();
    }

    @Override public void dump(Configuration config) {
        config.debug("path: " + this);
        config = config.debugIndent();
        for (Axis axis : path) {
            axis.dump(config);
        }
    }

    @Override public boolean isPath() {
        return true;
    }

    @Override public void eval(final Collection<Node> in, final Collection<Node> out, Configuration config) {

        List<Node> tmpin = new ArrayList<Node>();
        List<Node> tmpout = new ArrayList<Node>();
        tmpin.addAll(in);
        final int len = out.size();
        if (config.isDebug()) {
            config.debug(this + " test " + in);
        }
        for (int i=0;i<path.size();i++) {
            Axis axis = path.get(i);
            tmpout.clear();
            if (config.isDebug()) {
                config.debugIndent().debug("testing axis " + axis + " on " + tmpin.size() + " nodes");
            }
            axis.eval(tmpin, tmpout, config.debugIndent());
            if (tmpout.isEmpty()) {
                break;
            }
            tmpin.clear();
            for (int j=0;j<tmpout.size();j++) {
                Node n = tmpout.get(j);
                if (!tmpin.contains(n)) {
                    tmpin.add(n);
                }
            }
        }
        out.addAll(tmpout);
        if (config.isDebug()) {
            config = config.debugIndent();
            config.debug("output: " + tmpout.size() + " nodes");
            config = config.debugIndent();
            for (Node n : tmpout) {
                config.debug(n.toString());
            }
        }
    }

}
