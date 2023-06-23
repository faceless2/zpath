package me.zpath.ext;

import me.zpath.*;
import java.util.*;
import com.google.gson.*;

/** 
 * A EvalFactory for the Google Json API from <a href="https://github.com/google/gson">https://github.com/google/gson</a>,
 * specifically the <a href="https://www.javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/JsonElement.html">JsonElement</a>
 * class.
 */
public class GsonFactory implements EvalFactory {
    
    public GsonFactory() {
    }

    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public EvalContext create(Object proxy, Configuration config) {
        if (proxy instanceof JsonElement) {
            return new MyContext(config);
        }
        return null;
    }

    private static class MyContext implements EvalContext {

        private static class ReverseLookup {
            final JsonElement parent;
            final Object key;
            final int index;
            ReverseLookup(JsonElement parent, Object key, int index) {
                this.parent = parent;
                this.key = key;
                this.index = index;
            }
        }

        private final Map<JsonElement,ReverseLookup> registry;
        private final Configuration config;
        private int contextIndex = -1;
        private List<Object> contextObjects;

        MyContext(Configuration config) {
            this.config = config;
            this.registry = new HashMap<JsonElement,ReverseLookup>();
        }

        private void register(JsonElement child, JsonElement parent, Object key, int index) {
            registry.put(child, new ReverseLookup(parent, key, index));
        }

        @Override public Configuration getConfiguration() {
            return config;
        }

        @Override public Configuration.Logger getLogger() {
            return config.getLogger();
        }

        @Override public Function getFunction(String name) {
            return null;
        }

        @Override public void setContext(int index, List<Object> nodes) {
            if (nodes == null) {
                index = -1;
            }
            this.contextIndex = index;
            this.contextObjects = nodes;
        }

        @Override public int getContextIndex() {
            return contextIndex;
        }

        @Override public List<Object> getContext() {
            return contextObjects;
        }

        @Override public Iterable<? extends Object> get(final Object o, Object key) {
            if (o instanceof JsonArray) {
                final JsonArray list = (JsonArray)o;
                if (key == WILDCARD) {
                    return new Iterable<JsonElement>() {
                        public Iterator<JsonElement> iterator() {
                            final Iterator<JsonElement> i = list.iterator();
                            return new Iterator<JsonElement>() {
                                int index = 0;
                                public JsonElement next() {
                                    JsonElement elt = i.next();
                                    register(elt, list, Integer.valueOf(index), index);
                                    index++;
                                    return elt;
                                }
                                public boolean hasNext() {
                                    return i.hasNext();
                                }
                                public void remove() {
                                    throw new UnsupportedOperationException();
                                }
                            };
                        }
                    };
                } else if (key instanceof Integer) {
                    int index = ((Integer)key).intValue();
                    if (index >= 0 && index < list.size()) {
                        JsonElement elt = list.get(index);
                        register(elt, list, Integer.valueOf(index), index);
                        return Collections.<JsonElement>singletonList(elt);
                    }
                }
            } else if (o instanceof JsonObject) {
                JsonObject map = (JsonObject)o;
                if (key == WILDCARD) {
                    return new Iterable<JsonElement>() {
                        public Iterator<JsonElement> iterator() {
                            final Iterator<Map.Entry<String,JsonElement>> i = map.asMap().entrySet().iterator();
                            return new Iterator<JsonElement>() {
                                public JsonElement next() {
                                    Map.Entry<String,JsonElement> e = i.next();
                                    JsonElement elt = e.getValue();
                                    register(elt, map, e.getKey(), -1);
                                    return elt;
                                }
                                public boolean hasNext() {
                                    return i.hasNext();
                                }
                                public void remove() {
                                    throw new UnsupportedOperationException();
                                }
                            };
                        }
                    };
                } else if (key instanceof String) {
                    JsonElement elt = map.get((String)key);
                    if (elt != null) {
                        register(elt, map, key, -1);
                        return Collections.<JsonElement>singletonList(elt);
                    }
                }
            }
            return Collections.<Object>emptyList();
        }

        @Override public Object parent(Object o) {
            ReverseLookup l = registry.get(o);
            if (l != null) {
                return l.parent;
            }
            return null;
        }

        @Override public String stringValue(Object o) {
            if (o instanceof JsonPrimitive) {
                return ((JsonPrimitive)o).getAsString();
            }
            return null;
        }

        @Override public Number numberValue(Object o) {
            if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isNumber()) {
                return ((JsonPrimitive)o).getAsNumber();
            }
            return null;
        }

        @Override public boolean booleanValue(Object o) {
            if (o instanceof JsonNull) {
                return false;
            } else if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isBoolean()) {
                return ((JsonPrimitive)o).getAsBoolean();
            }
            return o != null;
        }

        @Override public Object key(Object o) {
            ReverseLookup l = registry.get(o);
            if (l != null) {
                return l.key;
            }
            return null;
        }

        @Override public int index(Object o) {
            ReverseLookup l = registry.get(o);
            if (l != null) {
                return l.index;
            }
            return -1;
        }
            
        @Override public String type(Object o) {
            if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isString()) {
                return "string";
            } else if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isNumber()) {
                return "number";
            } else if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isBoolean()) {
                return "boolean";
            } else if (o instanceof JsonObject) {
                return "map";
            } else if (o instanceof JsonArray) {
                return "list";
            } else if (o instanceof JsonNull) {
                return "null";
            }
            return null;
        }

        @Override public Object value(Object o) {
            if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isString()) {
                return ((JsonPrimitive)o).getAsString();
            } else if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isNumber()) {
                double d = ((JsonPrimitive)o).getAsNumber().doubleValue();
                if (d == (int)d) {
                    return Integer.valueOf((int)d);
                } else {
                    return Double.valueOf(d);
                }
            } else if (o instanceof JsonPrimitive && ((JsonPrimitive)o).isBoolean()) {
                return ((JsonPrimitive)o).getAsBoolean();
            } else if (o instanceof JsonNull) {
                return null;
            }
            return o;
        }


    }

}
