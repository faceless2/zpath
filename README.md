# ZPath and ZTemplate

A Java implementation of ZPath / ZTemplates from http://zpath.me

## Zpath API
```java
import me.zpath.ZPath;

Object context = ...;
ZPath path = ZPath.compile("table/tr[td]");
List<Object> match = ZPath.evaluate(context).all();  // List of zero or more matches
Object match = ZPath.evaluate(context).first();      // The first match, or null if none
```

A ZPath can be compiled once and reused in multiple threads.
`context` can be any type of object recognised by an `EvalFactory` registered with the implementation. The API ships with implementations for:

* `org.w3c.dom.Node`
* `com.bfo.json.Json` (see http://faceless2.github.io/json)
* `com.google.gson.JsonElement` (see https://github.com/google/gson)
* `java.util.Map` and `java.util.Collection` - traversed without reflection (tested with Jackson and Gson)


## Ztemplate API
```java
import me.zpath.ZTemplate;

Object context = ...;
Reader reader = ...;
ZTemplate template = ZTemplate.compile(reader);
reader = template.apply(object);

// Or to write to an Appendable
Appendable out = new StringBuilder();
template.apply(object, out);
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

Includer includer = Includer.getDefault(new File("dir"));
Configuration conf = new Configuration().setTemplateIncluder(includer);
Reader reader = new InputStreamReader(new FileInputStream("dir/template.zt"), "UTF-8");
ZTemplate template = ZTemplate.compile(reader, conf);
```
