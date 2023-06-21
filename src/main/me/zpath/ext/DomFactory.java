package me.zpath.ext;

import me.zpath.*;
import me.zpath.Node;
import org.w3c.dom.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class DomFactory implements NodeFactory {
    
    private static final int UNKNOWN_INDEX = -2;
    final List<Function> functions = new ArrayList<Function>();

    public DomFactory() {
        functions.add(new Function() {
            @Override public String getName() {
                return "uri";
            }
            @Override public boolean verify(List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    if (node instanceof Proxy) {
                        Proxy p = (Proxy)node;
                        String v = p.proxy.getNamespaceURI();
                        if (v != null && v.length() > 0) {
                            out.add(Node.create(v));
                        }
                    }
                }
            }
        });
        functions.add(new Function() {
            @Override public String getName() {
                return "local-name";
            }
            @Override public boolean verify(List<Term> args) {
                return args.size() <= 1;
            }
            @Override public void eval(List<Term> args, List<Node> in, List<Node> out, final Configuration config) {
                for (Node node : args.isEmpty() ? in : args.get(0).eval(in, new ArrayList<Node>(), config)) {
                    if (node instanceof Proxy) {
                        Proxy p = (Proxy)node;
                        String v = p.proxy.getLocalName();
                        if (v != null && v.length() > 0) {
                            out.add(Node.create(v));
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
    public Node create(Object proxy, Configuration config) {
        if (proxy instanceof org.w3c.dom.Node) {
            for (Function f : functions) {
                config.registerFunction(f);
            }
            if (proxy instanceof org.w3c.dom.Document) {
                proxy = ((org.w3c.dom.Document)proxy).getDocumentElement();
            }
            return new Proxy((org.w3c.dom.Node)proxy, UNKNOWN_INDEX);
        }
        return null;
    }

    private static class Proxy implements Node {

        private final org.w3c.dom.Node proxy;
        private int index;

        Proxy(org.w3c.dom.Node node, int index) {
            this.proxy = node;
            this.index = index;
        }

        @Override public Iterator<Node> get(Object keyobj) {
            if (keyobj == WILDCARD || keyobj instanceof String) {
                String key = keyobj == WILDCARD ? null : (String)keyobj;
                if (proxy instanceof Element) {
                    Element elt = (Element)proxy;
                    if (key != null && key.startsWith("@")) {
                        if (key.equals("@*")) {
                            return new Iterator<Node>() {
                                int i = 0;
                                @Override public boolean hasNext() {
                                    return i < elt.getAttributes().getLength();
                                }
                                @Override public Node next() {
                                    if (!hasNext()) {
                                        throw new NoSuchElementException();
                                    }
                                    Node node = new Proxy((Attr)elt.getAttributes().item(i), i);
                                    i++;
                                    return node;
                                }
                                @Override public void remove() {
                                    throw new UnsupportedOperationException();
                                }
                            };
                        } else {
                            Attr attr = elt.getAttributeNode(key.substring(1));
                            if (attr != null) {
                                return Collections.<Node>singleton(new Proxy(attr, -1)).iterator();
                            } else {
                                return Collections.<Node>emptySet().iterator();
                            }
                        }
                    } else {
                        return new Iterator<Node>() {
                            org.w3c.dom.Node node;
                            int index;
                            { step(); }
                            @Override public boolean hasNext() {
                                return node != null;
                            }
                            @Override public Node next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                Proxy out = new Proxy(node, index);
                                step();
                                return out;
                            }
                            @Override public void remove() {
                                throw new UnsupportedOperationException();
                            }
                            private void step() {
                                if (node == null) {
                                    node = proxy.getFirstChild();
                                    index = 0;
                                } else {
                                    node = node.getNextSibling();
                                    index++;
                                }
                                if (key != null) {
                                    while (node != null && !(node instanceof Element && node.getNodeName().equals(key))) {
                                        node = node.getNextSibling();
                                        index++;
                                    }
                                }
                            }
                        };
                    }
                }
            } else if (keyobj instanceof Integer) {
                if (proxy instanceof Element) {
                    final int index = ((Integer)keyobj).intValue();
                    org.w3c.dom.Node n;
                    int i = index;
                    for (n = proxy.getFirstChild();n!=null && i-- > 0;n=n.getNextSibling());
                    if (n != null) {
                        return Collections.<Node>singletonList(new Proxy(n, index)).iterator();
                    } else {
                        return Collections.<Node>emptySet().iterator();
                    }
                }
            }
            return null;
        }

        @Override public Node parent() {
            org.w3c.dom.Node node = proxy instanceof Attr ? ((Attr)proxy).getOwnerElement() : proxy.getParentNode();
            return node instanceof Element ? new Proxy(node, UNKNOWN_INDEX) : null;
        }

        @Override public String stringValue() {
            return proxy.getTextContent();
        }

        @Override public double doubleValue() {
            return Double.NaN;
        }

        @Override public boolean booleanValue() {
            return true;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Proxy && ((Proxy)o).proxy == proxy;
        }

        @Override public int hashCode() {
            return proxy.hashCode();
        }

        @Override public Object key() {
            if (proxy instanceof org.w3c.dom.Element) {
                return proxy.getNodeName();
            } else if (proxy instanceof org.w3c.dom.Attr) {
                return ((Attr)proxy).getName();
            } else {
                return null;
            }
        }

        @Override public int index() {
            if (index == UNKNOWN_INDEX) {
                index = -1;
                if (parent() != null) {
                    int i = 0;
                    for (org.w3c.dom.Node n = proxy.getParentNode().getFirstChild();n!=null;n=n.getNextSibling()) {
                        if (n == proxy) {
                            index = i;
                            break;
                        }
                        i++;
                    }
                }
            }
            return index;
        }

        @Override public Object proxy() {
            return proxy instanceof Attr ? ((Attr)proxy).getValue() : proxy;
        }

        @Override public String type() {
            if (proxy instanceof org.w3c.dom.Element) {
                return "element";
            } else if (proxy instanceof org.w3c.dom.Attr) {
                return "attr";
            } else if (proxy instanceof org.w3c.dom.Text) {
                return "text";
            } else if (proxy instanceof org.w3c.dom.Comment) {
                return "comment";
            } else if (proxy instanceof org.w3c.dom.ProcessingInstruction) {
                return "processing-instruction";
            } else {    // Meh
                return "node";
            }
        }

        @Override public String toString() {
            return proxy.toString(); //  + (name != null ? "#KEY="+name : index >= 0 ? "#INDEX="+index : "");
        }
    }

}
