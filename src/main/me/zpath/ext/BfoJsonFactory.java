package me.zpath.ext;

import me.zpath.*;
import com.bfo.json.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class BfoJsonFactory implements NodeFactory {
    
    final List<Function> functions = new ArrayList<Function>();

    public BfoJsonFactory() {
        functions.add(new FunctionTag());
    }

    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public Node create(Object proxy, Configuration config) {
        if (proxy instanceof Json) {
            for (Function f : functions) {
                config.registerFunction(f);
            }
            return new Proxy((Json)proxy, null, -1);
        }
        return null;
    }

    private static class Proxy implements Node {

        final int index;
        final String key;
        final Json proxy;

        Proxy(Json json, String key, int index) {
            this.proxy = json;
            this.key = key;
            this.index = index;
        }

        public Iterator<Node> get(String key) {
            if (key.equals("*")) {
                if (proxy.isList()) {
                    return new Iterator<Node>() {
                        int i = 0;
                        public boolean hasNext() {
                            return i < proxy.size();
                        }
                        public Node next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Node n = new Proxy(proxy.get(i), null, i);
                            i++;
                            return n;
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else if (proxy.isMap()) {
                    final Iterator<Map.Entry<Object,Json>> i = proxy.mapValue().entrySet().iterator();
                    return new Iterator<Node>() {
                        public boolean hasNext() {
                            return i.hasNext();
                        }
                        public Node next() {
                            Map.Entry<Object,Json> e = i.next();
                            return new Proxy(e.getValue(), e.getKey().toString(), -1);
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else {
                    return null;
                }
            } else {
                Json j = proxy.get(key);
                return j == null ? null : Collections.<Node>singleton(new Proxy(j, key, -1)).iterator();
            }
        }

        public Iterator<Node> get(int key) {
            Json j = proxy.get(key);
            return j == null ? null : Collections.<Node>singleton(new Proxy(j, null, index)).iterator();
        }

        public Node parent() {
            Json j = proxy.parent();
            return j == null ? null : new Proxy(j, null, -1);
        }

        public String stringValue() {
            return proxy.stringValue();
        }

        public double doubleValue() {
            return proxy.isNumber() ? proxy.doubleValue() : Double.NaN;
        }

        public boolean booleanValue() {
            if (proxy.isBoolean()) {
                return proxy.booleanValue();
            } else {
                return !proxy.isNull() && !proxy.isUndefined();
            }
        }

        public boolean equals(Object o) {
            return o instanceof Proxy && ((Proxy)o).proxy == proxy;
        }

        public int hashCode() {
            return proxy.hashCode();
        }

        public String key() {
            return key;
        }

        public int index() {
            return index;
        }

        public Object proxy() {
            return proxy;
        }

        public String type() {
            if (proxy.isString()) {
                return "string";
            } else if (proxy.isNumber()) {
                return "number";
            } else if (proxy.isBoolean()) {
                return "boolean";
            } else if (proxy.isMap()) {
                return "map";
            } else if (proxy.isList()) {
                return "list";
            } else if (proxy.isNull()) {
                return "null";
            } else if (proxy.isUndefined()) {
                return "undef";
            } else if (proxy.isBuffer()) {
                return "buffer";
            } else {
                return "unknown";       // Shouldn't happen
            }
        }

        public String toString() {
            return proxy.toString(); //  + (key != null ? "#KEY="+key : index >= 0 ? "#INDEX="+index : "");
        }

    }

    /**
     * The "tag" function offers a way to retrieve the CBOR tag from any object
     */
    private static class FunctionTag implements Function {

        public String getName() {
            return "tag";
        }

        public boolean verify(List<Term> arguments) {
            return true;
        }

        public String toString() {
            return getName() + "()";
        }

        public void eval(final List<Term> arguments, final Collection<Node> in, final Collection<Node> out, final Configuration config) {
            if (config.isDebug()) {
                config.debug(this + " " + arguments + " ctx=" + in);
            }
            Set<Node> nodes = new HashSet<Node>();
            for (Term t : arguments) {
                t.eval(in, nodes, config.debugIndent());
            }
            for (Node node : nodes) {
                if (node.proxy() instanceof Json) {
                    Json json = (Json)node.proxy();
                    long tag = json.getTag();
                    if (tag >= 0) {
                        out.add(Node.create(tag));
                    }
                }
            }
        }

    }


}
