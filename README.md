
ZPath is a programmer-friendly syntax for searching structured objects. If you can navigate a filesystem and code an if-statement in C, Java or JavaScript, you already know 95% of the syntax.

The grammar is universal: there's currently support for JSON, CBOR, and XML.

| Path | Matches |
| --- | --- |
| `/body` | all nodes called `body` inside the root node |
| `*/td`| all grandchildren of the current node called `td` |
| `**`| the current node and all its descendents |
| `#2`| the third child of the current node (indices starts at zero) |
| `../tr#0` | the first node called `tr` of the current node's parent |
| `../tr[is-first()]` | the same |
| `../tr[index() == 0]` | the same |
| `../tr[index() == 0]/td` | all `td` children of the same|
| `**/tr[count(td) == 2]`| all nodes at or below the current node called `tr` with two `td` children |
| `body[**/tr[count(td) == 2]]`| all `body` children that match the above description |
| `address[!city || type(city) == "null"]` | all `address` children where `city` is missing or set to `null`|
| `address[!!city]` | all `address` children with child `city` not `null` or `false` |
| `[key() != ix]` | all children where the key it's stored as in its parent != its `ix` value|
| `list/*[index() % 2 == 0]` | every even-numbered child of the `list` child |
| `item[is-first() ? "first" : name]` | for all `item` chidren, "first" if it's first, or its `name` child otherwise|
| `count(tr/td)` | if evaluated on a table, the number of cells in the table (a single number) |
| `tr/count(td)` | the number of cells in each row of the table (a list of numbers) |
| `table[@class == "defn"]` | all `table` children where the class attribute equals `defn` (XML only) |
| `[@* == "defn"]` | all children where any attribute equals `defn` (XML only) |
| `table/@class` | the class attribute of any `table` children, as a string (XML only) |
| `**/fruitbowl/*, **/fruit`|all descendents where parent is `fruitbowl` or that are called `fruit`|
| `union(**/fruitbowl/*, **/fruit)`|the same, but with duplicate nodes merged|

## ZPath Parsing

* an **expression** may be a **function**, a **path**, a string (in quotes), a number, or a combination of these using normal C-like operators
* binary operators `+ - * / % && || & |` and the ternary operator `? :` _must_ have white-space to either side
* a **path** is constructed like a UNIX file path, a list of **segments** seperated with `/`
* each path **segment** can be `*`, `**`, `..`, a **name**, an **index** (`#`integer), a combination (**name**`#`integer) or a **function**
* each **segment** may followed by zero or more **qualifying expressions** (an **expression** inside square brackets)
* a **qualifying expression** may never follow a `/` in a path unless it is the root `/`
* a function is a **name** immediately followed by `(`, zero or more **arguments** seperated by commas, then a `)`
* **arguments** depend on the function, but are typically **expressions**
* if a **name** contains `\n \r \t ( ) [ ] / , = & | ! < > #` or space characters, they must be backslash-escaped
* finally, the top-level expression _only_ may be a comma-separateed list of **expressions**

## ZPath Evaluation
* Evaluation starts with a _context node_ which is supplied by the caller, which forms the initial **node set**
* Each path **segment** or **qualifying expression** is applied to the current **node set**, resulting in a new **node set**  which is processed by the next segment or qualifying expression.
* **qualifying expressions** are evaluated for each node in the current **node set** - if _true_ the node is included in the next **node set**. A **path** is _true_ if it evaluates to one or more nodes of any value.
* **function** **segments** are used as in XPath 2.0; they are also called once for each node in the current **node set**, and the output is included in the next **node set**

## Functions

### Structural and Type functions
| Function | Description |
| --- | --- |
| `count()` | the number of nodes in the current **node set** |
| `count(expression)` | the number of nodes matched by **expression**|
| `index()` | the index of this node in the the current **node set** |
| `index(expression)` | for each node matched by **expression**, its index into its parent|
| `key()` | the key to retrieve this node from its parent (an integer for lists, _typically_ a string for maps)|
| `key(expression)` | for each node matched by **expression**, the key to retrieve it from its parent|
| `union(expression, ...)` | the set union of each **expression** (no duplicates) |
| `intersection(expression, ...)` | the set intersection of each **expression** |
| `is-first()` | shorthand for `index() == 0`|
| `is-last()` | shorthand for `index() == count() - 1`|
| `next()` | if this node can be retrieved from its parent with an **index**, the node at the next index |
| `prev()` | the same, but reutrn the node at the previous index |
| `string()` | the string value of the current node (an empty set if no string value exists) |
| `string(expression)` | for each node matched by **expression**, its string value |
| `num()` | the number value of the current node (an empty set if no number value exists)|
| `num(expression)` | for each node matched by **expression**, its number value |
| `type()` | the type of this node as a string|
| `type(expression)` | the type of each node mached by **expression**, or `"undefined"` if it is an empty set|


Types depend on the object type, and is dependent on the backing implementation. It's recommended that:

* JSON has `string`, `number`, `list`, `map`, `boolean` and `null`
* CBOR has all those, and also `buffer`
* XML has at least `element`, `text`, `processing-instruction` and `comment`


### Math functions
| Function | Description |
| --- | --- |
| `ceil()` | the ceiling value of all number nodes in the current **node set** (an empty set if none are numbers)|
| `ceil(expression)` | for each node matched by **expression**, its ceiling value |
| `ceil()` | the floor value of all number nodes in the current **Node set** (an empty set if none are numbers)|| `floor(expression)` | for each node matched by **expression**, its floor value |
| `ceil()` | the rounded value of all number nodes in the current **Node set** (an empty set if none are nubmers)|| | `round(expression)` | for each node matched by **expression**, its rounded value |
| `sum()` | the sum of all number nodes in the current **node set** (0 if none are numbers)|
| `sum(expression)` | the sum of all number of nodes matched by **expression**|
| `min()` | the minimum value of all number nodes in the current **node set** (an empty set if none are numbers)|
| `min(expression)` | the minimum of all number of nodes matched by **expression**|
| `max()` | the maximum value of all number nodes in the current **node set** (an empty set if none are numbers)|
| `max(expression)` | the maximum of all number of nodes matched by **expression**|



## API
```java
import me.zpath.ZPath;

Object context = ...;
ZPath path = ZPath.compile("table/tr[td]")
List<Object> match = ZPath.evaluate(context);
// match contains Strings, Numbers, or objects reachable from the supplied context, or else it is empty
```
`context` can be any type of object recognised by a `NodeFactory` registered with to the implementation. The API ships with implementations for:

* `org.w3c.dom.Node`
* `com.bfo.json.Json` (see http://faceless2.github.io/json)

