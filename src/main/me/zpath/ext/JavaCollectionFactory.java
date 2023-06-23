package me.zpath.ext;

import me.zpath.*;
import java.util.*;

/** 
 * A EvalFactory that evaluates {@link Collection} and {@link Map} objects without reflection
 */
public class JavaCollectionFactory implements EvalFactory {
    
    public JavaCollectionFactory() {
    }

    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public EvalContext create(Object o, Configuration config) {
        if (o instanceof Collection || o instanceof Map) {
            return new MyContext(config);
        }
        return null;
    }

    private static class MyContext implements EvalContext {

        private static class ReverseLookup {
            final Object parent;
            final Object key;
            final int index;
            ReverseLookup(Object parent, Object key, int index) {
                this.parent = parent;
                this.key = key;
                this.index = index;
            }
        }

        private final Map<Object,ReverseLookup> registry;
        private final Configuration config;
        private int contextIndex = -1;
        private List<Object> contextObjects;

        MyContext(Configuration config) {
            this.config = config;
            this.registry = new HashMap<Object,ReverseLookup>();
        }

        private void register(Object child, Object parent, Object key, int index) {
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
            if (o instanceof Collection) {
                @SuppressWarnings("unchecked") final Collection<Object> list = (Collection<Object>)o;
                if (key == WILDCARD) {
                    return new Iterable<Object>() {
                        public Iterator<Object> iterator() {
                            final Iterator<Object> i = list.iterator();
                            return new Iterator<Object>() {
                                int index = 0;
                                public Object next() {
                                    Object elt = i.next();
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
                        Object elt;
                        if (list instanceof List) {
                            elt = ((List)list).get(index);
                        } else {
                            Iterator<Object> i = list.iterator();
                            elt = i.next();
                            for (int j=0;j<index;j++) {
                                elt = i.next();
                            }
                        }
                        register(elt, list, Integer.valueOf(index), index);
                        return Collections.<Object>singletonList(elt);
                    }
                }
            } else if (o instanceof Map) {
                @SuppressWarnings("unchecked") Map<Object,Object> map = (Map<Object,Object>)o;
                if (key == WILDCARD) {
                    return new Iterable<Object>() {
                        public Iterator<Object> iterator() {
                            final Iterator<Map.Entry<Object,Object>> i = map.entrySet().iterator();
                            return new Iterator<Object>() {
                                public Object next() {
                                    Map.Entry<Object,Object> e = i.next();
                                    Object elt = e.getValue();
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
                } else {
                    Object elt = map.get(key);
                    if (elt != null || map.containsKey(key)) {
                        register(elt, map, key, -1);
                        return Collections.<Object>singletonList(elt);
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
            return null;
        }

        @Override public Number numberValue(Object o) {
            return null;
        }

        @Override public boolean booleanValue(Object o) {
            return true;
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
            if (o instanceof Map) {
                return "map";
            } else if (o instanceof Collection) {
                return "list";
            }
            return null;
        }

        @Override public Object value(Object o) {
            return o;
        }

    }

}
