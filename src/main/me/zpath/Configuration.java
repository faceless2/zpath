package me.zpath;

import java.util.*;
import java.io.*;
import java.net.*;

/**
 * The Configuration is a shared resourece which configures how ZPath expressions
 * are handded. There is normally no need to deal with this class unless you
 * want to register custom functions or factories.
 */
public class Configuration {

    private static final Configuration CONFIG = new Configuration();

    private Set<NodeFactory> factories;
    private Map<String,Function> functions;
    private Logger logger;
    private boolean closed;
    private int contextIndex = -1;
    private List<Node> contextNodes;

    private Configuration() {
    }

    /** 
     * Create a new Configuration and initialize it with the same functions, factories and logger as the supplied supplied Configuration, 
     * @param config the configuration to copy
     */
    public Configuration(Configuration config) {
        functions = new LinkedHashMap<String,Function>(config.functions);
        factories = new LinkedHashSet<NodeFactory>(config.factories);
        logger = config.logger;
    }

    /**
     * Set a Logger to write debug messages to
     * @param logger the Logger, or null for no logging.
     */
    public void setLogger(Logger logger) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        this.logger = logger;
    }

    /**
     * Return the Logger set by {@link #setLogger}
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Register a new Function with this Configuration
     * @param function the function
     */
    public void registerFunction(Function function) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        functions.put(function.getName(), function);
    }

    /**
     * Register a new Factory with this Configuration. Factories will also be
     * loaded from any <code>META-INF/services/me.zpath.NodeFactory</code>
     * resources found in the classpath when this class is initialized.
     * @param factory the factory
     */
    public void registerFactory(NodeFactory factory) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        factories.add(factory);
    }

    /**
     * Find the function that matches the specified name
     * @param name the function name
     * @return the function, or null if not found.
     */
    public Function getFunction(String name) {
        return functions.get(name);
    }

    /**
     * Return all the functions registered with this Configuration
     * @return the functions
     */
    public Collection<Function> getFunctions() {
        return functions.values();
    }

    /**
     * Return all the factories registered with this Configuration
     * @return the factories
     */
    public Collection<NodeFactory> getFactories() {
        return factories;
    }

    /**
     * When evaluating a path against this context, set the context of this evaluation
     * to an index into a set of nodes. Used to resolve <code>index()</code> and <code>count()</code>
     */
    public void setContext(int index, List<Node> nodes) {
        this.contextIndex = index;
        this.contextNodes = nodes;
    }

    int getContextIndex() {
        return contextIndex;
    }

    List<Node> getContextNodes() {
        return contextNodes;
    }

    private void close() {
        if (!closed) {
            functions = Collections.<String,Function>unmodifiableMap(functions);
            factories = Collections.<NodeFactory>unmodifiableSet(factories);
        }
        closed = true;
    }

    /**
     * Convert the specified object to a Node suitable for passing in to {@link ZPath#evalNode}
     * @param object the object
     * @return a Node matching the object
     * @throws IllegalArgumentException if a Node cannot  be created from the object
     */
    public Node toNode(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Object is null");
        }
        Node node = null;
        if (object instanceof Node) {
            node = (Node)object;
        } else {
            for (NodeFactory factory : getFactories()) {
                node = factory.create(object, this);
                if (node != null) {
                    break;
                }
            }
        }
        if (node == null) {
            throw new IllegalArgumentException("Can't create Node from " + object);
        }
        return node;
    }

    /**
     * Get the value below which two numbers are considered identical
     */
    public double getMinDouble() {
        return 0.00000001;
    }

    /**
     * Return the default Configuration, which is read-only.
     * To make changes, create a new Configuration using this one as a base
     * @return the default Configuration
     */
    public static Configuration getDefault() {
        return CONFIG;
    }

    @SuppressWarnings("unchecked") private static <T> List<T> getServiceList(Class<T> cl) {
        List<T> list = new ArrayList<T>();
        Set<String> seen = new HashSet<String>();
        try {
            for (Enumeration<URL> e = Configuration.class.getClassLoader().getResources("META-INF/services/" + cl.getName());e.hasMoreElements();) {
                URL url = (URL)e.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "ISO-8859-1"));
                String classname;
                while ((classname=reader.readLine()) != null) {
                    classname = classname.replaceAll("#.*", "").trim();
                    if (classname.length() > 0 && seen.add(classname)) {
                        try {
                            list.add((T)Class.forName(classname).getDeclaredConstructor().newInstance());
                        } catch (Throwable x) {
                            if (!(x instanceof NoClassDefFoundError || x instanceof ClassNotFoundException || x.getCause() instanceof NoClassDefFoundError)) {
                                x.printStackTrace();
                            }
                        }
                    }
                }
                reader.close();
            }
        } catch (IOException x) { }
        return list;
    }

    /**
     * A Logger can be used to log parsing and evaluation of a ZPath, for debugging
     */
    public static interface Logger {
        /**
         * Increase the logging depth
         */
        public void enter();
        /**
         * Log a string
         */
        public void log(String s);
        /**
         * Decrease the logging depth
         */
        public void exit();

        /**
         * Create a default logger which logs to the specified output
         * @param out the appendable to log to
         */
        public static Logger create(Appendable out) {
            return new DefaultLogger(out);
        }
    }

    private static class DefaultLogger implements Logger {
        private final Appendable out;
        DefaultLogger(Appendable out) {
            this.out = out;
        }
        int depth;
        @Override public void enter() {
            depth++;
        }
        @Override public void exit() {
            depth--;
        }
        @Override public void log(String s) {
            try {
                for (int i=0;i<depth;i++) {
                    out.append("  ");
                }
                out.append(s);
                out.append('\n');
                if (out instanceof Flushable) {
                    ((Flushable)out).flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------

    static {
        CONFIG.functions = new LinkedHashMap<String,Function>();
        CONFIG.factories = new LinkedHashSet<NodeFactory>();
        CONFIG.factories.addAll(getServiceList(me.zpath.NodeFactory.class));

        // Core: eval, union, intersection, key, index, count
        CONFIG.registerFunction(new Function() {
            //
            // eval(...)               eval its argument
            //
            @Override public String getName() {
                return "eval";
            }
            @Override public boolean verify(List<Term> args) {
                return args.size() == 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, Configuration config) {
                args.get(0).eval(in, out, config);
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // union(...)               a union of all its arguments, removing duplicates
            //
            @Override public String getName() {
                return "union";
            }
            @Override public boolean verify(List<Term> args) {
                return true;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, Configuration config) {
                Set<Node> seen = new HashSet<Node>();
                List<Node> tmp = new ArrayList<Node>();
                for (Term t : args) {
                    for (Node node : t.eval(in, tmp, config)) {
                        if (seen.add(node)) {
                            out.add(node);
                        }
                    }
                    tmp.clear();
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // intersection(...)        an intersection of all its arguments, removing duplicates
            //
            @Override public String getName() {
                return "intersection";
            }
            @Override public boolean verify(List<Term> args) {
                return true;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                Set<Node> work = null;
                List<Node> tmp = new ArrayList<Node>();
                for (Term t : args) {
                    if (work == null) {
                        work = new LinkedHashSet<Node>(t.eval(in, tmp, config));
                    } else {
                        work.retainAll(t.eval(in, tmp, config));
                    }
                    tmp.clear();
                }
                out.addAll(work);
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // key()           return the name by which this node is typically accessed from its parent (ie string for maps/XML elements, int for arrays).
            // key(path)       for every node matching path, return name()
            //
            @Override public String getName() {
                return "key";
            }
            @Override public boolean verify(List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    Object s = node.key();
                    if (s != null) {
                        out.add(Node.create(s));
                    }
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // index()          return the index into the current nodeset of this node
            // index(path)      for every node matching path, if it can be accessed by an index from its parent, return that index.
            //
            @Override public String getName() {
                return "index";
            }
            @Override public boolean verify(List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                if (args.isEmpty()) {
                    if (config.getContextIndex() >= 0) {
                        out.add(Node.create(config.getContextIndex()));
                    }
                } else {
                    for (Node node : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                        Node parent = node.parent();
                        if (parent != null) {
                            int index = node.index();
                            if (index >= 0) {
                                out.add(Node.create(index));
                            }
                        }
                    }
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // count()          return the number of nodes in the current nodeset
            // count(path)      return the number of nodes matching path
            //
            public String getName() {
                return "count";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                if (args.isEmpty()) {
                    out.add(Node.create(config.getContextNodes().size()));
                } else {
                    int count = 0;
                    for (Node node : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                        count++;
                    }
                    out.add(Node.create(count));    // count(*) or *[index() + 1 == count()]
                }
            }
        });

        /*
        CONFIG.registerFunction(new Function() {
            //
            // prev()           return the node in the current nodeset with the previous index()
            //
            public String getName() {
                return "prev";
            }
            public boolean verify(List<Term> terms) {
                return terms.isEmpty();
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                List<Node> nodeset = config.getContextNodes();
                if (nodeset != null) {
                    int index = config.getContextIndex();
                    if (index > 0) {
                        out.add(nodeset.get(index - 1));
                    }
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            //
            // next()           return the node in the current nodeset with the next index()
            //
            public String getName() {
                return "next";
            }
            public boolean verify(List<Term> terms) {
                return terms.isEmpty();
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                List<Node> nodeset = config.getContextNodes();
                if (nodeset != null) {
                    int index = config.getContextIndex();
                    if (index >= 0 && index + 1 < nodeset.size()) {
                        out.add(nodeset.get(index + 1));
                    }
                }
            }
        });
        */


        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "min";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                double min = Double.NaN;
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        min = min == min ? Math.min(min, d) : d;
                    }
                }
                out.add(Node.create(min));
            }
        });
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "max";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                double max = Double.NaN;
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        max = max == max ? Math.max(max, d) : d;
                    }
                }
                out.add(Node.create(max));
            }
        });
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "sum";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                double sum = 0;
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        sum += d;
                    }
                }
                out.add(Node.create(sum));
            }
        });

        // Math functions
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "ceil";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        d = Math.ceil(d);
                        out.add(d == (int)d ? Node.create((int)d) : Node.create(d));
                    }
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "floor";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        d = Math.floor(d);
                        out.add(d == (int)d ? Node.create((int)d) : Node.create(d));
                    }
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "round";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    double d = node.doubleValue();
                    if (d == d) {
                        d = Math.round(d);
                        out.add(d == (int)d ? Node.create((int)d) : Node.create(d));
                    }
                }
            }
        });

        // Type conversion functions
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "type";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                boolean set = false;
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    set = true;
                    String s = node.type();
                    if (s == s) {
                        out.add(Node.create(s));
                    }
                }
                if (!set) {
                    out.add(Node.create("undefined"));
                }
            }
        });
        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "string";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    String s = node.stringValue();;
                    if (s != null) {
                        out.add(node);
                    } else {
                        double d = node.doubleValue();
                        if (d == d) {
                            if (d == (int)d) {
                                out.add(Node.create(Integer.toString((int)d)));
                            } else {
                                out.add(Node.create(Double.toString(d)));
                            }
                        }
                    }
                }
            }
        });

        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "num";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    String s;
                    double d = node.doubleValue();
                    if (d == d) {
                        out.add(node);
                    } else if ((s=node.stringValue()) != null) {
                        try {
                            d = Double.parseDouble(s);
                            if (d == d && !Double.isInfinite(d)) {
                                out.add(Node.create(d));
                            }
                        } catch (Exception e) { }
                    }
                }
            }
        });

        CONFIG.registerFunction(new Function() {
            public String getName() {
                return "encode";
            }
            public boolean verify(List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                StringBuilder sb = new StringBuilder();
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    String s = node.stringValue();;
                    if (s != null) {
                        encodeXML(s, true, sb);
                        out.add(Node.create(sb.toString()));
                        sb.setLength(0);
                    }
                }
            }
            private void encodeXML(String s, boolean attribute, StringBuilder sb) {
                int len = s.length();
                for (int i=0;i<len;) {
                    final int c = s.codePointAt(i);
                    if (c < 0x80) {      // ASCII range: test most common case first
                        if (c < 0x20 && (c != '\t' && c != '\r' && c != '\n')) {
                            // Illegal XML character, even encoded. Skip or substitute
                            sb.append("&#xfffd;");   // Unicode replacement character
                        } else {
                            switch(c) {
                              case '&':  sb.append("&amp;"); break;
                              case '>':  sb.append("&gt;"); break;
                              case '<':  sb.append("&lt;"); break;
                              case '\'':  if (attribute) { sb.append("&apos;"); break; }
                              case '\"':  if (attribute) { sb.append("&quot;"); break; }
                              default:   sb.append((char)c);
                            }
                        }
                    } else if ((c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff) {
                        // Illegal XML character, even encoded. Skip or substitute
                        sb.append("&#xfffd;");   // Unicode replacement character
                    } else {
                        sb.append("&#x");
                        sb.append(Integer.toHexString(c));
                        sb.append(';');
                    }
                    i += c < 0x10000 ? 1 : 2;
                }
            }
        });
        CONFIG.close();
    }

}
