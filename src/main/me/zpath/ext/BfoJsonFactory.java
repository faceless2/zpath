package me.zpath.ext;

import me.zpath.*;
import com.bfo.json.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class BfoJsonFactory implements NodeFactory {
    
    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public Node create(Object proxy, Configuration config) {
        if (proxy instanceof Json) {
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

        public Node get(String key) {
            Json j = proxy.get(key);
            return j == null ? null : new Proxy(j, key, -1);
        }

        public Node get(int key) {
            Json j = proxy.get(key);
            return j == null ? null : new Proxy(j, null, index);
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
            } else {
                return "unknown";       // Shouldn't happen
            }
        }

        public Iterator<Node> children() {
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
        }

        public String toString() {
            return proxy.toString(); //  + (key != null ? "#KEY="+key : index >= 0 ? "#INDEX="+index : "");
        }

    }

}
