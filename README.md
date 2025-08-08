# ZPath and ZTemplate

A Java implementation of ZPath / ZTemplates from <a href="https://zpath.me">https://zpath.me</a>

* Javadoc is always available at <a href="https://faceless2.github.io/zpath/docs/">https://faceless2.github.io/zpath/docs/</a>
* Jar is available at <a href="https://faceless2.github.io/zpath/dist/zpath-0.3.jar">https://faceless2.github.io/zpath/dist/zpath-0.3.jar</a>

## ZPath API
```java
import me.zpath.ZPath;
import java.util.List;

Configuration config = new Configuration();
Object context = ...;
ZPath path = ZPath.compile("table/tr[td]", config);
List<Object> match = path.evaluate(context).all();   // List of zero or more matches
Object match = ZPath.evaluate(context).first();      // The first match, or null if none
```

A ZPath can be compiled once and reused in multiple threads.
`context` can be any type of object recognised by an `EvalFactory` registered with the implementation. The API ships with implementations for:

* `org.w3c.dom.Node`
* `javax.json` or `jakarta.json` (any JSR353 implementation)
* `com.bfo.json.Json` (see http://faceless2.github.io/json)
* `com.google.gson.JsonElement` (see https://github.com/google/gson)
* `java.util.Map` and `java.util.Collection` - traversed without reflection (tested with Jackson and Gson)


## ZTemplate API
```java
import me.zpath.ZTemplate;
import java.io.Reader;

Object context = ...;
Reader reader = ...;
Configuration config = new Configuration();
ZTemplate template = ZTemplate.compile(reader, config);
reader = template.apply(context);

// Or to write to an Appendable
Appendable out = new StringBuilder();
template.apply(context, out);
```

A ZTemplate can be compiled once and reused in multiple threads.
`context` can be any type of object accepted by ZPath.

By default the "include" functionality is not enabled, for security, but it's
easy to add if required, either using a default implementation to load from the
file, or implement <code>me.zpath.Includer</code>

```java
import me.zpath.ZTemplate;
import me.zpath.Includer;
import me.zpath.Configuration;
import java.io.*;

Includer includer = Includer.getDefault(new File("dir"));
Configuration conf = new Configuration().setTemplateIncluder(includer);
Reader reader = new InputStreamReader(new FileInputStream("dir/template.zt"), "UTF-8");
ZTemplate template = ZTemplate.compile(reader, conf);
```

## ZPath with BFO Json
This is a complete example, using the "multiline string" syntax from Java 15 and based
on the "BFO Json" library from https://github.com/faceless2/json
```java
import com.bfo.json.Json;
import me.zpath.ZPath;

public class Test{
 public static void main(String...args) {
  Json json = Json.read("""
  {
   "person": {
    "name": {
     "first": "John",
     "last": "Smith"
    },
    "age": 27,
    "books": [
     {
      "name": "A Clockwork Orange",
      "author": "Anthony Burgess"
     },
     {
      "name": "Wolf Hall",
      "author": "Hilary Mantel"
     },
     {
      "name": "The First Man",
      "author": "Albert Camus"
     }
    ]
   }
  }
  """);

  ZPath.compile("person/name/first").eval(json).first(); // "John"

  ZPath.compile("count(person/books/*)").eval(json).first(); // 3

  ZPath.compile("**/author").eval(json).all();
    // ["Anthony Burgess", "Hilary Mantel", "Albert Camus"]
 }
}
```
