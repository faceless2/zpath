package me.zpath;

import java.io.*;
import java.util.*;

public class TestHarness {

    private static final BitSet DEBUGALL = new BitSet();
    private static final Object FAIL = new Object();
    static int index;

    public static void main(String[] args) throws Exception {
        BitSet debug = new BitSet();
        List<TestEngine> engines = new ArrayList<TestEngine>();
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
            } else if (s.equals("--test-xml")) {
                engines.add(new XMLTestEngine());
            } else if (s.equals("--test-bfo")) {
                engines.add(new BFOTestEngine());
            } else if (s.equals("--test-jackson")) {
                engines.add(new JacksonTestEngine());
            } else if (s.equals("--test-gson")) {
                engines.add(new GsonTestEngine());
            } else if (s.equals("--test-gson-generic")) {
                engines.add(new GsonGenericTestEngine());
            } else if (s.equals("--test-jsr353")) {
                engines.add(new JSR353TestEngine());
            } else {
                throw new IllegalArgumentException(s);
            }
        }
        if (engines.isEmpty()) {
            engines.add(new XMLTestEngine());
            engines.add(new BFOTestEngine());
            engines.add(new JacksonTestEngine());
            engines.add(new GsonTestEngine());
            engines.add(new GsonGenericTestEngine());
            engines.add(new JSR353TestEngine());
        }
        for (TestEngine engine : engines) {
            System.out.println("# Testing " + engine);
            tests("tests.txt", debug, 0, engine);
        }
    }

    private static int tests(String name, BitSet debug, int index, TestEngine engine) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(TestHarness.class.getResourceAsStream(name), "UTF-8"));
        String s, type = null;
        Object model = null;
        StringBuilder sb = new StringBuilder();
        int line = 1, test = 0;
        while ((s=r.readLine()) != null) {
            if (type != null) {
                if (s.equals("---- END")) {
                    model = engine.load(type, sb.toString());
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
                    } else if (model != null) {
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
                                                o = engine.child(o, key, keyindex);
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
            Configuration config = new Configuration();
            if (debug) {
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
            System.out.println(String.format("%03d", index) + " FAIL \"" + path + "\" expected " + dump(expected) + " got " + dump(out) + " (line " + line + ")");
        }
        return ok;
    }

    private static String dump(List<Object> l) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i=0;i<l.size();i++) {
            Object o = l.get(i);
            if (i > 0) {
                sb.append(',');
            }
            if (o == null) {
                sb.append("null");
            } else {
                sb.append(o);
                sb.append("(" + o.getClass().getName().replaceAll("java.lang.", "") + ")");
            }
        }
        sb.append("]");
        return sb.toString();
    }

}
