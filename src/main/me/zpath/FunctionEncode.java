package me.zpath;

import java.util.*;

class FunctionEncode implements Function {

    public String getName() {
        return "encode";
    }

    public boolean verify(List<Term> arguments) {
        return true;
    }

    public String toString() {
        return getName() + "()";
    }

    public void eval(final List<Term> arguments, final Collection<Node> in, final Collection<Node> out, final Configuration config) {
        double max = Double.NaN;
        if (config.isDebug()) {
            config.debug(this + " " + arguments + " ctx=" + in);
        }
        Set<Node> nodes = new HashSet<Node>();
        for (Term t : arguments) {
            t.eval(in, nodes, config.debugIndent());
        }
        StringBuilder sb = null;
        for (Node node : nodes) {
            String s = node.stringValue();
            if (s != null) {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                encodeXML(s, true, sb);
            }
        }
        if (sb != null) {
            out.add(Node.create(sb.toString()));
        }
    }

    private static void encodeXML(String s, boolean attribute, StringBuilder sb) {
        int len = s.length();
        for (int i=0;i<len;) {
            final int c = s.codePointAt(i);
            if (c < 0x80) {      // ASCII range: test most common case first
                if (c < 0x20 && (c != '\t' && c != '\r' && c != '\n')) {
                    // Illegal XML character, even encoded. Skip or substitute
                    sb.append("&#xfffd;");   // Unicode replacement character
                } else {
                    switch(c) {
                      case '&':  sb.append("&amp;"); break;
                      case '>':  sb.append("&gt;"); break;
                      case '<':  sb.append("&lt;"); break;
                      case '\'':  if (attribute) { sb.append("&apos;"); break; }
                      case '\"':  if (attribute) { sb.append("&quot;"); break; }
                      default:   sb.append((char)c);
                    }
                }
            } else if ((c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff) {
                // Illegal XML character, even encoded. Skip or substitute
                sb.append("&#xfffd;");   // Unicode replacement character
            } else {
                sb.append("&#x");
                sb.append(Integer.toHexString(c));
                sb.append(';');
            }
            i += c < 0x10000 ? 1 : 2;
        }
    }


}
