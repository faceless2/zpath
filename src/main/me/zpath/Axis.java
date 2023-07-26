package me.zpath;

import java.util.*;

/**
 * The Axis interface is how we move from one set of nodes to the next - the term is from XPath
 */
interface Axis {

    /**
     * A value that can be passed into {@link #axisKey} to mean "applies to any index"
     */
    public final static int ANYINDEX = -2;

    /**
     * Evaluate the set of nodes supplied in "in", move along this axis and add matching nodes to "out"
     * @param in the list of nodes in our input set - read only
     * @param out the list of nodes we are to add to
     * @param context the contexturation
     */
    List<Object> eval(List<Object> in, List<Object> out, EvalContext context);

    /**
     * Write this object to the specified Logger
     * @param logger the logger
     */
    default void log(Configuration.Logger logger) {
        logger.log(toString());
    }

    /**
     * The "travel to a matching child of the input node" axis.
     * Several possibilities
     * <table>
     * <tr><th>*</th><td>(WILDCARD, ANYINDEX)</td><td>match key WILDCARD, all items</td></tr>
     * <tr><th>name</th><td>(name, ANYINDEX)</td><td>match key "name", all items</td></tr>
     * <tr><th>name#1</th><td>(name, 1)</td><td>match key "name", second item</td></tr>
     * <tr><th>#1</th><td>(null, 1)</td><td>match key 1</td></tr>
     * <tr><th>*#1</th><td>(WILDCARD, 1)</td><td>match key WILDCARD, first item</td></tr>
     * <tr><th>#1#2</th><td>(1, 2)</td><td>match key 1, third item</td></tr>
     * </table>
     * @param name if a name is used as part of the axis, wil
     * @hidden
     */
    static Axis axisKey(final Object name, final int index) {
        return new Axis() {
            @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
                final Configuration.Logger logger = context.getLogger();
                // Duplicate handling: assuming a tree where every primitive value
                // is distinct, because the parents have no duplicate, children
                // will have no duplicates. However if values are shared (primitive
                // or complex) then the output could have duplicates. Primitives
                // are OK, others are invalid. So allow dups.
                //
                for (Object node : in) {
                    int c = name != null ? index : ANYINDEX;
                    for (Object n : context.get(node, name != null ? name : Integer.valueOf(index))) {
                        if (n == null) {
                            n = EvalContext.NULL;
                        }
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
            // Duplicate handling: assuming a tree where every primitive value
            // is distinct, there will be no duplicates if in.size() == 1.
            // If primitives are duplicated it's the same for axisKey, and
            // if complex items are dups it's cyclic and things crash.
            //
            // However if in.size > 1 then duplicates of complex items
            // are possible: div/** would exhibit this if model had nested divs.
            // So find those, but only do so for non-primitives, because it's
            // faster and will match other languages better.
            //
            // Note if we traverse and hit a complex item we've seen we can
            // stop: if item is a duplicate, its children will be too.
            //
            // Choice of "seen" doesn't matter; only items in it will be maps/lists etc.
            // Identity is probably faster than Hash
            Set<Object> seen = Collections.<Object>newSetFromMap(new IdentityHashMap<Object,Boolean>());
            List<Object> temp = new ArrayList<Object>();
            for (Object node : in) {
                // Iterative depth first traversal from node
                stack.push(node);
                while (!stack.isEmpty()) {
                    Object n = stack.pop();
                    if (seen == null || !context.isParent(n) || seen.add(n)) {
                        out.add(n);
                        temp.clear();
                        for (Object o : context.get(n, EvalContext.WILDCARD)) {
                            if (o == null) {
                                o = EvalContext.NULL;
                            }
                            temp.add(o);
                        }
                        for (int j=temp.size()-1;j>=0;j--) {
                            stack.push(temp.get(j));
                        }
                    }
                    if (logger != null) {
                        logger.log("match: " + n);
                    }
                }
            }
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
            // Choice of "seen" doesn't matter; only items in it will be maps/lists etc.
            // Identity is probably faster than Hash
            Set<Object> seen = Collections.<Object>newSetFromMap(new IdentityHashMap<Object,Boolean>());
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
                    boolean match = false;
                    if (!tmp.isEmpty()) {
                        Object n = tmp.get(0);
                        match = n == EvalContext.NULL || context.value(n) == null || Expr.booleanValueRequired(context, n);
                    }
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
