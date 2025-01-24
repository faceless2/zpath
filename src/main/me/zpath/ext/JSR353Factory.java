package me.zpath.ext;

import me.zpath.*;
import java.util.*;
import javax.json.*;

/** 
 * A EvalFactory for the Google Json API from <a href="https://github.com/google/gson">https://github.com/google/gson</a>,
 * specifically the <a href="https://www.javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/JsonElement.html">JsonElement</a>
 * class.
 */
public class JSR353Factory implements EvalFactory {
    
    /**
     * The default constructor
     * @throws Exception if the factory can't be loaded because the <code>com.google.json</code> package is not available
     */
    public JSR353Factory() throws Exception {
        Class.forName("javax.json.Json");
    }

    @Override public EvalContext create(Object proxy, Configuration config) {
        if (proxy instanceof JsonValue) {
            return new MyContext(config);
        }
        return null;
    }

    private static class MyContext implements EvalContext {

        private static class ReverseLookup {
            final JsonStructure parent;
            final Object key;
            final int index;
            ReverseLookup(JsonStructure parent, Object key, int index) {
                this.parent = parent;
                this.key = key;
                this.index = index;
            }
        }

        private final Map<JsonValue,ReverseLookup> registry;
        private final Configuration config;
        private int contextIndex = -1;
        private List<Object> contextObjects;

        MyContext(Configuration config) {
            this.config = config;
            this.registry = new HashMap<JsonValue,ReverseLookup>();
        }

        private void register(JsonValue child, JsonStructure parent, Object key, int index) {
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
                    return new Iterable<JsonValue>() {
                        public Iterator<JsonValue> iterator() {
                            final Iterator<JsonValue> i = list.iterator();
                            return new Iterator<JsonValue>() {
                                int index = 0;
                                public JsonValue next() {
                                    JsonValue elt = i.next();
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
                        JsonValue elt = list.get(index);
                        register(elt, list, Integer.valueOf(index), index);
                        return Collections.<JsonValue>singletonList(elt);
                    }
                }
            } else if (o instanceof JsonObject) {
                JsonObject map = (JsonObject)o;
                if (key == WILDCARD) {
                    return new Iterable<JsonValue>() {
                        public Iterator<JsonValue> iterator() {
                            final Iterator<Map.Entry<String,JsonValue>> i = map.entrySet().iterator();
                            return new Iterator<JsonValue>() {
                                public JsonValue next() {
                                    Map.Entry<String,JsonValue> e = i.next();
                                    JsonValue elt = e.getValue();
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
                    JsonValue elt = map.get((String)key);
                    if (elt != null) {
                        register(elt, map, key, -1);
                        return Collections.<JsonValue>singletonList(elt);
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
            if (o instanceof JsonString) {
                return ((JsonString)o).getString();
            }
            return null;
        }

        @Override public Number numberValue(Object o) {
            if (o instanceof JsonNumber) {
                JsonNumber num = (JsonNumber)o;
                if (num.isIntegral()) {
                    try {
                        return num.intValueExact();
                    } catch (Exception e) {
                        try {
                            return num.longValueExact();
                        } catch (Exception e2) {
                            return num.bigIntegerValue();
                        }
                    }
                } else {
                    return num.doubleValue();
                }
            }
            return null;
        }

        @Override public Boolean booleanValue(Object o) {
            if (o == JsonValue.TRUE) {
                return Boolean.TRUE;
            } else if (o == JsonValue.FALSE) {
                return Boolean.FALSE;
            }
            return null;
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
            if (o instanceof JsonString) {
                return "string";
            } else if (o instanceof JsonNumber) {
                return "number";
            } else if (o == JsonValue.TRUE || o == JsonValue.FALSE) {
                return "boolean";
            } else if (o instanceof JsonObject) {
                return "map";
            } else if (o instanceof JsonArray) {
                return "list";
            } else if (o == JsonValue.NULL) {
                return "null";
            }
            return null;
        }

        @Override public Object value(Object o) {
            String type = type(o);
            if ("string".equals(type)) {
                return stringValue(o);
            } else if ("number".equals(type)) {
                return numberValue(o);
            } else if ("boolean".equals(type)) {
                return booleanValue(o);
            } else if ("null".equals(type)) {
                return null;
            }
            return o;
        }

        @Override public boolean isUnique(Object o) {
            return o instanceof JsonObject || o instanceof JsonArray;
        }

        @Override public Integer compare(Object a, Object b, String test) {
            return null;
        }

        public String toString() {
            return "[JSR353Context]";
        }

    }

}
