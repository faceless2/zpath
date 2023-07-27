package me.zpath;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;

class XMLTestEngine implements TestEngine {

    public String toString() {
        return "XML DOM";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("XML")) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMResult result = new DOMResult();
            transformer.transform(new StreamSource(new StringReader(data)), result);
            Node root = ((Document)result.getNode()).getDocumentElement();
            Stack<Node> q = new Stack<Node>();
            q.push(root);
            while (!q.isEmpty()) {
                org.w3c.dom.Node n = q.pop();
                if (n instanceof Text) {
                    String s = n.getNodeValue();
                    boolean ok = false;
                    for (int i=0;i<s.length();i++) {
                        char c = s.charAt(i);
                        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        n.getParentNode().removeChild(n);
                    }
                } else {
                    for (n=n.getFirstChild();n!=null;n=n.getNextSibling()) {
                        q.push(n);
                    }
                }
            }
            return root;
        }
        return null;
    }

    @Override public Object child(Object parent, String key, int index) {
        if (parent instanceof Node) {
            for (Node n = ((Node)parent).getFirstChild();n!=null;n=n.getNextSibling()) {
                if ((key == null || n instanceof Element && n.getNodeName().equals(key)) && index-- == 0) {
                    return n instanceof Text ? n.getNodeValue() : n;
                }
            }
        }
        throw new RuntimeException();
    }

}

