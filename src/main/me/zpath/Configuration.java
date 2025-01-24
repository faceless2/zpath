package me.zpath;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;

/**
 * The Configuration is a shared resourece which configures how ZPath expressions
 * and ZTemplates are handled.
 */
public class Configuration {

    private static final Set<Function> FUNCTIONS = new LinkedHashSet<Function>();
    private static final Set<EvalFactory> FACTORIES = new LinkedHashSet<EvalFactory>();
    private static final Map<String,Pattern> PATTERNCACHE = new LinkedHashMap<String,Pattern>(32, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String,Pattern> eldest) {
            return size() > 64;
        }
    };

    private Set<EvalFactory> factories;
    private Set<Function> functions;
    private Logger logger;
    private Locale locale = Locale.getDefault();
    private Includer includer = null;
    private int maxiterations = 1000000;
    private int maxdepth = 3;
    private boolean htmlEscape = true;
    private long maxbytes = 1024*1024*10;               // 10MB
    private double mindouble = 0.00000001;

    /**
     * Create a new Configuration
     */
    public Configuration() {
        functions = new LinkedHashSet<Function>(FUNCTIONS);
        factories = new LinkedHashSet<EvalFactory>(FACTORIES);
    }

    /** 
     * Create a new Configuration and initialize it with the same functions, factories and logger as the supplied supplied Configuration, 
     * @param config the configuration to copy
     */
    public Configuration(Configuration config) {
        functions = new LinkedHashSet<Function>(config.functions);
        factories = new LinkedHashSet<EvalFactory>(config.factories);
        logger = config.logger;
        locale = config.locale;
        includer = config.includer;
        maxiterations = config.maxiterations;
        maxdepth = config.maxdepth;
        htmlEscape = config.htmlEscape;
        maxbytes = config.maxbytes;
        mindouble = config.mindouble;
    }

    /**
     * Set a Logger to write debug messages to
     * @param logger the Logger, or null for no logging.
     * @return this
     */
    public Configuration setLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Return the Logger set by {@link #setLogger}
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Set the Locale to be used for locale-specific operations
     * @param locale, or null to use the system default
     * @return this
     */
    public Configuration setLocale(Locale locale) {
        this.locale = locale == null ? Locale.getDefault() : locale;
        return this;
    }

    /**
     * Return the Locale set by {@link #setLocale}
     * @return the locale
     */
    public Locale getLocale() {
        return locale;
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

    /**
     * Get the value below which two numbers are considered identical
     * @return the minimum double 
     */
    public double getMinDouble() {
        return mindouble;
    }

    /** 
     * Set the value below which two double-precision numbers are considered identical
     * @param d the minimum double - defaults to 0.00000001 to catch rounding error
     * @return this
     */
    public Configuration setMinDouble(double d) {
        if (d == d && !Double.isInfinite(d) && d >= 0) {
            this.mindouble = d;
        }
        return this;
    }

    /**
     * Return the maximum number of iterations that a ZTemplate can cycle
     * for before failing.
     * @return the described value
     */
    public int getTemplateMaxIterations() {
        return maxiterations;
    }

    /**
     * Set the maximum nuber of iterations that a ZTemplate can cycle
     * for before failing.
     * @param maxiterations the maximum iterations, which defaults to 1000000.
     * @return this
     */
    public Configuration setTemplateMaxIterations(int maxiterations) {
        if (maxiterations > 0) {
            this.maxiterations = maxiterations;
        }
        return this;
    }

    /**
     * Set the maximum number of bytes that can be written by a {@link ZTemplate}
     * before it fails.
     * @param maxbytes the maximum number of bytes that can be written by a template, which defaults to 10485760 (10MB)
     * @return this
     */
    public Configuration setTemplateMaxOutputSize(long maxbytes) {
        if (maxbytes > 0) {
            this.maxbytes = maxbytes;
        }
        return this;
    }

    /**
     * Return the maximum number of bytes that can be written by a {@link ZTemplate}, as set by {@link #setTemplateMaxOutputSize}
     * @return the maximum number of bytes
     */
    public long getTemplateMaxOutputSize() {
        return maxbytes;
    }

    /**
     * Return the maximum depth a chain of includes into a ZTemplate can be before failing
     * @return the maximum depth
     */
    public int getTemplateMaxIncludeDepth() {
        return maxdepth;
    }

    /**
     * Set the maximum depth a chain of includes into a ZTemplate can be before failing
     * @param depth the maximum depth;
     * @return this
     */
    public Configuration setTemplateMaxIncludeDepth(int depth) {
        this.maxdepth = depth;
        return this;
    }

    /**
     * Set whether ZTemplates should escape any ZPath string expresions in a way
     * that makes them suitable for HTML. The default is <code>true</code>
     * @param htmlEscape whether to escape Strings written to the output to make them suitable for HTML
     * @return this
     */
    public Configuration setTemplateHTMLEscape(boolean htmlEscape) {
        this.htmlEscape = htmlEscape;
        return this;
    }

    /**
     * Return the "HTML escape" flag as set by {@link #setTemplateHTMLEscape}
     * @return the HTML escape flag
     */
    public boolean isTemplateHTMLEscape() {
        return htmlEscape;
    }

    /**
     * Set the Includer to use when including content into ZTemplates created by this Configuration.
     * The default is <code>null</code>.
     * @param includer the includer, or null to disallow inclusions.
     * @return this
     */
    public Configuration setTemplateIncluder(Includer includer) {
        this.includer = includer;
        return this;
    }

    /**
     * Return the Includer set by {@link #setTemplateIncluder}.
     * @return the includer
     */
    public Includer getTemplateIncluder() {
        return includer;
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
                            if (!(x instanceof NoClassDefFoundError || x instanceof ClassNotFoundException || x.getCause() instanceof NoClassDefFoundError || x.getCause() instanceof ClassNotFoundException)) {
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
         * @param value the string to log
         */
        public void log(String value);
        /**
         * Decrease the logging depth
         */
        public void exit();

        /**
         * Create a default logger which logs to the specified output
         * @param out the appendable to log to
         * @return the logger
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
        FACTORIES.addAll(getServiceList(me.zpath.EvalFactory.class));

        // Core: union, intersection, key, index, count
        FUNCTIONS.add(new Function() {
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
        FUNCTIONS.add(new Function() {
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
        FUNCTIONS.add(new Function() {
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
                for (Object n : in) {
                    List<Object> in1 = Collections.<Object>singletonList(n);
                    for (Object node : args.isEmpty() ? in1 : args.get(0).eval(in1, new ArrayList<Object>(), context)) {
                        Object s = context.key(node);
                        if (s != null) {
                            out.add(s);
                        }
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            //
            // value()          return the primitive values of the current nodeset
            // value(path)      for every node matching path, return its primitive value
            //
            public boolean matches(String name) {
                return "value".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                boolean set = false;
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    Object o = context.value(node);
                    if (o == null) {
                        o = EvalContext.NULL;
                    }
                    out.add(o);
                }
            }
        });
        FUNCTIONS.add(new Function() {
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
                    } else {
                        int count = 0;
                        for (Object n : in) {
                            out.add(count++);
                        }
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
        FUNCTIONS.add(new Function() {
            //
            // count()          return the number of nodes in the current nodeset
            // count(path)      return the number of nodes matching path
            //
            public boolean matches(String name) {
                return "count".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                if (args.isEmpty()) {
                    if (context.getContext() != null) {
                        out.add(context.getContext().size());   //a/b[count()]
                    } else {
                        for (Object node : in) {
                            out.add(1);                         // a/b/count()
                        }
                    }
                } else {                                        // count(*)
                    ArrayList<Object> tmp = new ArrayList<Object>();
                    Term term = args.get(0);
                    for (Object node : in) {
                        List<Object> in1 = Collections.<Object>singletonList(node);
                        int count = 0;
                        for (Object n : term.eval(in1, tmp, context)) {
                            count++;
                        }
                        out.add(count);
                        tmp.clear();
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            //
            // is-first()       return true if index into the current nodeset of this node == 0
            // is-last()        return the index into the current nodeset of this node == count()-1
            //
            @Override public boolean matches(String name) {
                return "is-first".equals(name) || "is-last".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() == 0;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                if (context.getContextIndex() >= 0 && context.getContext() != null) {   // a[is-first()]
                    out.add(context.getContextIndex() == ("is-first".equals(name) ? 0 : context.getContext().size() - 1));
                } else {
                    out.add(true);      // a/b/is-first()
                }
            }
        });

        FUNCTIONS.add(new Function() {
            //
            // prev()           if this node can be accessed from its parent with an index, evaluates as the the node accessed from the previous index.
            // prev(x)          if the specified nodes can be accessed from their parents with an index, evaluates as the the node accessed from the previous index.
            // next()           if this node can be accessed from its parent with an index, evaluates as the the node accessed from the next index.
            // next(x)          if the specified nodes can be accessed from their parent with an index, evaluates as the the node accessed from the next index.
            //
            public boolean matches(String name) {
                return "prev".equals(name) || "next".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.isEmpty();
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
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
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "min".equals(name) || "max".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                Number v = null;
                for (Object node : allnodes(args, in, context, CONTEXT_OR_ALL)) {
                    Number n = Expr.numberValue(context, node);
                    if (n != null) {
                        if (v == null) {
                            v = n;
                        } else {
                            int c = Expr.compare(n, v, context);
                            if ((c < 0 && "min".equals(name)) || (c > 0 && "max".equals(name))) {
                                v = n;
                            }
                        }
                    }
                }
                if (v != null) {
                    out.add(v);
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "sum".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                // This is a pain - if we're given 1000 integers and one BigDecimal, output has to be BigDecimal.
                Number v = null;
                for (Object node : allnodes(args, in, context, CONTEXT_OR_ALL)) {
                    Number n = Expr.numberValue(context, node);
                    if (n != null) {
                        if (v == null) {
                            v = n;
                        } else if (v instanceof BigDecimal || n instanceof BigDecimal || (v instanceof BigInteger && (n instanceof Double || n instanceof Float)) || (n instanceof BigInteger && (v instanceof Double || n instanceof Float))) {
                            v = ((BigDecimal)(v instanceof BigDecimal ? v : new BigDecimal(v.toString()))).add((BigDecimal)(v instanceof BigDecimal ? v : new BigDecimal(v.toString())));
                        } else if (v instanceof BigInteger || n instanceof BigInteger) {

                            v = ((BigInteger)(v instanceof BigInteger ? v : new BigInteger(v.toString()))).add((BigInteger)(v instanceof BigInteger ? v : new BigInteger(v.toString())));
                        } else if (v instanceof Double || v instanceof Float || n instanceof Double || n instanceof Float) {
                            v = Double.valueOf(v.doubleValue() + n.doubleValue());
                        } else if (v instanceof Long || n instanceof Long) {
                            v = Long.valueOf(v.longValue() + n.longValue());
                        } else {
                            v = Integer.valueOf(v.intValue() + n.intValue());
                        }
                    }
                }
                if (v != null) {
                    out.add(v);
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "ceil".equals(name) || "floor".equals(name) || "round".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    Number n = Expr.numberValue(context, node);
                    if (n != null) {
                        if (n instanceof BigDecimal) {
                            n = ((BigDecimal)n).round(new MathContext(MathContext.UNLIMITED.getPrecision(), "ceil".equals(name) ? RoundingMode.CEILING : "floor".equals(name) ? RoundingMode.FLOOR: RoundingMode.HALF_UP)); // This sucks! No RoundingMode rounds 2.4 to 2, 2.5 to 3 and -2.5 to 2. Insane but suck up the edge case
                        } else if (n instanceof Double || n instanceof Float) {
                            double d;
                            if ("ceil".equals(name)) {
                                d = Math.ceil(n.doubleValue());
                            } else if ("floor".equals(name)) {
                                d = Math.floor(n.doubleValue());
                            } else {
                                d = Math.round(n.doubleValue());
                            }
                            if (d == (int)d) {
                                out.add(Integer.valueOf((int)d));
                            } else if (d == (long)d) {
                                out.add(Long.valueOf((long)d));
                            } else {
                                out.add(Double.valueOf(d));     // yep - for massive doubles
                            }
                        } else {
                            out.add(n);
                        }
                    }
                }
            }
        });

        // Type functions: type, value, string, number
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "type".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                boolean set = false;
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    set = true;
                    String s = null;
                    if (node == EvalContext.NULL) {
                        s = "null";
                    } else if (node instanceof String) {
                        s = "string";
                    } else if (node instanceof Number) {
                        s = "number";
                    } else if (node instanceof Boolean) {
                        s = "boolean";
                    } else if (node != null) {
                        s = context.type(node);
                        if (s == null) {
                            s = node.getClass().getName();
                        }
                    }
                    if (s != null) {
                        out.add(s);
                    }
                }
                if (!set) {
                    out.add("undefined");
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "string".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    String s = Expr.stringValue(context, node);
                    if (s == null) {
                        if (context.value(node) instanceof Boolean) {
                            s = context.value(node).toString();
                        } else {
                            Number n = Expr.numberValue(context, node);
                            if (n != null) {
                                s = n.toString();
                                // Strip trailing ".0"
                                int ix = s.indexOf(".");
                                if (ix > 0) {
                                    boolean found = false;
                                    for (int i=ix+1;i<s.length();i++) {
                                        if (s.charAt(i) != '0') {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        s = s.substring(0, ix);
                                    }
                                }
                            }
                        }
                    }
                    if (s != null) {
                        out.add(node);
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "number".equals(name);
            }
            public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    Number n = Expr.numberValue(context, node);
                    if (n == null) {
                        String s = Expr.stringValue(context, node);
                        if (s != null) {
                            if (s.indexOf('.') < 0) {
                                try {
                                    n = Long.valueOf(s);
                                    if (n.longValue() == n.intValue()) {
                                        n = Integer.valueOf(n.intValue());
                                    }
                                } catch (Exception e) { 
                                    try {
                                        n = new BigInteger(s);
                                    } catch (Exception e2) { }
                                }
                            } else {
                                try {
                                    n = Double.valueOf(s);
                                } catch (Exception e) { 
                                    try {
                                        n = new BigDecimal(s);
                                    } catch (Exception e2) { }
                                }
                            }
                        }
                    }
                    if (n != null) {
                        out.add(n);
                    }
                }
            }
        });

        // Format functions: format, encode
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "format".equals(name);
            }
            //a/b/format("%02d") input of one
            //a/b[format("%02d") == "00"] input of one
            //format("%02d", a/b) - input of two
            public boolean verify(final String name, final List<Term> args) {
                return (args.size() == 1 || args.size() == 2) && args.get(0).isString();    // format must be a constant
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                String format = args.get(0).stringValue();
                Locale locale = context.getConfiguration().getLocale();
                for (Object node : allnodes(args, in, context, CONTEXT_OR_SECOND)) {
                    String v = null;;
                    try {
                        Number n = Expr.numberValue(context, node);
                        if (n != null) {
                            try {
                                v = String.format(locale, format, n);
                            } catch (Exception e) {
                                v = String.format(locale, format, n.doubleValue());
                            }
                        } else {
                            String s = Expr.stringValue(context, node);
                            if (s != null) {
                                v = String.format(locale, format, v);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.out);
                    }
                    if (v != null) {
                        out.add(v);
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "escape".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                StringBuilder sb = new StringBuilder();
                for (Object node : allnodes(args, in, context, CONTEXT_OR_ALL)) {
                    String s = Expr.stringValue(context, node);
                    if (s != null) {
                        Expr.escapeXML(s, true, sb);
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "unescape".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                StringBuilder sb = new StringBuilder();
                for (Object node : allnodes(args, in, context, CONTEXT_OR_ALL)) {
                    String s = Expr.stringValue(context, node);
                    if (s != null) {
                        Expr.unescapeXML(s, sb);
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "index-of".equals(name) || "last-index-of".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() == 1 || args.size() == 2;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                String search = Expr.stringValue0(context, args.get(args.size() - 1).eval(in, new ArrayList<Object>(), context));
                if (search != null) {
                    for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                        String value = Expr.stringValue(context, node);
                        if (value != null) {
                            out.add("index-of".equals(name) ? value.indexOf(search) : value.lastIndexOf(search));
                        }
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "string-length".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    String value = Expr.stringValue(context, node);
                    if (value != null) {
                        out.add(value.length());
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "lower-case".equals(name) || "upper-case".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                    String value = Expr.stringValue(context, node);
                    if (value != null) {
                        Locale locale = context.getConfiguration().getLocale();
                        out.add("lower-case".equals(name) ? value.toLowerCase(locale) : value.toUpperCase(locale));
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "substring".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() == 2 || args.size() == 3;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                Number off = Expr.numberValue0(context, args.get(args.size() - 2).eval(in, new ArrayList<Object>(), context));
                Number len = Expr.numberValue0(context, args.get(args.size() - 1).eval(in, new ArrayList<Object>(), context));
                if (off != null && len != null) {
                    for (Object node : allnodes(args, in, context, CONTEXT_OR_FIRST)) {
                        String value = Expr.stringValue(context, node);
                        if (value != null) {
                            out.add(value.substring(off.intValue(), len.intValue()));
                        }
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "matches".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                if (!((args.size() == 1 || args.size() == 2) && args.get(0).isString())) {
                    return false;
                }
                Pattern pattern = compilePattern(args.get(0).stringValue());
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                Pattern pattern = compilePattern(args.get(0).stringValue());
                for (Object node : allnodes(args, in, context, CONTEXT_OR_SECOND)) {
                    String value = Expr.stringValue(context, node);
                    if (value != null) {
                        out.add(pattern.matcher(value).find());
                    }
                }
            }
        });
        FUNCTIONS.add(new Function() {
            Pattern pattern;
            public boolean matches(String name) {
                return "replace".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                if (!((args.size() == 2 || args.size() == 3) && args.get(0).isString())) {
                    return false;
                }
                Pattern pattern = compilePattern(args.get(0).stringValue());
                return true;
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                Pattern pattern = compilePattern(args.get(0).stringValue());
                for (Object node : allnodes(args, in, context, CONTEXT_OR_THIRD)) {
                    String value = Expr.stringValue(context, node);
                    if (value != null) {
                        String replace = Expr.stringValue0(context, args.get(1).eval(in, new ArrayList<Object>(), context));
                        if (replace == null) {
                            replace = "";
                        }
                        out.add(pattern.matcher(value).replaceAll(replace));
                    }
                }
            }
        });

        FUNCTIONS.add(new Function() {
            public boolean matches(String name) {
                return "date-format".equals(name);
            }
            //a/b/format("%02d") input of one
            //a/b[format("%02d") == "00"] input of one
            //format("%02d", a/b) - input of two
            public boolean verify(final String name, final List<Term> args) {
                return (args.size() == 1 || args.size() == 2) && args.get(0).isString();    // format must be a constant
            }
            @Override public void eval(final String name, List<Term> args, List<Object> in, List<Object> out, final EvalContext context) {
                String format = args.get(0).stringValue();
                Locale locale = context.getConfiguration().getLocale();
                for (Object node : allnodes(args, in, context, CONTEXT_OR_SECOND)) {
                    String v = null;
                    try {
                        Number n = Expr.numberValue(context, node);
                        TemporalAccessor date = null;
                        if (n != null) {
                            if (n.longValue() > 32504283350l) {  // Early year 3000 in ms, 12 Jan 1971 in seconds
                                date = Instant.ofEpochMilli(n.longValue());
                            } else {
                                date = Instant.ofEpochSecond(n.longValue());
                            }
                        } else {
                            String s = Expr.stringValue(context, node);
                            if (s != null) {
                                // FFS, in 2023 I still can't parse ISO8601 without hyphens/colons
                                if (s.length() > 4 && Character.isDigit(s.charAt(4))) {
                                    s = s.substring(0, 4) + "-" + s.substring(4);
                                }
                                if (s.length() > 7 && Character.isDigit(s.charAt(7))) {
                                    s = s.substring(0, 7) + "-" + s.substring(7);
                                }
                                if (s.length() > 10 && s.charAt(10) != ' ') {
                                    s = s.substring(0, 10) + " " + s.substring(10);
                                }
                                if (s.length() > 13 && Character.isDigit(s.charAt(13))) {
                                    s = s.substring(0, 13) + ":" + s.substring(13);
                                }
                                if (s.length() > 16 && Character.isDigit(s.charAt(16))) {
                                    s = s.substring(0, 16) + ":" + s.substring(16);
                                }
                                try {
                                    if (s.length() > 16) {
                                        date = OffsetDateTime.parse(s);
                                    } else {
                                        date = LocalDate.parse(s);
                                    }
                                } catch (Exception e) { }
                            }
                        }
                        // We'll use:
                        // y - year
                        // M - month
                        // d - date
                        // H - 24hr
                        // h - 12hr
                        // m - minute
                        // s - second
                        if (date != null) {
                            v = DateTimeFormatter.ofPattern(format, locale).format(date);
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.out);
                    }
                    if (v != null) {
                        out.add(v);
                    }
                }
            }
        });
    }

    private static Pattern compilePattern(String string) {
        Pattern pattern;
        synchronized(PATTERNCACHE) {
            pattern = PATTERNCACHE.get(string);
        }
        if (pattern == null) {
            pattern = Pattern.compile(string);
            synchronized(PATTERNCACHE) {
                PATTERNCACHE.put(string, pattern);
            }
        }
        return pattern;
    }

    private static final int ALLARGS = 0;               // Iterate over args only, or nothing of no args
    private static final int CONTEXT_OR_ALL = 1;        // Iterate over all args, or context if no args supplied
    private static final int ONEARGS = 2;               // Iterate over first arguent only, or nothing if no args
    private static final int CONTEXT_OR_FIRST  = 3;     // Iterate over first argument only, or context if no args supplied
    private static final int CONTEXT_OR_SECOND  = 4;    // Iterate over second argument only, or context if only one arg supplied
    private static final int CONTEXT_OR_THIRD  = 5;     // Iterate over third argument only, or context if only one arg supplied

    /**
     * Return an iterator that will cover all nodes
     * @param contextfallback if true and no arguments are supplied, iterator over our context ("in")
     * @param allargs if true, iterate over more than one argument if supplied
     */
    private static Iterable<Object> allnodes(final List<Term> args, final List<Object> in, final EvalContext context, final int flags) {
        // This is pretty awful
        return new Iterable<Object>() {
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {
                    private int c = 0;
                    private Iterator<Object> i;
                    {skip();}
                    public boolean hasNext() {
                        if (!i.hasNext()) {
                            skip();
                            if (!i.hasNext()) {
                                return false;
                            }
                        }
                        return true;
                    }
                    private void skip() {
                        if (i == null) {
                            if (args.isEmpty() || (args.size() < 2 && flags == CONTEXT_OR_SECOND) || (args.size() < 3 && flags == CONTEXT_OR_THIRD)) {
                                if (flags == CONTEXT_OR_ALL || flags == CONTEXT_OR_FIRST || flags == CONTEXT_OR_SECOND || flags == CONTEXT_OR_THIRD) {
                                    i = in.iterator();
                                } else {
                                    i = Collections.<Object>emptyList().iterator();
                                }
                            } else if (flags == CONTEXT_OR_SECOND) {
                                i = args.get(1).eval(in, new ArrayList<Object>(), context).iterator();
                            } else if (flags == CONTEXT_OR_THIRD) {
                                i = args.get(2).eval(in, new ArrayList<Object>(), context).iterator();
                            } else {
                                i = args.get(0).eval(in, new ArrayList<Object>(), context).iterator();
                            }
                        } else if ((flags == ALLARGS || flags == CONTEXT_OR_ALL) && c + 1 < args.size()) {
                            i = args.get(++c).eval(in, new ArrayList<Object>(), context).iterator();
                        }
                    }
                    public Object next() {
                        return i.next();
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

}
