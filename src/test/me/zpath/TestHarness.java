package me.zpath;

import com.bfo.json.*;
import java.util.*;

public class TestHarness {
    private static final Object FAIL = new Object();
    private Object model;
    private String path;
    private Object[] expected;
    TestHarness(Object model, String path, Object... expected) {
        this.model = model;
        this.path = path;
        this.expected = expected;
    }
    public List<Object> test() {
        ZPath p = ZPath.compile(path);
        List<Object> l = p.eval(model);
        if (l.size() != expected.length) {
            return l;
        } else {
            for (int i=0;i<l.size();i++) {
                if (!unwrap(expected[i]).equals(unwrap(l.get(i)))) {
                    return l;
                }
            }
            return null;
        }
    }
    static Object unwrap(Object o) {
        if (o instanceof Json) {
            Json j = (Json)o;
            if (j.isString()) {
                o = j.stringValue();
            } else if (j.isNumber()) {
                o = j.numberValue();
            } else if (j.isBoolean()) {
                o = j.booleanValue();
            }
        }
        return o;
    }

    static int index;

    private static void test(Object model, String path, Object... expected) {
        List<Object> l = null;
        boolean ok = false;
        try {
            ZPath p = ZPath.compile(path);
            l = p.eval(model);
            if (l.size() == expected.length) {
                ok = true;
                for (int i=0;i<l.size();i++) {
                    if (!unwrap(expected[i]).equals(unwrap(l.get(i)))) {
                        ok = false;
                    }
                }
            }
        } catch (RuntimeException e) {
            if (expected.length == 1 && expected[0] == FAIL) {
                ok = true;
            } else {
                throw e;
            }
        }
        if (ok) {
            System.out.println(String.format("%03d", index) + " OK   \"" + path + "\"");
        } else {
            System.out.println(String.format("%03d", index) + " FAIL \"" + path + "\" expected " + Arrays.toString(expected) + " got " + l);
        }
        index++;
    }

    public static void main(String[] args) throws Exception {
        // https://jsonpath.com
        // {
        //     "firstName": "John",
        //     "lastName": "doe",
        //     "age": 26,
        //     "address": {
        //         "streetAddress": "naist street",
        //         "city": "Nara",
        //         "postalCode": "630-0192"
        //     },
        //     "phoneNumbers": [
        //         {
        //             "type": "iPhone",
        //             "number": "0123-4567-8888"
        //         },
        //         {
        //             "type": "home",
        //             "number": "0123-4567-8910"
        //         }
        //     ]
        // }

        Json j = Json.read("{ \"firstName\": \"John\", \"lastName\": \"doe\", \"age\": 26, \"address\": { \"streetAddress\": \"naist street\", \"city\": \"Nara\", \"postalCode\": \"630-0192\" }, \"phoneNumbers\": [ { \"type\": \"iPhone\", \"number\": \"0123-4567-8888\" }, { \"type\": \"home\", \"number\": \"0123-4567-8910\" } ] }");

        test(j, "firstName", j.get("firstName"));
        test(j, "firstName", "John");
        test(j, "age", 26);
        test(j, "address/city", "Nara");
        test(j, "[age==26]", j);                       // test on current item
        test(j, "*[city=='Nara']", j.get("address"));  // test an all children
        test(j, "**[age==26]", j);                     // test an self and all descendents (finds self)
        test(j, "**[city=='Nara']", j.get("address")); // test an self and all descendents (finds child)
        test(j, "/[age==26]", j);                      // test on root
        test(j, "/" + "*" + "/[city=='Nara']", FAIL);            // disallow square brace after any slash other than leading slash
        test(j, "/*[city=='Nara']", j.get("address")); // test on all children of root item
        test(j, "/**[age==26]", j);                    // test on root and all descendents (finds self)
        test(j, "/**[city]", j.get("address"));        // test on root and all descendents (finds child)
        test(j, "/" + "*" + "/**[city=='Nara']", j.get("address"));// test on all descendents of root
        test(j, "address[city=='Nara']", j.get("address"));
        test(j, "address[age!=26]", j.get("address"));
        test(j, "address[city=='Nara' && age!=26]", j.get("address"));
        test(j, "address[city==\"Nara\" && postalCode=='630-0192']", j.get("address"));
        test(j, "address[city=='Nara' || foo]", j.get("address"));
        test(j, "address[foo || city=='Nara']", j.get("address"));
        test(j, "address[foo && city=='Nara']");
        test(j, "address[foo]");
        test(j, "address[city]", j.get("address"));
        test(j, "address[city && postalCode]", j.get("address"));
        test(j, "address[city || foo]", j.get("address"));
        test(j, "address[city && 2+3==5]", j.get("address"));
        test(j, "phoneNumbers/0", j.get("phoneNumbers").get(0));
        test(j, "phoneNumbers/*[type=='home']", j.get("phoneNumbers").get(1));
        test(j, "phoneNumbers/*[type=='home']/number", j.get("phoneNumbers").get(1).get("number"));
        test(j, "/**[type=='home']", j.get("phoneNumbers").get(1));
        test(j, "/**[type=='home']/number", j.get("phoneNumbers").get(1).get("number"));
        test(j, "/address[!foo]", j.get("address"));
        test(j, "/address[!city]");
        test(j, "/address/*[@!='city']", j.get("address").get("streetAddress"), j.get("address").get("postalCode"));
        test(j, "/address/@", "address");
        test(j, "/address/city/..", j.get("address"));
        test(j, ".", j);
        test(j, "./address", j.get("address"));
        test(j, "/address/.", j.get("address"));
        test(j, "/address/././city/..", j.get("address"));
        test(j, "phoneNumbers/*[type=='home']/@", 1);
        test(j, "/address/../..");
        test(j, "/..");
        test(j, "..");
        test(j, "../*");
        test(j, "@");
        test(j, "../@");
        test(j, "count(phoneNumbers)", 2);

        // TODO
        // -- @ means "my key"A, 
    }
}
