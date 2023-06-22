package me.zpath;

import java.io.*;
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
        BufferedReader r = new BufferedReader(new InputStreamReader(TestHarness.class.getResourceAsStream(name), "UTF-8"));
        String s, type = null;
        Object model = null;
        StringBuilder sb = new StringBuilder();
        int line = 1, test = 0;
        while ((s=r.readLine()) != null) {
            if (type != null) {
                if (s.equals("---- END")) {
                    model = load(type, sb.toString());
                    if (model == null) {
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
                        final String expression = s.substring(0, ix).trim();
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
                                    val.add(com.bfo.json.Json.read(t).stringValue());   // save parsing ourselves
                                } else if ((t.charAt(0) >= '0' && t.charAt(0) <= '9') || t.charAt(0) == '-') {
                                    double d = Double.parseDouble(t);
                                    if (d == (int)d) {
                                        val.add(Integer.valueOf((int)d));
                                    } else {
                                        val.add(Double.valueOf(d));
                                    }
                                } else if (t.equals("true")) {
                                    val.add(Boolean.TRUE);
                                } else if (t.equals("false")) {
                                    val.add(Boolean.FALSE);
                                } else if (t.equals("null")) {
                                    val.add(null);
                                } else if (t.charAt(0) == '/') {
                                    Object o = model;
                                    if (!t.equals("/")) {
                                        for (String key : t.substring(1).split("/")) {
                                            int keyindex = 0;
                                            int j = key.indexOf("[");
                                            if (j >= 0 && key.charAt(key.length() - 1) == ']') {
                                                try {
                                                    keyindex = Integer.parseInt(key.substring(j + 1, key.length() - 1));
                                                    key = key.substring(0, j);
                                                } catch (Exception e2) {
                                                    throw new IllegalArgumentException("Bad XML query on line " + line + ": " + t + "(" + key + ")");
                                                }
                                            } else {
                                                try {
                                                    keyindex = Integer.parseInt(key);
                                                    key = null;
                                                } catch (NumberFormatException e) {}
                                            }
                                            try {
                                                o = child(o, key, keyindex);
                                            } catch (Exception e) {
                                                throw new IllegalArgumentException("Bad query on line " + line + ": \"" + t + "\" (" + key + "[" + keyindex + "])");
                                            }
                                        }
                                    }
                                    val.add(o);
                                } else {
                                   throw new IllegalArgumentException("Bad query on line " + line + ": " + t);
                                }
                            }
                        }
                        test(test, line, model, expression, val, debug == DEBUGALL || debug.get(test));
                        test++;
                    }
                }
            }
            line++;
        }
        return index;
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
            out = p.eval(model).unwrap().all();
            if (expected == null) {
                ok = false;
            } else {
                if (out.size() == expected.size()) {
                    ok = true;
                    for (int i=0;i<out.size();i++) {
                        Object o1 = expected.get(i);
                        Object o2 = out.get(i);
                        if (o1 instanceof Number && o2 instanceof Number) {
                            if (((Number)o1).doubleValue() != ((Number)o2).doubleValue()) {
                                ok = false;
                                break;
                            }
                        } else if (o1 == null ? o2 != null : !o1.equals(o2)) {
                            ok = false;
                            break;
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

    //-------------------------------------------------------------------------------------
    // Implementation specific bits here
    //-------------------------------------------------------------------------------------

    private static Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
//            return com.bfo.json.Json.read(data);
            return com.google.gson.JsonParser.parseString(data);
        } else if (type.equals("CBOR")) {
            return com.bfo.json.Json.read(new StringReader(data), new com.bfo.json.JsonReadOptions().setCborDiag(true));
        } else if (type.equals("XML")) {
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            javax.xml.transform.dom.DOMResult result = new javax.xml.transform.dom.DOMResult();
            transformer.transform(new javax.xml.transform.stream.StreamSource(new StringReader(data)), result);
            org.w3c.dom.Node root = ((org.w3c.dom.Document)result.getNode()).getDocumentElement();
            Stack<org.w3c.dom.Node> q = new Stack<org.w3c.dom.Node>();
            q.push(root);
            while (!q.isEmpty()) {
                org.w3c.dom.Node n = q.pop();
                if (n instanceof org.w3c.dom.Text) {
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

    /**
     * For the supplied parent, return the specified child.
     * @param key if not null, the access is by string, otherwise it is an indexed child
     * @param index which matching child (zero for the first)
     * @return the child - if a string/double/boolean/null primitive it should return the primitive
     * @throws RuntimeException if the child is not found
     */
    private static Object child(Object parent, String key, int index) {
        if (parent instanceof com.bfo.json.Json) {
            com.bfo.json.Json child;
            if (key != null) {
                child = ((com.bfo.json.Json)parent).get(key);
            } else {
                child = ((com.bfo.json.Json)parent).get(index);
            }
            if (child == null) {
                throw new RuntimeException();
            } else if (child.isString()) {
                return child.stringValue();
            } else if (child.isNumber()) {
                double d = child.doubleValue();
                return d == (int)d ? Integer.valueOf((int)d) : Double.valueOf(d);
            } else if (child.isBoolean()) {
                return child.booleanValue();
            } else {
                return child;
            }
        } else if (parent instanceof org.w3c.dom.Node) {
           for (org.w3c.dom.Node n = ((org.w3c.dom.Node)parent).getFirstChild();n!=null;n=n.getNextSibling()) {
               if ((key == null || n instanceof org.w3c.dom.Element && n.getNodeName().equals(key)) && index-- == 0) {
                   return n instanceof org.w3c.dom.Text ? n.getNodeValue() : n;
               }
           }
            throw new RuntimeException();
        } else if (parent instanceof com.google.gson.JsonElement) {
           com.google.gson.JsonElement child;
           if (key != null) {
               child = ((com.google.gson.JsonObject)parent).get(key);
           } else {
               child = ((com.google.gson.JsonArray)parent).get(index);
           }
           if (child == null) {
                throw new RuntimeException();
           } else if (child.isJsonNull()) {
               return null;
           } else if (child.isJsonPrimitive()) {
               com.google.gson.JsonPrimitive p = (com.google.gson.JsonPrimitive)child;
               if (p.isString()) {
                   return p.getAsString();
               } else if (p.isNumber()) {
                   return p.getAsNumber();
               } else if (p.isBoolean()) {
                   return p.getAsBoolean();
               }
           }
           return child;
        }
        return null;
    }

}
