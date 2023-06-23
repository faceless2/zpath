package me.zpath.ext;

import me.zpath.*;
import com.bfo.json.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class BfoJsonFactory implements EvalFactory {
    
    private static final List<Function> FUNCTIONS = new ArrayList<Function>();

    static {
        FUNCTIONS.add(new Function() {
            @Override public boolean matches(final String name) {
                return "tag".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, final List<Term> args, final List<Object> in, final List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    if (node instanceof Json) {
                        Json json = (Json)node;
                        long tag = json.getTag();
                        if (tag >= 0) {
                            out.add(tag == (int)tag ? Integer.valueOf((int)tag) : Long.valueOf(tag));
                        }
                    }
                }
            }
        });
    }

    public EvalContext create(final Object o, Configuration config) {
        if (o instanceof Json) {
            return new MyContext(config);
        }
        return null;
    }

    private static class MyContext implements EvalContext {

        private final Configuration config;
        private int contextIndex = -1;
        private List<Object> contextObjects;

        MyContext(Configuration config) {
            this.config = config;
        }

        @Override public Configuration getConfiguration() {
            return config;
        }

        @Override public Configuration.Logger getLogger() {
            return config.getLogger();
        }

        @Override public Function getFunction(String name) {
            for (Function f : FUNCTIONS) {
                if (f.matches(name)) {
                    return f;
                }
            }
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

        @Override public Iterable<? extends Object> get(final Object o, final Object key) {
            if (o instanceof Json) {
                Json json = (Json)o;
                if (key == WILDCARD) {
                    if (json.isList()) {
                        return json.listValue();
                    } else if (json.isMap()) {
                        return json.mapValue().values();
                    }
                } else {
                    json = json.get(key);
                    if (json != null) {
                        return Collections.<Object>singleton(json);
                    }
                }
            }
            return Collections.<Object>emptyList();
        }

        @Override public Object parent(final Object o) {
            return o instanceof Json ? ((Json)o).parent() : null;
        }

        @Override public String stringValue(final Object o) {
            if (o instanceof Json) {
                return ((Json)o).stringValue();
            }
            return null;
        }

        @Override public Number numberValue(final Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                if (json.isNumber()) {
                    return json.numberValue();
                }
            }
            return null;
        }

        @Override public boolean booleanValue(final Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                if (json.isBoolean()) {
                    return json.booleanValue();
                } else if (json.isNull() || json.isUndefined()) {
                    return false;
                }
                return true;
            }
            return true;
        }

        @Override public Object key(final Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                Json parent = json.parent();
                if (parent == null) {
                    return null;
                } else if (parent.isList()) {
                    return parent.listValue().indexOf(json);
                } else {
                    for (Map.Entry<Object,Json> e : parent.mapValue().entrySet()) {
                        if (e.getValue() == json) {
                            return e.getKey();
                        }
                    }
                }
            }
            return null;
        }

        @Override public int index(final Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                Json parent = json.parent();
                if (parent != null && parent.isList()) {
                    return parent.listValue().indexOf(json);
                }
            }
            return -1;
        }

        @Override public String type(final Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                if (json.isString()) {
                    return "string";
                } else if (json.isNumber()) {
                    return "number";
                } else if (json.isBoolean()) {
                    return "boolean";
                } else if (json.isMap()) {
                    return "map";
                } else if (json.isList()) {
                    return "list";
                } else if (json.isNull()) {
                    return "null";
                } else if (json.isUndefined()) {
                    return "undefined";
                } else if (json.isBuffer()) {
                    return "buffer";
                } else {
                    return "unknown";       // Shouldn't happen
                }
            }
            return null;
        }

        public Object value(Object o) {
            if (o instanceof Json) {
                Json json = (Json)o;
                if (json.isString()) {
                    return json.stringValue();
                } else if (json.isNumber()) {
                    return json.numberValue();
                } else if (json.isBoolean()) {
                    return json.booleanValue();
                } else if (json.isNull() || json.isUndefined()) {
                    return null;
                }
            }
            return o;
        }

    }

}
