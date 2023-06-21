package me.zpath;

import com.bfo.json.*;
import java.io.*;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;
import java.util.*;

public class TestHarness {

    private static final BitSet DEBUGALL = new BitSet();
    private static final Object FAIL = new Object();
    static int index;

    public static void main(String[] args) throws Exception {
        BitSet debug = new BitSet();
        for (int i=0;i<args.length;i++) {
            String s = args[i];
            if (s.equals("--debug")) {
                for (String t : args[++i].split(",")) {
                    if (t.equals("all")) {
                        debug = DEBUGALL;
                    } else if (debug != DEBUGALL) {
                        debug.set(Integer.parseInt(t));
                    }
                }
            } else {
                throw new IllegalArgumentException(s);
            }
        }
        tests("tests.txt", debug, 0);
    }

    private static int tests(String name, BitSet debug, int index) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        BufferedReader r = new BufferedReader(new InputStreamReader(TestHarness.class.getResourceAsStream(name), "UTF-8"));
        String s, type = null;
        Object model = null;
        StringBuilder sb = new StringBuilder();
        int line = 1, test = 0;
        while ((s=r.readLine()) != null) {
            if (type != null) {
                if (s.equals("---- END")) {
                    if (type.equals("JSON")) {
                        model = Json.read(sb.toString());
                    } else if (type.equals("CBOR")) {
                        model = Json.read(new StringReader(sb.toString()), new JsonReadOptions().setCborDiag(true));
                    } else if (type.equals("XML")) {
                        Transformer transformer = transformerFactory.newTransformer();
                        DOMResult result = new DOMResult();
                        transformer.transform(new StreamSource(new StringReader(sb.toString())), result);
                        model = result.getNode();
                        Stack<org.w3c.dom.Node> q = new Stack<org.w3c.dom.Node>();
                        q.push(((Document)model).getDocumentElement());
                        while (!q.isEmpty()) {
                            org.w3c.dom.Node n = q.pop();
                            if (n instanceof org.w3c.dom.Text) {
                                s = n.getNodeValue();
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

                    } else {
                        throw new IllegalStateException("Unknown model type " + type);
                    }
                    type = null;
                } else {
                    sb.append(s);
                    sb.append('\n');
                }
            } else {
                int ix;
                if (s.length() > 0 && s.charAt(0) == '#' && (s.length() == 1 || !Character.isDigit(s.charAt(1)))) {
                    s = "";
                } else if ((ix=s.indexOf("\t#")) >= 0) {
                    while (ix > 0 && s.charAt(ix - 1) == '\t') {
                        ix--;
                    }
                    s = s.substring(0, ix);
                }
                if (s.startsWith("---- BEGIN ")) {
                    type = s.substring(11);
                    sb.setLength(0);
                } else if (s.length() > 0) {
                   ix = s.indexOf("\t");
                   if (ix < 0) {
                       throw new IllegalStateException("No tab on line " + line + ": " + s);
                   } else {
                       String key = s.substring(0, ix).trim();
                       List<Object> val;
                       if (s.endsWith("\tERROR")) {
                           val = null;
                       } else if (s.endsWith("\tNULL")) {
                           val = Collections.<Object>emptyList();
                       } else {
                           val = new ArrayList<Object>();
                           for (String t : s.substring(s.lastIndexOf("\t") + 1).split(",")) {
                               t = t.trim();
                               if (t.charAt(0) == '"') {
                                   val.add(Json.read(t).stringValue());
                               } else if ((t.charAt(0) >= '0' && t.charAt(0) <= '9') || t.charAt(0) == '-') {
                                   try {
                                       val.add(Integer.parseInt(t));
                                   } catch (Exception e) {
                                       val.add(Double.parseDouble(t));
                                   }
                               } else if (t.equals("true")) {
                                   val.add(Boolean.TRUE);
                               } else if (t.equals("false")) {
                                   val.add(Boolean.FALSE);
                               } else if (t.equals("null")) {
                                   val.add(null);
                               } else if (t.charAt(0) == '/') {
                                   if (model instanceof Json) {
                                       Json j = (Json)model;
                                       if (!t.equals("/")) {
                                           for (String q : t.substring(1).split("/")) {
                                               try {
                                                   ix = Integer.parseInt(q);
                                                   j = j.get(ix);
                                               } catch (NumberFormatException e) {
                                                   j = j.get(q);
                                               }
                                               if (j == null) {
                                                   throw new IllegalArgumentException("Bad JSON query on line " + line + ": \"" + t + "\" (" + q + ")");
                                               }
                                           }
                                       }
                                       val.add(j);
                                   } else if (model instanceof Document) {
                                       org.w3c.dom.Node node = ((Document)model).getDocumentElement();
                                       if (!t.equals("/")) {
                                           for (String q : t.substring(1).split("/")) {
                                               try {
                                                   ix = Integer.parseInt(q);
                                                   for (org.w3c.dom.Node n = node.getFirstChild();n!=null;n=n.getNextSibling()) {
                                                       if (ix-- == 0) {
                                                           node = n;
                                                           break;
                                                       }
                                                   }
                                                   if (ix != -1) {
                                                       throw new IllegalArgumentException("Bad XML query on line " + line + ": " + t + "(" + q + ")");
                                                   }
                                               } catch (NumberFormatException e) {
                                                   int j = q.indexOf("[");
                                                   if (j >= 0 && q.charAt(q.length() - 1) == ']') {
                                                       try {
                                                           ix = Integer.parseInt(q.substring(j + 1, q.length() - 1));
                                                           q = q.substring(0, j);
                                                       } catch (Exception e2) {
                                                           throw new IllegalArgumentException("Bad XML query on line " + line + ": " + t + "(" + q + ")");
                                                       }
                                                   } else {
                                                       ix = 0;
                                                   }
                                                   for (org.w3c.dom.Node n = node.getFirstChild();n!=null;n=n.getNextSibling()) {
                                                       if (n instanceof Element && n.getNodeName().equals(q) && ix-- == 0) {
                                                           node = n;
                                                           break;
                                                       }
                                                   }
                                                   if (ix != -1) {
                                                       throw new IllegalArgumentException("Bad XML query on line " + line + ": " + t + "(" + q + ")");
                                                   }
                                               }
                                           }
                                       }
                                       val.add(node);
                                   }
                               } else {
                                   throw new IllegalArgumentException("Bad query on line " + line + ": " + t);
                                }
                            }
                        }
                        test(test, line, model, key, val, debug == DEBUGALL || debug.get(test));
                        test++;
                    }
                }
            }
            line++;
        }
        return index;
    }

    private static Object unwrap(Object o) {
        if (o instanceof Json) {
            Json j = (Json)o;
            if (j.isString()) {
                o = j.stringValue();
            } else if (j.isNumber()) {
                o = j.numberValue();
                double d = ((Number)o).doubleValue();
                if (d == (int)d) {
                    o = Integer.valueOf((int)d);
                }
            } else if (j.isBoolean()) {
                o = j.booleanValue();
            } else if (j.isNull()) {
                o = null;
            }
        } else if (o instanceof Number) {
            double d = ((Number)o).doubleValue();
            if (d == (int)d) {
                o = Integer.valueOf((int)d);
            }
        }
        return o;
    }

    private static boolean test(int index, int line, Object model, String path, List<Object> expected, boolean debug) {
        boolean ok = false;
        List<Object> out = null;
        try {
            Configuration config = Configuration.getDefault();
            if (debug) {
                config = new Configuration(config);
                config.setLogger(Configuration.Logger.create(System.out));
            }
            ZPath p = ZPath.compile(path, config);
            out = p.eval(model);
            if (expected == null) {
                ok = false;
            } else {
                if (out.size() == expected.size()) {
                    ok = true;
                    for (int i=0;i<out.size();i++) {
                        Object o1 = unwrap(expected.get(i));
                        Object o2 = unwrap(out.get(i));
                        if (o1 == null ? o2 != null : !o1.equals(o2)) {
                            ok = false;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            if (expected == null) {
                ok = true;
            } else {
                e.printStackTrace(System.out);
                ok = false;
            }
        }
        if (ok) {
            System.out.println(String.format("%03d", index) + " OK   \"" + path + "\"");
        } else {
            System.out.println(String.format("%03d", index) + " FAIL \"" + path + "\" expected " + expected + " got " + out + " (line " + line + ")");
        }
        return ok;
    }

}
