package me.zpath.ext;

import me.zpath.*;
import org.w3c.dom.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class DomFactory implements EvalFactory {
    
    private static final List<Function> FUNCTIONS = new ArrayList<Function>();

    public DomFactory() {
        FUNCTIONS.add(new Function() {
            @Override public boolean matches(final String name) {
                return "url".equals(name) || "local-name".equals(name);
            }
            @Override public boolean verify(final String name, final List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(final String name, final List<Term> args, final List<Object> in, final List<Object> out, final EvalContext context) {
                for (Object node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Object>(), context)) {
                    if (node instanceof Node) {
                        Node elt = (Node)node;
                        String v = "url".equals(name) ? elt.getNamespaceURI() : elt.getLocalName();;
                        if (v != null && v.length() > 0) {
                            out.add(v);
                        }
                    }
                }
            }
        });
    }

    /** 
     * Create a new Node, or return null if this factory doesn't apply
     * @param proxy a <code>com.bfo.json</code> object
     */
    public EvalContext create(Object proxy, Configuration config) {
        if (proxy instanceof Node) {
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


        @Override public Iterable<? extends Object> get(Object o, Object keyobj) {
            if (o instanceof Document) {
                o = ((Document)o).getDocumentElement();
            }
            if (o instanceof Element) {
                final Element elt = (Element)o;
                if (keyobj == WILDCARD || keyobj instanceof String) {
                    String key = keyobj == WILDCARD ? null : (String)keyobj;
                    if (key != null && key.startsWith("@")) {
                        if (key.equals("@*")) {
                            return new Iterable<Node>() {
                                public Iterator<Node> iterator() {
                                    return new Iterator<Node>() {
                                        int i = 0;
                                        @Override public boolean hasNext() {
                                            return i < elt.getAttributes().getLength();
                                        }
                                        @Override public Node next() {
                                            if (!hasNext()) {
                                                throw new NoSuchElementException();
                                            }
                                            Node node = elt.getAttributes().item(i);
                                            i++;
                                            return node;
                                        }
                                        @Override public void remove() {
                                            throw new UnsupportedOperationException();
                                        }
                                    };
                                }
                            };
                        } else {
                            Attr attr = elt.getAttributeNode(key.substring(1));
                            if (attr != null) {
                                return Collections.<Object>singleton(attr);
                            }
                        }
                    } else {
                        return new Iterable<Node>() {
                            public Iterator<Node> iterator() {
                                return new Iterator<Node>() {
                                    Node node;
                                    { step(); }
                                    @Override public boolean hasNext() {
                                        return node != null;
                                    }
                                    @Override public Node next() {
                                        if (!hasNext()) {
                                            throw new NoSuchElementException();
                                        }
                                        Node n = node;
                                        step();
                                        return n;
                                    }
                                    @Override public void remove() {
                                        throw new UnsupportedOperationException();
                                    }
                                    private void step() {
                                        if (node == null) {
                                            node = elt.getFirstChild();
                                        } else {
                                            node = node.getNextSibling();
                                        }
                                        if (key != null) {
                                            while (node != null && !(node instanceof Element && node.getNodeName().equals(key))) {
                                                node = node.getNextSibling();
                                            }
                                        }
                                    }
                                };
                            }
                        };
                    }
                } else if (keyobj instanceof Integer) {
                    int index = ((Integer)keyobj).intValue();
                    for (Node n = elt.getFirstChild();n!=null;n=n.getNextSibling()) {
                        if (index-- == 0) {
                            return Collections.<Object>singletonList(n);
                        }
                    }
                }
            }
            return Collections.<Object>emptySet();
        }

        @Override public Object parent(Object o) {
            if (o instanceof Attr) {
                return ((Attr)o).getOwnerElement();
            } else if (o instanceof Node) {
                Node parent = ((Node)o).getParentNode();
                if (parent instanceof Element) {
                    return parent;
                }
            }
            return null;
        }

        @Override public String stringValue(Object o) {
            if (o instanceof Node) {
                return ((Node)o).getTextContent();
            } else {
                return o == null ? null : o.toString();
            }
        }

        @Override public double doubleValue(Object o) {
            if (o instanceof Number) {
                return ((Number)o).doubleValue();
            }
            return Double.NaN;
        }

        @Override public boolean booleanValue(Object o) {
            if (o instanceof Node) {
                return true;
            } else if (o instanceof Boolean) {
                return ((Boolean)o).booleanValue();
            }
            return o != null;
        }

        @Override public Object key(Object o) {
            if (o instanceof Node) {
                return ((Node)o).getNodeName();
            }
            return null;
        }

        @Override public int index(Object o) {
            if (o instanceof Element) {
                Node parent = ((Node)o).getParentNode();
                if (parent instanceof Element) {
                    int index = 0;
                    for (Node n=parent.getFirstChild();n!=null;n=n.getNextSibling()) {
                        if (n == o) {
                            return index;
                        }
                        index++;
                    }
                }
            }
            return -1;
        }

        @Override public String type(Object o) {
            if (o instanceof Element) {
                return "element";
            } else if (o instanceof Attr) {
                return "attr";
            } else if (o instanceof Text) {
                return "text";
            } else if (o instanceof Comment) {
                return "comment";
            } else if (o instanceof ProcessingInstruction) {
                return "processing-instruction";
            } else if (o instanceof Node) {
                return "node";
            }
            return null;
        }

        @Override public Object value(Object o) {
            if (o instanceof Attr) {
                return ((Attr)o).getValue();
            } else if (o instanceof Text) {
                return ((Text)o).getNodeValue();
            }
            return o;
        }
    }

}
