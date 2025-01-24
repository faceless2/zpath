package me.zpath;

import com.google.gson.*;
import java.io.*;
import java.util.*;

class GsonTestEngine implements TestEngine {

    public String toString() {
        return "GSON JsonElement";
    }

    @Override public Object load(String type, String data) throws Exception {
        if (type.equals("JSON")) {
            return new Gson().fromJson(data, JsonElement.class);                // Gson JsonParser to JsonElement

        }
        return null;
    }

    @Override public Object child(Object parent, String key, int index) {
        if (parent instanceof JsonElement) {
            JsonElement child;
            if (key != null) {
                child = ((JsonObject)parent).get(key);
            } else {
                child = ((JsonArray)parent).get(index);
            }
            if (child == null) {
                throw new RuntimeException();
            } else if (child.isJsonNull()) {
                return null;
            } else if (child.isJsonPrimitive()) {
                JsonPrimitive p = (JsonPrimitive)child;
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
        throw new RuntimeException();
    }

}
