package me.zpath;

import java.io.*;
import com.bfo.json.*;

class BFOTestEngine implements TestEngine {

    public String toString() {
        return "BFO JSON/CBOR";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
            return Json.read(data);
        } else if (type.equals("CBOR")) {
            return Json.read(new JsonReader().setCborDiag(true).setInput(data));
        }
        return null;
    }

    @Override public Object child(Object parent, String key, int index) {
        if (parent instanceof Json) {
            Json child;
            if (key != null) {
                child = ((Json)parent).get(key);
            } else {
                child = ((Json)parent).get(index);
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
        }
        throw new RuntimeException();
    }
}
