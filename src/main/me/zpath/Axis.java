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
     * @param config the configuration
     */
    List<Node> eval(List<Node> in, List<Node> out, Configuration config);

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
            @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
                final Configuration.Logger logger = config.getLogger();
                Set<Node> seen = new HashSet<Node>(out);
                for (Node node : in) {
                    if (seen.add(node)) {
                        Iterator<Node> i = node.get(name != null ? name : Integer.valueOf(index));
                        if (i != null && i.hasNext()) {
                            int c = name != null ? index : ANYINDEX;
                            while (i.hasNext()) {
                                Node n = i.next();
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
                        } else if (logger != null) {
                            logger.log("miss: " + node);
                        }
                    }
                }
                return out;
            }
            @Override public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("axis-key(");
                if (name != null) {
                    if (name == Node.WILDCARD) {
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
        @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
            final Configuration.Logger logger = config.getLogger();
            Stack<Node> stack = new Stack<Node>();
            Set<Node> seen = new HashSet<Node>(out);
            for (Node node : in) {
                // Iterative depth first traversal from node
                stack.push(node);
                while (!stack.isEmpty()) {
                    Node n = stack.pop();
                    Iterator<Node> i = n.get("*");
                    if (i != null) { // Add to stack in reverse order
                        List<Node> temp = new ArrayList<Node>();
                        while (i.hasNext()) {
                            temp.add(i.next());
                        }
                        for (int j=temp.size()-1;j>=0;j--) {
                            stack.push(temp.get(j));
                        }
                    }
                    if (seen.add(n)) {
                        out.add(n);
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
        @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
            final Configuration.Logger logger = config.getLogger();
            Set<Node> seen = new HashSet<Node>(out);
            for (Node node : in) {
                Node parent = node.parent();
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
        @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
            Node root = in.iterator().next();
            while (root.parent() != null) {
                root = root.parent();
            }
            if (config.getLogger() != null) {
                config.getLogger().log("match: " + root);
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
        @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
            Set<Node> seen = new HashSet<Node>(out);
            final Configuration.Logger logger = config.getLogger();
            for (Node node : in) {
                if (seen.add(node)) {
                    out.add(node);
                    if (logger != null) {
                        logger.log("match: " + node);
                    }
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
            @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
                final Configuration.Logger logger = config.getLogger();
                List<Node> tmp = new ArrayList<Node>();
                int oldindex = config.getContextIndex();
                List<Node> oldcontext = config.getContextNodes();
                List<Node> contextNodes = Collections.<Node>unmodifiableList(in);
                for (int i=0;i<in.size();i++) {
                    Node node = in.get(i);
                    tmp.clear();
                    config.setContext(i, contextNodes);
                    term.eval(Collections.<Node>singletonList(node), tmp, config);
                    boolean match = !tmp.isEmpty() && (term.isPath() || tmp.iterator().next().booleanValue());
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
                config.setContext(oldindex, oldcontext);
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
