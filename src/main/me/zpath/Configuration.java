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
    private Appendable debug;
    private boolean closed;
    int depth;

    private Configuration() {
    }

    /** 
     * Create a new Configuration that is a clone of the parent Configuration
     * @param config the configuration to copy
     */
    public Configuration(Configuration config) {
        functions = new HashMap<String,Function>(config.functions);
        factories = new LinkedHashSet<NodeFactory>(config.factories);
    }

    /**
     * Return a new Configuration which is a more-nested version of this one.
     * Only call from within custom Functions that do debug output
     * @return a new Configuration
     */
    public Configuration debugIndent() {
        if (debug == null) {
           return this;
        }
        Configuration c = new Configuration();
        c.functions = functions;
        c.factories = factories;
        c.debug = debug;
        c.depth = depth++;
        return c;
    }

    /**
     * Register a new Function with this Configuration
     * @param function the function
     */
    public void registerFunction(Function function) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        if (functions == null) {
            functions = new HashMap<String,Function>();
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
        return functions == null ? null : functions.get(name);
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
     * Return true if this Configuration has a Debugger set
     * @return true if debug is set
     */
    public boolean isDebug() {
        return debug != null;
    }

    /**
     * If debug is set, write a debug string to the logger
     * @param s the string to log
     */
    public void debug(String s) {
        if (debug != null) {
            try {
                for (int i=0;i<depth;i++) {
                    debug.append("  ");
                }
                debug.append(s);
                debug.append('\n');
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Set an Appendable to write debug messages to
     * @param out the appendeble, or null for no debug.
     */
    public void setDebug(Appendable out) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        this.debug = out;
    }

    private void close() {
        if (!closed) {
            functions = Collections.<String,Function>unmodifiableMap(functions);
            factories = Collections.<NodeFactory>unmodifiableSet(factories);
        }
        closed = true;
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

    static {
        CONFIG.functions = new HashMap<String,Function>();
        CONFIG.factories = new LinkedHashSet<NodeFactory>();
        CONFIG.factories.addAll(getServiceList(me.zpath.NodeFactory.class));
        CONFIG.registerFunction(new FunctionCount());
        CONFIG.registerFunction(new FunctionMin());
        CONFIG.registerFunction(new FunctionMax());
        CONFIG.registerFunction(new FunctionSum());
        CONFIG.registerFunction(new FunctionEncode());
        CONFIG.registerFunction(new FunctionNumber());
        CONFIG.registerFunction(new FunctionString());
        CONFIG.registerFunction(new FunctionFloor());
        CONFIG.registerFunction(new FunctionCeil());
        CONFIG.registerFunction(new FunctionRound());
        CONFIG.close();
    }

}
