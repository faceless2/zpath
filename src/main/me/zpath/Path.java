package me.zpath;

import java.util.*;

class Path extends Term {

    final List<Axis> path;
    final String tostring;

    Path(List<Axis> path, String tostring) {
        this.path = path;
        this.tostring = tostring;
    }

    @Override public String toString() {
        return "path(" + tostring + ")";
    }

    @Override public void log(Configuration.Logger logger) {
        super.log(logger);
        logger.enter();
        for (Axis axis : path) {
            axis.log(logger);
        }
        logger.exit();
    }

    @Override public boolean isPath() {
        return true;
    }

    @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
        final Configuration.Logger logger = config.getLogger();
        List<Node> tmpin = new ArrayList<Node>();
        List<Node> tmpout = new ArrayList<Node>();
        tmpin.addAll(in);
        final int len = out.size();
        if (logger != null) {
            logger.log(this + " eval");
        }
        try {
            if (logger != null) {
                logger.enter();
            }
            for (int i=0;i<path.size();i++) {
                Axis axis = path.get(i);
                tmpout.clear();
                try {
                    if (logger != null) {
                        logger.log(axis + " eval on " + tmpin.size() + " nodes");
                        logger.enter();
                    }
                    axis.eval(tmpin, tmpout, config);
                } finally {
                    if (logger != null) {
                        logger.exit();
                    }
                }
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
            if (logger != null) {
                logger.log("output: " + tmpout.size() + " nodes");
                logger.enter();
                for (Node n : tmpout) {
                    logger.log(n.toString());
                }
                logger.exit();
            }
            return out;
        } finally {
            if (logger != null) {
                logger.exit();
            }
        }
    }

}
