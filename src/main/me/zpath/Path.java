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
                try {
                    if (logger != null) {
                        logger.log(axis + " eval on " + tmpin.size() + " nodes");
                        logger.enter();
                    }
                    tmpout.clear();
                    axis.eval(tmpin, tmpout, context);
                } finally {
                    if (logger != null) {
                        logger.exit();
                    }
                }

                if (tmpout.isEmpty()) {
                    tmpin.clear();
                    break;
                }
                // Make the output from this segment the input
                // for the next segment.
                //
                // Axes within a path can be either standard axes
                // or function axes. The standard axes guarantee that
                // their outputs contain no duplicates. Function Axes
                // do not, for example /**/eval(/**) will give repeats.
                //
                // XPath solves this by disallowing duplicate *nodes*,
                // but allowing duplicate atomic values. We'll do the
                // same.
                //
                // The complication if the data structure we're working
                // with *also* uses atomic values - a list containing
                // two copies of the same string. We can't distinguish
                // this, but we also can't identify it's key if it's
                // stored twice. In many non-Java languages (eg JS)
                // this is how it's going to be.
                // 
                List<Object> t = tmpout;
                tmpout = tmpin;
                tmpin = t;
            }
            out.addAll(tmpin);
            if (logger != null) {
                logger.log("output: " + tmpin.size() + " nodes");
                logger.enter();
                for (Object n : tmpin) {
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
