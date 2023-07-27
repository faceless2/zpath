package me.zpath;

import com.google.gson.*;
import java.io.*;
import java.util.*;

class GsonGenericTestEngine implements TestEngine {

    public String toString() {
        return "GSON Generic";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
            return new Gson().fromJson(data, Map.class);   
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
