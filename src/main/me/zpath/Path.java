package me.zpath;

import java.util.*;

class Path extends Term {

    private final List<Axis> path;
    private final String tostring;

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

    @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
        final Configuration.Logger logger = context.getLogger();
        List<Object> tmpin = new ArrayList<Object>();
        List<Object> tmpout = new ArrayList<Object>();
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
                    axis.eval(tmpin, tmpout, context);
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
                    Object n = tmpout.get(j);
                    if (!tmpin.contains(n)) {
                        tmpin.add(n);
                    }
                }
            }
            out.addAll(tmpout);
            if (logger != null) {
                logger.log("output: " + tmpout.size() + " nodes");
                logger.enter();
                for (Object n : tmpout) {
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
