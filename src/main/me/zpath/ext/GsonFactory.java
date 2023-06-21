package me.zpath.ext;

import me.zpath.*;
import java.util.*;
import com.google.gson.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class GsonFactory implements NodeFactory {
    
    private final List<Function> functions = new ArrayList<Function>();

    public GsonFactory() {
    }

    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public Node create(Object proxy, Configuration config) {
        if (proxy instanceof JsonElement) {
            for (Function f : functions) {
                config.registerFunction(f);
            }
            return new Proxy(null, (JsonElement)proxy, null, -1);
        }
        return null;
    }

    private static class Proxy implements Node {

        private final Proxy parent;
        private final JsonElement proxy;
        private final Object key;
        private final int index;

        Proxy(Proxy parent, JsonElement json, Object key, int index) {
            this.parent = parent;
            this.proxy = json;
            this.key = key;
            this.index = index;
        }

        @Override public Iterator<Node> get(Object key) {
            if (key == WILDCARD) {
                if (proxy.isJsonArray()) {
                    final JsonArray list = (JsonArray)proxy;
                    return new Iterator<Node>() {
                        int i = 0;
                        public boolean hasNext() {
                            return i < list.size();
                        }
                        public Node next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Node n = new Proxy(Proxy.this, list.get(i), Integer.valueOf(i), i);
                            i++;
                            return n;
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else if (proxy.isJsonObject()) {
                    final JsonObject map = (JsonObject)proxy;
                    final Iterator<Map.Entry<String,JsonElement>> i = map.asMap().entrySet().iterator();
                    return new Iterator<Node>() {
                        public boolean hasNext() {
                            return i.hasNext();
                        }
                        public Node next() {
                            Map.Entry<String,JsonElement> e = i.next();
                            return new Proxy(Proxy.this, e.getValue(), e.getKey(), -1);
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } else {
                    return null;
                }
            } else if (key instanceof Integer && proxy.isJsonArray()) {
                final JsonArray list = (JsonArray)proxy;
                int index = ((Integer)key).intValue();
                if (index >= 0 && index < list.size()) {
                    return Collections.<Node>singleton(new Proxy(this, list.get(index), key, index)).iterator();
                }
            } else if (key instanceof String && proxy.isJsonObject()) {
                final JsonObject map = (JsonObject)proxy;
                JsonElement j = map.get((String)key);
                if (j != null) {
                    return Collections.<Node>singleton(new Proxy(this, j, key, -1)).iterator();
                }
            }
            return null;
        }

        @Override public Node parent() {
            return parent;
        }

        @Override public String stringValue() {
            if (proxy instanceof JsonPrimitive) {
                return ((JsonPrimitive)proxy).getAsString();
            }
            return null;
        }

        @Override public double doubleValue() {
            if (proxy instanceof JsonPrimitive && ((JsonPrimitive)proxy).isNumber()) {
                return ((JsonPrimitive)proxy).getAsNumber().doubleValue();
            }
            return Double.NaN;
        }

        @Override public boolean booleanValue() {
            if (proxy instanceof JsonNull) {
                return false;
            } else if (proxy instanceof JsonPrimitive) {
                JsonPrimitive j = (JsonPrimitive)proxy;
                if (j.isBoolean()) {
                    return j.getAsBoolean();
                }
            }
            return true;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Proxy && ((Proxy)o).proxy == proxy;
        }

        @Override public int hashCode() {
            return proxy.hashCode();
        }

        @Override public Object key() {
            return key;
        }

        @Override public int index() {
            return index;
        }

        @Override public Object proxy() {
            return proxy;
        }

        @Override public String type() {
            if (proxy.isJsonPrimitive() && ((JsonPrimitive)proxy).isString()) {
                return "string";
            } else if (proxy.isJsonPrimitive() && ((JsonPrimitive)proxy).isNumber()) {
                return "number";
            } else if (proxy.isJsonPrimitive() && ((JsonPrimitive)proxy).isBoolean()) {
                return "boolean";
            } else if (proxy.isJsonObject()) {
                return "map";
            } else if (proxy.isJsonArray()) {
                return "list";
            } else if (proxy.isJsonNull()) {
                return "null";
            } else {
                return "unknown";       // Shouldn't happen
            }
        }

        @Override public String toString() {
//            return proxy.toString();
            return proxy.toString() + "#KEY="+key();
        }

    }

}
