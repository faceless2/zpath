package me.zpath;

import java.util.*;

interface Axis {

    void eval(Collection<Node> in, Collection<Node> out, Configuration config);

    /** @hidden */
    void dump(Configuration config);

    /** @hidden */
    static Axis axisName(final String name) {
        return new Axis() {
            @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
                for (Node node : in) {
                    Node n = node.get(name);
                    if (n != null) {
                        out.add(n);
                        if (config.isDebug()) {
                            config.debug("match: " + n);
                        }
                    } else if (config.isDebug()) {
                        config.debug("miss: " + n);
                    }
                }
            }
            @Override public String toString() {
                StringBuilder sb = new StringBuilder();
                for (int i=0;i<name.length();i++) {
                    char c = name.charAt(i);
                    if (c == '\\' || Term.DELIMITERS.indexOf(c) >= 0) {
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
                return sb.toString();
            }
            @Override public void dump(Configuration config) {
                config.debug("axis: child name=\"" + toString() + "\"");
            }
        };
    }

    /** @hidden */
    static Axis axisIndex(final int index) {
        return new Axis() {
            @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
                for (Node node : in) {
                    Node n = node.get(index);
                    if (n != null) {
                        out.add(n);
                        if (config.isDebug()) {
                            config.debug("match: " + n);
                        }
                    } else if (config.isDebug()) {
                        config.debug("miss: " + n);
                    }
                }
            }
            @Override public String toString() {
                return Integer.toString(index);
            }
            @Override public void dump(Configuration config) {
                config.debug("axis: child index=" + index);
            }
        };
    }

    /** @hidden */
    static Axis ANYCHILD = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, Configuration config) {
            for (Node node : in) {
                Iterator<Node> i = node.children();
                if (i != null) {
                    while (i.hasNext()) {
                        Node n = i.next();
                        out.add(n);
                        if (config.isDebug()) {
                            config.debug("match: " + n);
                        }
                    }
                }
            }
        }
        @Override public String toString() {
            return "*";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: any child");
        }
    };

    /** @hidden */
    static Axis SELFORANYDESCENDENT = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            Stack<Node> stack = new Stack<Node>();
            for (Node node : in) {
                // Iterative depth first traversal from node
                stack.push(node);
                while (!stack.isEmpty()) {
                    Node n = stack.pop();
                    Iterator<Node> i = n.children();
                    if (i != null) { // Add to stack in reverse order
                        List<Node> temp = new ArrayList<Node>();
                        while (i.hasNext()) {
                            temp.add(i.next());
                        }
                        for (int j=temp.size()-1;j>=0;j--) {
                            stack.push(temp.get(j));
                        }
                    }
                    out.add(n);
                    if (config.isDebug()) {
                        if (config.isDebug()) {
                            config.debug("match: " + n);
                        }
                    }
                }
            }
        }
        @Override public String toString() {
            return "**";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: self or any descendent");
        }
    };

    /** @hidden */
    static Axis PARENT = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            for (Node node : in) {
                Node parent = node.parent();
                if (parent != null) {
                    out.add(parent);
                    if (config.isDebug()) {
                        config.debug("match: " + parent);
                    }
                }
            }
        }
        @Override public String toString() {
            return "..";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: parent");
        }
    };

    /** @hidden */
    static Axis ROOT = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            Node root = in.iterator().next();
            while (root.parent() != null) {
                root = root.parent();
            }
            if (config.isDebug()) {
                config.debug("match: " + root);
            }
            out.add(root);
        }
        @Override public String toString() {
            return "/";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: root");
        }
    };

    /** @hidden */
    static Axis SELF = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            for (Node node : in) {
                out.add(node);
                if (config.isDebug()) {
                    config.debug("match: " + node);
                }
            }
        }
        @Override public String toString() {
            return ".";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: self\n");
        }
    };

    /** @hidden */
    static Axis KEY = new Axis() {
        @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            for (Node node : in) {
                if (node.key() != null) {
                    out.add(Node.create(node.key()));
                    if (config.isDebug()) {
                        config.debug("match: \"" + node.key() + "\"");
                    }
                } else if (node.index() >= 0) {
                    out.add(Node.create(node.index()));
                    if (config.isDebug()) {
                        config.debug("match: " + node.index());
                    }
                } else {
                    if (config.isDebug()) {
                        config.debug("miss");
                    }
                }
            }
        }
        @Override public String toString() {
            return "@";
        }
        @Override public void dump(Configuration config) {
            config.debug("axis: key");
        }
    };

    /** @hidden */
    static Axis axisMatch(final Term term) {
        return new Term() {
            @Override public void eval(final Collection<Node> in, final Collection<Node> out, final Configuration config) {
                List<Node> tmp = new ArrayList<Node>();
                for (Node node : in) {
                    tmp.clear();
                    term.eval(Collections.<Node>singleton(node), tmp, config);
                    boolean match = !tmp.isEmpty() && tmp.iterator().next().booleanValue();
                    if (match) {
                        out.add(node);
                    }
                    if (config.isDebug()) {
                        if (match) {
                            config.debug("match: " + node);
                        } else {
                            config.debug("miss");
                        }
                    }
                }
            }
            @Override public String toString() {
                return "[" + term + "]";
            }
            @Override public void dump(Configuration config) {
                config.debug("axis: matches " + term);
                Configuration copy = config.debugIndent();
                StringBuilder sb = new StringBuilder();
                copy.setDebug(sb);
                term.dump(copy);
                String s = sb.toString();
                s = s.substring(s.indexOf('\n') + 1, s.length() - 1);
                config.debug(s);
            }
        };
    }

}
