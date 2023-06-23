package me.zpath;

import java.util.*;

/**
 * The Axis interface is how we move from one set of nodes to the next - the term is from XPath
 */
interface Axis {

    final static int ANYINDEX = -2;

    /**
     * Evaluate the set of nodes supplied in "in", move along this axis and add matching nodes to "out"
     * @param in the list of nodes in our input set - read only
     * @param out the list of nodes we are to add to
     * @param context the contexturation
     */
    List<Object> eval(List<Object> in, List<Object> out, EvalContext context);

    default void log(Configuration.Logger logger) {
        logger.log(toString());
    }

    /**
     * The "travel to a matching child of the input node" axis
     *
     * Several possibilities
     *   *              (WILDCARD, ANYINDEX)    match key WILDCARD, all items
     *   name           (name, ANYINDEX)        match key "name", all items
     *   name#1         (name, 1)               match key "name", second item
     *   #1             (null, 1)               match key 1
     *   *#1            (WILDCARD, 1)           match key WILDCARD, first item
     *   #1#2           (1, 2)                  match key 1, third item
     *
     * @param name if a name is used as part of the axis, wil
     * @hidden
     */
    static Axis axisKey(final Object name, final int index) {
        return new Axis() {
            @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
                final Configuration.Logger logger = context.getLogger();
                // Our input is guaranteed to have no duplicates, which means
                // the output can have no duplicates as a child can only belong
                // to one parent.
                for (Object node : in) {
                    int c = name != null ? index : ANYINDEX;
                    for (Object n : context.get(node, name != null ? name : Integer.valueOf(index))) {
                        if (c == ANYINDEX || c-- == 0) {
                            out.add(n);
                            if (logger != null) {
                                logger.log("match: " + n);
                            }
                            if (c != ANYINDEX) {
                                break;
                            }
                        }
                    }
                }
                assert new HashSet<Object>(out).size() == out.size() : "Duplicates";
                return out;
            }
            @Override public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("axis-key(");
                if (name != null) {
                    if (name == EvalContext.WILDCARD) {
                        sb.append("key=*");
                    } else {
                        sb.append("key=\"");
                        String n = name.toString();
                        for (int i=0;i<n.length();i++) {
                            char c = n.charAt(i);
                            if (c == '\\' || ZPath.PATH_DELIMITERS.indexOf(c) >= 0) {
                                if (c == '\n') {
                                    sb.append("\\n");
                                } else if (c == '\r') {
                                    sb.append("\\r");
                                } else if (c == '\t') {
                                    sb.append("\\t");
                                } else {
                                    sb.append("\\");
                                    sb.append(c);
                                }
                            } else {
                                sb.append(c);
                            }
                        }
                        sb.append('"');
                    }
                }
                if (index != ANYINDEX) {
                    if (name != null) {
                        sb.append(" index=");
                    } else {
                        sb.append("index=");
                    }
                    sb.append(index);
                }
                sb.append(')');
                return sb.toString();
            }
        };
    }

    /**
     * The "travel to the input node or any of its descendents" axis
     * @hidden
     */
    static Axis SELFORANYDESCENDENT = new Axis() {
        @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
            final Configuration.Logger logger = context.getLogger();
            Stack<Object> stack = new Stack<Object>();
            // Input has no duplicates, tree descent for each node will
            // contain no duplicates, but tree descent for more than
            // one node could contain duplicates.
            Set<Object> seen = in.size() < 2 ? null : new HashSet<Object>(out); // optimization - don't track seen if list is empty
            List<Object> temp = new ArrayList<Object>();
            for (Object node : in) {
                // Iterative depth first traversal from node
                stack.push(node);
                while (!stack.isEmpty()) {
                    Object n = stack.pop();
                    temp.clear();
                    for (Object o : context.get(n, EvalContext.WILDCARD)) {
                        temp.add(o);
                    }
                    for (int j=temp.size()-1;j>=0;j--) {
                        stack.push(temp.get(j));
                    }
                    if (seen == null || seen.add(n)) {
                        out.add(n);
                    }
                    if (logger != null) {
                        logger.log("match: " + n);
                    }
                }
            }
            assert new HashSet<Object>(out).size() == out.size() : "Duplicates";
            return out;
        }
        @Override public String toString() {
            return "axis-self-or-descendent()";
        }
    };

    /**
     * The "travel to the parent node" axis
     * @hidden
     */
    static Axis PARENT = new Axis() {
        @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
            final Configuration.Logger logger = context.getLogger();
            Set<Object> seen = new HashSet<Object>(out);
            for (Object node : in) {
                Object parent = context.parent(node);
                if (parent != null && seen.add(parent)) {
                    out.add(parent);
                    if (logger != null) {
                        logger.log("match: " + parent);
                    }
                }
            }
            return out;
        }
        @Override public String toString() {
            return "axis-parent()";
        }
    };

    /**
     * The "travel to the root node" axis
     * @hidden
     */
    static Axis ROOT = new Axis() {
        @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
            Object root = in.iterator().next();
            Object o;
            while ((o=context.parent(root)) != null) {
                root = o;
            }
            if (context.getLogger() != null) {
                context.getLogger().log("match: " + root);
            }
            out.add(root);
            return out;
        }
        @Override public String toString() {
            return "axis-root()";
        }
    };

    /**
     * The "travel to the input node" axis
     * @hidden
     */
    static Axis SELF = new Axis() {
        @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
            // Input has no duplicates, so output can have no duplicates
            final Configuration.Logger logger = context.getLogger();
            for (Object node : in) {
                out.add(node);
                if (logger != null) {
                    logger.log("match: " + node);
                }
            }
            return out;
        }
        @Override public String toString() {
            return "axis-self()";
        }
    };

    /**
     * The "travel to the any input nodes that match the expression" axis
     * @hidden
     */
    static Axis axisMatch(final Term term) {
        return new Term() {
            @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
                final Configuration.Logger logger = context.getLogger();
                List<Object> tmp = new ArrayList<Object>();
                int oldindex = context.getContextIndex();
                List<Object> oldcontext = context.getContext();
                List<Object> contextObjects = Collections.<Object>unmodifiableList(in);
                for (int i=0;i<in.size();i++) {
                    Object node = in.get(i);
                    context.setContext(i, contextObjects);
                    tmp.clear();
                    term.eval(Collections.<Object>singletonList(node), tmp, context);
                    boolean match = !tmp.isEmpty() && (term.isPath() || Expr.booleanValue(context, tmp.iterator().next()));
                    if (match) {
                        out.add(node);
                    }
                    if (logger != null) {
                        if (match) {
                            logger.log("match: " + node);
                        } else {
                            logger.log("miss: " + node);
                        }
                    }
                }
                context.setContext(oldindex, oldcontext);
                return out;
            }
            @Override public String toString() {
                return "axis-match(" + term + ")";
            }
            @Override public void log(Configuration.Logger logger) {
                super.log(logger);
                logger.enter();
                term.log(logger);
                logger.exit();
            }
        };
    }

}
