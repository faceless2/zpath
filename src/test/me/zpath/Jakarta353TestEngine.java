package me.zpath;

import jakarta.json.*;
import java.io.*;
import java.util.*;

class Jakarta353TestEngine implements TestEngine {

    public String toString() {
        return "Jakarta353";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
            JsonReader reader = Json.createReader(new StringReader(data));
            return reader.read();
        }
        return null;
    }

    @Override public Object child(Object parent, String key, int index) {
        if (parent instanceof JsonStructure) {
            JsonValue child;
            if (key != null) {
                child = ((JsonObject)parent).get(key);
            } else {
                child = ((JsonArray)parent).get(index);
            }
            if (child == null) {
                throw new RuntimeException();
            } else if (child == JsonValue.NULL) {
                return null;
            } else if (child == JsonValue.TRUE) {
                return Boolean.TRUE;
            } else if (child == JsonValue.FALSE) {
                return Boolean.FALSE;
            } else if (child instanceof JsonNumber) {
                JsonNumber num = (JsonNumber)child;
                if (num.isIntegral()) {
                    try {
                        return num.intValueExact();
                    } catch (Exception e) { 
                        try {
                            return num.longValueExact();
                        } catch (Exception e2) { 
                            return num.bigIntegerValue();
                        }
                    }
                } else {
                    return num.doubleValue();
                }
            } else if (child instanceof JsonString) {
                return ((JsonString)child).getString();
            }
            return child;
        }
        throw new RuntimeException();
    }

}
