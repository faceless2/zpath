package me.zpath;

import java.util.*;
import java.io.*;
import java.net.*;

/**
 * The Configuration is a shared resourece which configures how ZPath expressions
 * are handlded. There is normally no need to deal with this class unless you
 * want to register custom functions or factories.
 */
public class Configuration {

    private static final Configuration CONFIG = new Configuration();

    private Set<EvalFactory> factories;
    private Set<Function> functions;
    private Logger logger;
    private boolean closed;

    private Configuration() {
    }

    /** 
     * Create a new Configuration and initialize it with the same functions, factories and logger as the supplied supplied Configuration, 
     * @param config the configuration to copy
     */
    public Configuration(Configuration config) {
        functions = new LinkedHashSet<Function>(config.functions);
        factories = new LinkedHashSet<EvalFactory>(config.factories);
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
     * Return all the functions registered with this Configuration
     * @return the functions
     */
    public Collection<Function> getFunctions() {
        return functions;
    }

    /**
     * Return all the factories registered with this Configuration
     * @return the factories
     */
    public Collection<EvalFactory> getFactories() {
        return factories;
    }

    private void close() {
        if (!closed) {
            functions = Collections.<Function>unmodifiableSet(functions);
            factories = Collections.<EvalFactory>unmodifiableSet(factories);
        }
        closed = true;
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
        CONFIG.functions = new LinkedHashSet<Function>();
        CONFIG.factories = new LinkedHashSet<EvalFactory>();
        CONFIG.factories.addAll(getServiceList(me.zpath.EvalFactory.class));

        // Core: eval, union, intersection, key, index, count
        CONFIG.getFunctions().add(new Function() {
            @Override public boolean matches(String name) {
                return "eval".equals(name);
            }
            @Override public boolean verify(String name, List<Term> args) {
                return args.size() == 1;
            }
            @Override public void eval(String name, List<Term> args, List<Object> in, List<Object> out, EvalContext context) {
                args.get(0).eval(in, out, context);
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // union(...)               a union of all its arguments, removing duplicates
            //
            @Override public boolean matches(String name) {
                return "union".equals(name);
            }
            @Override public boolean verify(String name, List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, EvalContext context) {
                Set<Object> seen = new HashSet<Object>();
                List<Object> tmp = new ArrayList<Object>();
                for (Term t : args) {
                    for (Object node : t.eval(in, tmp, context)) {
                        if (seen.add(node)) {
                            out.add(node);
                        }
                    }
                    tmp.clear();
                }
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // intersection(...)        an intersection of all its arguments, removing duplicates
            //
            @Override public boolean matches(String name) {
                return "intersection".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                Set<Object> work = null;
                List<Object> tmp = new ArrayList<Object>();
                for (Term t : args) {
                    if (work == null) {
                        work = new LinkedHashSet<Object>(t.eval(in, tmp, context));
                    } else {
                        work.retainAll(t.eval(in, tmp, context));
                    }
                    tmp.clear();
                }
                out.addAll(work);
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // key()           return the name by which this node is typically accessed from its parent (ie string for maps/XML elements, int for arrays).
            // key(path)       for every node matching path, return name()
            //
            @Override public boolean matches(String name) {
                return "key".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    Object s = context.key(node);
                    if (s != null) {
                        out.add(s);
                    }
                }
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // index()          return the index into the current nodeset of this node
            // index(path)      for every node matching path, if it can be accessed by an index from its parent, return that index.
            //
            @Override public boolean matches(String name) {
                return "index".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                if (args.isEmpty()) {
                    if (context.getContextIndex() >= 0) {
                        out.add(context.getContextIndex());
                    }
                } else {
                    for (Object node : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                        Object parent = context.parent(node);
                        if (parent != null) {
                            int index = context.index(node);
                            if (index >= 0) {
                                out.add(index);
                            }
                        }
                    }
                }
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // count()          return the number of nodes in the current nodeset
            // count(path)      return the number of nodes matching path
            //
            public boolean matches(String name) {
                return "count".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                if (args.isEmpty()) {
                    if (context.getContext() != null) {
                        out.add(context.getContext().size());
                    }
                } else {
                    int count = 0;
                    for (Object node : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                        count++;
                    }
                    out.add(count);    // count(*) or *[index() + 1 == count()]
                }
            }
        });
        CONFIG.getFunctions().add(new Function() {
            //
            // is-first()       return true if index into the current nodeset of this node == 0
            // is-last()          return the index into the current nodeset of this node == count()-1
            //
            @Override public boolean matches(String name) {
                return "is-first".equals(name) || "is-last".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() == 0;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                if (context.getContextIndex() >= 0 && context.getContext() != null) {
                    out.add(context.getContextIndex() == ("is-first".equals(name) ? 0 : context.getContext().size() - 1));
                }
            }
        });
        /*
        CONFIG.getFunctions().add(new Function() {
            //
            // match(index)     return the specified node from the current nodeset. TODO is this useful?
            //
            public boolean matches(String name) {
                return "match".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() == 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                List<Object> context = context.getContext();
                if (context != null) {
                    List<Object> tmp = new ArrayList<Object>();
                    args.get(0).eval(in, tmp, context);
                    if (tmp.size() > 0) {
                        double d = tmp.doubleValue();
                        if (d == d) {
                            int i = (int)d;
                            if (i >= 0 && i < context.size()) {
                                out.add(context.get(i));
                            }
                        }
                    }
                }
            }
        });
        */

        CONFIG.getFunctions().add(new Function() {
            //
            // prev()           if this node can be accessed from its parent with an index, evaluates as the the node accessed from the previous index.
            // prev(x)          if the specified nodes can be accessed from their parents with an index, evaluates as the the node accessed from the previous index.
            // next()           if this node can be accessed from its parent with an index, evaluates as the the node accessed from the next index.
            // next(x)          if the specified nodes can be accessed from their parent with an index, evaluates as the the node accessed from the next index.
            //
            public boolean matches(String name) {
                return "prev".equals(name) || "next".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.isEmpty();
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    int index = context.index(node);
                    if (index >= 0) {
                        for (Object o : context.get(context.parent(node), index + ("prev".equals(name) ? -1 : 1))) {
                            out.add(o);
                        }
                    }
                }
            }
        });

        // Math functions
        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "min".equals(name) || "max".equals(name) || "sum".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                double v = "sum".equals(name) ? 0 : Double.NaN;
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    double d = Expr.doubleValue(context, node);
                    if (d == d) {
                        if (v != v) {
                            v = d;
                        } else if ("min".equals(name)) {
                            v = Math.min(v, d);
                        } else if ("max".equals(name)) {
                            v = Math.max(v, d);
                        } else if ("sum".equals(name)) {
                            v = v + d;
                        }
                    }
                }
                out.add(v);
            }
        });
        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "ceil".equals(name) || "floor".equals(name) || "round".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    double d = Expr.doubleValue(context, node);
                    if (d == d) {
                        if ("ceil".equals(name)) {
                            d = Math.ceil(d);
                        } else if ("floor".equals(name)) {
                            d = Math.floor(d);
                        } else {
                            d = Math.round(d);
                        }
                        out.add(d == (int)d ? Integer.valueOf((int)d) : Double.valueOf(d));
                    }
                }
            }
        });

        // Type conversion functions
        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "type".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                boolean set = false;
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    set = true;
                    String s;
                    if (node == null) {
                        s = "null";
                    } else if (node instanceof String) {
                        s = "string";
                    } else if (node instanceof Number) {
                        s = "number";
                    } else if (node instanceof Boolean) {
                        s = "boolean";
                    } else {
                        s = context.type(node);
                        if (s == null) {
                            s = node.getClass().getName();
                        }
                    }
                    out.add(s);
                }
                if (!set) {
                    out.add("undefined");
                }
            }
        });
        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "string".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    String s = Expr.stringValue(context, node);
                    if (s == null) {
                        double d = Expr.doubleValue(context, node);
                        if (d == d) {
                            if (d == (int)d) {
                                s = Integer.toString((int)d);
                            } else {
                                s = Double.toString(d);
                            }
                        }
                    }
                    if (s != null) {
                        out.add(node);
                    }
                }
            }
        });

        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "num".equals(name);
            }
            public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    double d = Expr.doubleValue(context, node);
                    if (d != d) {
                        String s = Expr.stringValue(context, node);
                        if (s != null) {
                            try {
                                d = Double.parseDouble(s);
                            } catch (Exception e) { }
                        }
                    }
                    if (d == (int)d) {
                        out.add(Integer.valueOf((int)d));
                    } else if (d == d && !Double.isInfinite(d)) {
                        out.add(Double.valueOf(d));
                    }
                }
            }
        });

        CONFIG.getFunctions().add(new Function() {
            public boolean matches(String name) {
                return "encode".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> terms) {
                return terms.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                StringBuilder sb = new StringBuilder();
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    String s = Expr.stringValue(context, node);
                    if (s != null) {
                        encodeXML(s, true, sb);
                        out.add(sb.toString());
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
