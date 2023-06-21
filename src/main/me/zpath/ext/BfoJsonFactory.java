package me.zpath.ext;

import me.zpath.*;
import com.bfo.json.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class BfoJsonFactory implements NodeFactory {
    
    private static final Object NOKEY = new Object();
    private static final int NOINDEX = -2;
    private final List<Function> functions = new ArrayList<Function>();

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
            return new Proxy((Json)proxy, NOKEY, NOINDEX);
        }
        return null;
    }

    private static class Proxy implements Node {

        private Object key;
        private int index;
        private final Json proxy;

        Proxy(Json json, Object key, int index) {
            this.proxy = json;
            this.key = key;
            this.index = index;
        }

        @Override public Iterator<Node> get(Object key) {
            if (key == WILDCARD) {
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
                            Node n = new Proxy(proxy.get(i), Integer.valueOf(i), i);
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
                            return new Proxy(e.getValue(), e.getKey(), -1);
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else {
                    return null;
                }
            } else {
                int index = -1;
                Json j = proxy.get(key);
                if (j != null && proxy.isList() && key instanceof Integer) {
                    index = ((Integer)key).intValue();
                }
                return j == null ? null : Collections.<Node>singleton(new Proxy(j, key, index)).iterator();
            }
        }

        @Override public Node parent() {
            Json j = proxy.parent();
            return j == null ? null : new Proxy(j, NOKEY, NOINDEX);
        }

        @Override public String stringValue() {
            return proxy.stringValue();
        }

        @Override public double doubleValue() {
            return proxy.isNumber() ? proxy.doubleValue() : Double.NaN;
        }

        @Override public boolean booleanValue() {
            if (proxy.isBoolean()) {
                return proxy.booleanValue();
            } else {
                return !proxy.isNull() && !proxy.isUndefined();
            }
        }

        @Override public boolean equals(Object o) {
            return o instanceof Proxy && ((Proxy)o).proxy == proxy;
        }

        @Override public int hashCode() {
            return proxy.hashCode();
        }

        @Override public Object key() {
            if (key == NOKEY) {
                if (proxy.parent() == null) {
                    key = null;
                } else if (proxy.parent().isMap()) {
                    Json p = proxy.parent();
                    for (Map.Entry<Object,Json> e : proxy.parent().mapValue().entrySet()) {
                        if (e.getValue() == proxy) {
                            key = e.getKey();
                            break;
                        }
                    }
                } else if (proxy.parent().isList()) {
                    List<Json> l = proxy.parent().listValue();
                    for (int i=0;i<l.size();i++) {
                        if (l.get(i) == proxy) {
                            key = Integer.valueOf(i);
                            index = i;
                            break;
                        }
                    }
                } else {
                    key = null;
                }
            }
            return key;
        }

        @Override public int index() {
            if (index == NOINDEX) {
                index = -1;
                if (proxy.parent() != null && proxy.parent().isList()) {
                    key();
                }
            }
            return index;
        }

        @Override public Object proxy() {
            return proxy;
        }

        @Override public String type() {
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

        @Override public String toString() {
//            return proxy.toString();
            return proxy.toString() + "#KEY="+key();
        }

    }

    /**
     * The "tag" function offers a way to retrieve the CBOR tag from any object
     */
    private static class FunctionTag implements Function {
        @Override public String getName() {
            return "tag";
        }
        @Override public boolean verify(List<Term> args) {
            return args.size() <= 1;
        }
        @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
            for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                if (node.proxy() instanceof Json) {
                    Json json = (Json)node.proxy();
                    long tag = json.getTag();
                    if (tag >= 0) {
                        out.add(tag == (int)tag ? Node.create((int)tag) : Node.create(tag));
                    }
                }
            }
        }
    }


}
