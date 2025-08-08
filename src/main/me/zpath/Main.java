package me.zpath;

import java.io.*;
import java.util.*;

class Main {

    private static String jsonsupport;
    static {
        try {
            Class.forName("com.bfo.json.Json");
            jsonsupport = "bfo";
        } catch (Throwable e) {
            try {
                Class.forName("java.json.Json");
                jsonsupport = "jsr";
            } catch (Throwable e2) {
                try {
                    Class.forName("jakarta.json.Json");
                    jsonsupport = "jakarta";
                } catch (Throwable e3) {
                    try {
                        Class.forName("com.google.gson.Gson");
                        jsonsupport = "gson";
                    } catch (Throwable e4) { }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean stdin = false;
        String format = "auto";
        List<ZPath> expr = new ArrayList<ZPath>();
        ZTemplate template = null;
        for (int i=0;i<args.length;i++) {
            String s = args[i];
            if ((s.equals("--eval") || s.equals("-e")) && ++i < args.length) {
                if (template != null) {
                    help("Specified both an zpath and a ztemplate");
                }
                expr.add(ZPath.compile(args[i]));
            } else if ((s.equals("--format") || s.equals("-f")) && ++i < args.length) {
                format = s;
            } else if (s.equals("--help") || s.equals("-h")) {
                help(null);
            } else if ((s.equals("--template") || s.equals("-t")) && ++i < args.length) {
                s = args[i];
                if (!expr.isEmpty()) {
                    help("Specified both an zpath and a ztemplate");
                }
                if ("-".equals(s)) {
                    if (stdin) {
                        help("Reading from stdin more than once");
                    } else {
                        template = ZTemplate.compile(new InputStreamReader(System.in, "UTF-8"), null);
                        stdin = true;
                    }
                } else {
                    template = ZTemplate.compile(new File(s), null);
                }
            } else {
                if ("-".equals(s)) {
                    if (stdin) {
                        help("Reading from stdin more than once");
                    } else {
                        s = null;
                        stdin = true;
                    }
                } else if ("--".equals(s) && ++i < args.length) {
                    s = args[i];
                } else if (s.startsWith("-") || s.startsWith("--")) {
                    help("Invalid option \"" + s + "\"");
                }
                Object o = read(s, format);
                if (template != null) {
                    template.apply(o, System.out);
                } else if (!expr.isEmpty()) {
                    for (ZPath e : expr) {
                        System.err.println("Evaluating \"" + e + "\" on " + (s == null ? "stdin" : "\"" + s + "\""));
                        for (Object q : e.eval(o).unwrap().all()) {
                            if (q instanceof org.w3c.dom.Element) {
                                q = niceXML((org.w3c.dom.Element)q);
                            }
                            System.out.println(q);
                        }
                    }
                } else {
                    System.out.println(o);
                }
            }
        }
        if (args.length == 0) {
            help(null);
        }
    }

    private static String niceXML(org.w3c.dom.Element e) throws Exception {
        javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
        javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(e);
        StringWriter sb = new StringWriter();
        transformer.transform(source, new javax.xml.transform.stream.StreamResult(sb));
        return sb.toString();
    }

    private static void help(String err) {
        if (err != null) {
            System.err.println("ERROR: " + err);
            System.err.println();
        }
        System.err.println("ZPath version " + ZPath.class.getPackage().getImplementationVersion());
        System.err.println();
        System.err.println("Usage: java -cp zpath.jar me.zpath.Main <options> <files>...");
        System.err.println();
        System.err.println("  Options");
        System.err.println();
        System.err.println("    --help | -h                this message");
        System.err.println("    --eval | -e {zpath}        evaluate a ZPath expression (may be used");
        System.err.println("                               more than once");
        System.err.println("    --template | -t {file}     load a ZTemplate");
        System.err.println("    --format | -f {format}     set the file-format for input files");
        System.err.println();
        System.err.println("  Files are read in and will be processed by a template (if specified");
        System.err.println("  or have the expressions evaluated against it. A filename of \"-\"");
        System.err.println("  reads from stdin, and a filename beginning with a hyphen can be");
        System.err.println("  specified by preceding it with \"--\", eg \"-- -filename\".");
        System.err.println();
        System.err.println("File formats are");
        System.err.println("  auto      try and guess from one of the ones below");
        System.err.println("  xml       supported");
        if ("bfo".equals(jsonsupport)) {
            System.err.println("  json      supported (via BFO API)");
            System.err.println("  cbor      supported (via BFO API)");
            System.err.println("  msgpack   supported (via BFO API)");
        } else if ("jsr".equals(jsonsupport)) {
            System.err.println("  json      supported (via javax JSR353 API)");
            System.err.println("  cbor      not supported");
            System.err.println("  msgpack   not supported");
        } else if ("jakarta".equals(jsonsupport)) {
            System.err.println("  json      supported (via jakarta JSR353 API)");
            System.err.println("  cbor      not supported");
            System.err.println("  msgpack   not supported");
        } else if ("gson".equals(jsonsupport)) {
            System.err.println("  json      supported (via GSON API)");
            System.err.println("  cbor      not supported");
            System.err.println("  msgpack   not supported");
        } else {
            System.err.println("  json      not supported (need BFO or JSR353 API in classpath");
            System.err.println("  cbor      not supported (need BFO API in classpath)");
            System.err.println("  msgpack   not supported (need BFO API in classpath)");
        }
        System.err.println();
        System.exit(err == null ? 0 : 1);
    }

    private static Object read(String path, String format) {
        InputStream in = null;
        try {
            in = path == null ? System.in : new FileInputStream(path);
            if (path == null) {
                path = "-";
            }
            in = new BufferedInputStream(in);
            if ("auto".equals(format)) {
                in.mark(8);
                int c = in.read();
                if (c == '{' || c == '[') {
                    format = "json";
                } else if (c == '<') {
                    format = "xml";
                } else {
                    System.err.println("ERROR: Unabled to guess format from \"" + path + "\"");
                    System.exit(3);
                }
                in.reset();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed reading \"" + path + "\": " + e.getMessage());
            System.exit(2);
        }
        try {
            if ("xml".equals(format)) {
                javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                javax.xml.transform.dom.DOMResult result = new javax.xml.transform.dom.DOMResult();
                transformer.transform(new javax.xml.transform.stream.StreamSource(in), result);
                return result.getNode();
            } else if ("json".equals(format) && "bfo".equals(jsonsupport)) {
                // return com.bfo.json.Json.read(in, null);     // For API 1.6
                return com.bfo.json.Json.read(in);              // For API 2.0
            } else if ("cbor".equals(format) && "bfo".equals(jsonsupport)) {
                // return com.bfo.json.Json.readCbor(in, null); // For API 1.6
                return com.bfo.json.Json.readCbor(in);          // For API 2.0
            } else if ("msgpack".equals(format) && "bfo".equals(jsonsupport)) {
                // return com.bfo.json.Json.readMsgpack(in, null);      // For API 1.6
                return com.bfo.json.Json.read(new com.bfo.json.MsgpackReader().setInput(in));   // For API 2.0
            } else if ("json".equals(format) && "jsr".equals(jsonsupport)) {
                return javax.json.Json.createReader(in).read();
            } else if ("json".equals(format) && "jakarta".equals(jsonsupport)) {
                return jakarta.json.Json.createReader(in).read();
            } else if ("json".equals(format) && "gson".equals(jsonsupport)) {
                return new com.google.gson.Gson().fromJson(new InputStreamReader(in, "UTF-8"), com.google.gson.JsonElement.class);
            } else {
                System.err.println("ERROR: No reader for format \"" + format + "\"");
                System.exit(4);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed reading \"" + path + "\": " + e.getMessage());
            System.exit(5);
        } finally {
            try {
                in.close();
            } catch (Exception e) {}
        }
        return null;
    }

}
