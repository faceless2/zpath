package me.zpath;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;

class JacksonTestEngine implements TestEngine {

    public String toString() {
        return "Jackson Generic";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
            return new ObjectMapper().readerFor(Map.class).readValue(data);      // Jackson to Collection
        }
        return null;
    }

    @Override public Object child(Object parent, String key, int index) {
        if (parent instanceof Map) {
            return key != null ? ((Map)parent).get(key) : ((Map)parent).get(index);
        } else if (parent instanceof List) {
            return ((List)parent).get(index);
        }
        throw new RuntimeException();
    }

}
