package me.zpath.ext;

import me.zpath.*;
import me.zpath.Node;
import org.w3c.dom.*;
import java.util.*;

/** 
 * A Node factory for the BFO Json API from <a href="https://faceless2.github.com/json">https://faceless2.github.com/json</a>
 */
public class DomFactory implements NodeFactory {
    
    final List<Function> functions = new ArrayList<Function>();

    public DomFactory() {
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
            return new Proxy((org.w3c.dom.Node)proxy, null, -1);
        }
        return null;
    }

    private static class Proxy implements Node {

        final int index;
        final String key;
        final org.w3c.dom.Node proxy;

        Proxy(org.w3c.dom.Node node, String key, int index) {
            this.proxy = node;
            this.key = key;
            this.index = index;
        }

        public Iterator<Node> get(String key) {
            if (proxy instanceof Element) {
                Element elt = (Element)proxy;
                if (key.startsWith("@")) {
                    if (key.equals("@*")) {
                        return new Iterator<Node>() {
                            int i = 0;
                            public boolean hasNext() {
                                return i < elt.getAttributes().getLength();
                            }
                            public Node next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                Attr attr = (Attr)elt.getAttributes().item(i);
                                Node node = new Proxy(attr, attr.getName(), -1);
                                i++;
                                return node;
                            }
                        };
                    } else {
                        Attr attr = elt.getAttributeNode(key.substring(1));
                        if (attr != null) {
                            return Collections.<Node>singleton(new Proxy(attr, attr.getName(), -1)).iterator();
                        }
                    }
                } else {
                    return new Iterator<Node>() {
                        org.w3c.dom.Node node;
                        {
                            node = proxy.getFirstChild();
                            while (node != null && !(node instanceof Element && (key.equals("*") || node.getNodeName().equals(key)))) {
                                node = node.getNextSibling();
                            }
                        }
                        public boolean hasNext() {
                            return node != null;
                        }
                        public Node next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Proxy out = new Proxy(node, key.equals("*") ? node.getNodeName() : key, -1);
                            node = node.getNextSibling();
                            while (node != null && !(node instanceof Element && (key.equals("*") || node.getNodeName().equals(key)))) {
                                node = node.getNextSibling();
                            }
                            return out;
                        }
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            }
            return null;
        }

        public Iterator<Node> get(int key) {
            if (proxy instanceof Element) {
                int i = key;
                for (org.w3c.dom.Node n=proxy.getFirstChild();n!=null;n=n.getNextSibling()) {
                    if (n instanceof Element && i-- == 0) {
                        return Collections.<Node>singleton(new Proxy(n, null, key)).iterator();
                    }
                }
            }
            return null;
        }

        public Node parent() {
            org.w3c.dom.Node node = proxy instanceof Attr ? ((Attr)proxy).getOwnerElement() : proxy.getParentNode();
            return node instanceof Element ? new Proxy(node, null, -1) : null;
        }

        public String stringValue() {
            return proxy.getTextContent();
        }

        public double doubleValue() {
            return Double.NaN;
        }

        public boolean booleanValue() {
            return true;
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
            if (proxy instanceof org.w3c.dom.Element) {
                return "element";
            } else {
                return "text";
            }
            /*
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
            */
        }

        public String toString() {
            return proxy.toString(); //  + (key != null ? "#KEY="+key : index >= 0 ? "#INDEX="+index : "");
        }

    }

    /**
     * The "tag" function offers a way to retrieve the CBOR tag from any object
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
     */


}
