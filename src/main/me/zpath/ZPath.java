package me.zpath;

import java.util.*;

/**
 * ZPath is a syntax for traversing a structure object, like a JSON object or XML, and retrieving
 * matching nodes. It's similar in concept to XPath and JSONPath and JMESPath, but with an eye
 * on legibility and consistency.
 */
public class ZPath {

    private final Term term;
    private final Configuration config;

    private ZPath(Term term, Configuration config) {
        this.term = term;
        this.config = config;
    }

    public String toString() {
        return term.toString();
    }

    /**
     * Compile the specified ZPath expression.
     * The returned value is constant, and can be used across multiple threads simultaneously
     * @param value the value
     * @return the compiled ZPath
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static ZPath compile(String value) {
        return compile(value, null);
    }

    /**
     * Compile the specified ZPath expression.
     * The returned value is constant, and can be used across multiple threads simultaneously
     * @param value the value
     * @param config the Configuration to use, or <code>null</code> to use the default Configuration
     * @return the compiled ZPath
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static ZPath compile(String value, Configuration config) {
        if (config == null) {
            config = Configuration.getDefault();
        }
        config = new Configuration(config);     // So it can be modified if required
        if (config.isDebug()) {
            config = config.debugIndent();
            config.depth = 0;
        }
        if (config.isDebug()) {
            config.debug("ZPath.compile \"" + value + "\"");
        }
        CursorList<Term> in = tokenize(value);
        if (config.isDebug()) {
            config.debugIndent().debug("tokens:");
            config.debugIndent().debugIndent().debug(in.toString());
        }
//        System.out.println("T="+in);
        if (config.isDebug()) {
            config.debugIndent().debug("ast:");
        }
        Term term = parseOperand(in, config.debugIndent().debugIndent());
        if (in.tell() != in.size()) {
            error(in, "bad token");
        }
        return new ZPath(term, config);
    }

    /**
     * <p>
     * Evaluate this ZPath against the supplied object.
     * The object may implement {@link Node} - if it does not, the
     * factories {@link Configuration#getFactories} registered with the
     * Configuration will be searched to find a matching instance to 
     * create a Node from the object. If the object cannot be created,
     * this throws {@link IllegalArgumentException}
     * </p><p>
     * The return value is a list of Objects that match the specified expression;
     * they will be from the structure reachable from the supplied Object, or
     * may be constants (Strings, Numbers, Boolean) if the ZPath evaluates to
     * that type of object.
     * </p><p>
     * If no nodes are found, an empty list is returned.
     * </p>
     * @param object the context object
     * @return a List of objects that match the specified ZPath, or an empty list if no objects are found
     */
    public List<Object> eval(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Object is null");
        }
        Node node = null;
        if (object instanceof Node) {
            node = (Node)object;
        } else {
            for (NodeFactory factory : config.getFactories()) {
                node = factory.create(object, config);
                if (node != null) {
                    break;
                }
            }
        }
        if (node == null) {
            throw new IllegalArgumentException("Can't create Node from " + object);
        }
        List<Node> out = new ArrayList<Node>();
        if (config.isDebug()) {
            config.debug("ZPath.eval " + node);
        }
        Configuration config = this.config.debugIndent();
        term.eval(Collections.<Node>singleton(node), out, config);
        if (config.isDebug()) {
            if (out.isEmpty()) {
                config.debug("result: no matches");
            } else {
                config.debug("result:");
                config = config.debugIndent();
                for (Node n : out) {
                    config.debug(n.toString());
                }
            }
        }
        List<Object> oout = new ArrayList<Object>(out.size());
        for (Node n : out) {
            oout.add(n.proxy());
        }
        return oout;
    }

    /**
     * Call {@link #eval} with the supplied object and return the first value,
     * or null if there were no matches
     * @param object the context object
     * @return the first object that match the specified ZPath, or null if no objects are found
     */
    public Object evalSingle(Object object) {
        List<Object> l = eval(object);
        return l.isEmpty() ? null : l.get(0);
    }

    private static void error(CursorList<Term> in, String err) {
        throw new IllegalStateException("Error: " + err + " " + in);
    }

    private static Path parsePath(CursorList<Term> in, Configuration config) {
        int i, len = in.size();
        List<Axis> out = new ArrayList<Axis>();
        Term t;
        boolean first = true;
        while ((t=in.next()) != null) {
//            System.out.println("* t="+t);
            if (t.isName()) {
                out.add(Axis.axisName(t.value));
            } else if (t.isInteger() && !first) {
                out.add(Axis.axisIndex(Integer.parseInt(t.value)));
            } else if (t == Term.STAR) {
                out.add(Axis.axisName(t.value));
            } else if (t == Term.STARSTAR) {
                out.add(Axis.SELFORANYDESCENDENT);
            } else if (t == Term.AT) {
                out.add(Axis.KEY);
            } else if (t == Term.DOTDOT) {
                out.add(Axis.PARENT);
            } else if (t == Term.DOT) {
                out.add(Axis.SELF);
            } else if (t == Term.LBRACE) {
                int start = in.tell(), d = 1;
                while ((t=in.next()) != null) {
                    if (t == Term.LBRACE) {
                        d++;
                    } else if (t == Term.RBRACE) {
                        if (--d == 0) {
                            break;
                        }
                    }
                }
                if (d == 0) {
                    CursorList<Term> sub = in.sub(start, in.tell() - 1);
                    Term ex = parseExpression(sub, config);
                    if (sub.tell() != sub.size()) {
                        error(in, "expression failed");
                    }
                    out.add(Axis.axisMatch(ex));
                } else {
                    in.seek(start - 1);
                    error(in, "mismatched brace");
                }
            } else if (t == Term.SLASH) {
                if (first) {
                    out.add(Axis.ROOT);
                }
                t = in.peek();
                if (t != null && t != Term.DOT && t != Term.DOTDOT && !t.isInteger() && !t.isName() && t != Term.AT && t != Term.STARSTAR && t != Term.STAR && !(t == Term.LBRACE && first)) {
                    error(in, "can't follow slash");     // Not recoverable
                }
                t = in.peek();
            } else {
                in.seek(in.tell() - 1);
                break;
            }
            first = false;
        }
        return out.isEmpty() ? null : new Path(out);
    }

    private static Term parseFunction(CursorList<Term> in, Configuration config) {
        Term t;
        List<Term> args = new CursorList<Term>();
        String name = in.next().value;
        while ((t=in.next()) != null && t != Term.RPAREN) {
            Term parsed = parseOperand(new CursorList<Term>(Collections.<Term>singleton(t)), config);
            if (parsed == null) {
                parsed = t;
            }
            args.add(parsed);
        }
        if (t == null) {
            error(in, "missing rparen");
        }
        Function f = config.getFunction(name);
        if (f == null) {
            f = new FunctionAxis.Dynamic(name);     // Fail now or later?
        }
        if (!f.verify(args)) {
            error(in.seek(in.tell() - 1), "invalid function arguments for \"" + name + "\"");
        }
        return new FunctionAxis(f, args);
    }

    private static Term parseOperand(CursorList<Term> in, Configuration config) {
        Term t = in.peek();
        if (t.isInteger() || t.isReal() || t.isString()) {
            return in.next();
        } else if (t.isFunction()) {
            return parseFunction(in, config);
        } else {
            return parsePath(in, config);
        }
    }

    private static Term parseExpression(CursorList<Term> in, Configuration config) {
        final int tell = in.tell();
        Stack<Term> stack = new Stack<Term>();
        List<Term> out = new ArrayList<Term>();
        // First convert infix->postfix with stack
        // https://www2.cs.arizona.edu/classes/cs127b/fall15/infix.pdf
        Term t;
        while ((t=in.next()) != null) {
            if (t == Term.WS) {
                // noop
            } else if (t == Term.LPAREN) {
                stack.add(t);
            } else if (t == Term.RPAREN) {
                while (!stack.isEmpty()) {
                    t = stack.pop();
                    if (t == Term.LPAREN) {
                        t = null;
                        break;
                    } else {
                        out.add(t);
                    }
                }
                if (t != null) {
                    error(in, "Unbalanced close brace");
                }
            } else if (t.score > 0 && !(t == Term.SLASH && in.peek().isName())) {
                Term t2;
                while (!stack.isEmpty() && ((t2=stack.peek()).score <= t.score)) {
                    out.add(stack.pop());
                }
                stack.add(t);
            } else {
                in.seek(in.tell() - 1);
                Term op = parseOperand(in, config);
                if (op != null) {
                    out.add(op);
                    while (!stack.isEmpty() && stack.peek().isUnary()) {     // unary operator special handling
                        out.add(stack.pop());
                    }
                } else {
                    break;
                }
            }
        }
        while (!stack.isEmpty()) {
            out.add(stack.pop());
        }

        // Now convert our postfix stack to single term
        for (int i=0;i<out.size();i++) {
            t = out.get(i);
            if (t.score > 0) {
                if (t.isUnary() && stack.size() > 0) {
                    Term a = stack.pop();
                    t = new Calc(a, t, null);
                    stack.add(t);
                } else if (stack.size() > 1) {
                    Term b = stack.pop();
                    Term a = stack.pop();
                    t = new Calc(a, t, b);
                    stack.add(t);
                } else {
                    error(in.seek(tell), "invalid expression");
                }
            } else {
                stack.add(t);
            }
        }
        if (stack.size() != 1) {
            error(in.seek(tell), "invalid expression");
        }
        t = stack.pop();
        return t;
    }

    private static CursorList<Term> tokenize(String in) {
        CursorList<Term> tokens = new CursorList<Term>();
        final int len = in.length();
        for (int i=0;i<len;i++) {
            int c = in.codePointAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                while (i + 1 < len && ((c=in.codePointAt(i + 1)) == ' ' || c == '\t' || c == '\r' || c == '\n')) {
                    i++;
                }
                tokens.add(Term.WS);
            } else if (c == '.') {
                if (i + 1 < len && in.codePointAt(i + 1) == '.') {
                    i++;
                    tokens.add(Term.DOTDOT);
                } else {
                    tokens.add(Term.DOT);
                }
            } else if (c == '/') {
                tokens.add(Term.SLASH);
            } else if (c == '[') {
                tokens.add(Term.LBRACE);
            } else if (c == ']') {
                tokens.add(Term.RBRACE);
            } else if (c == '(') {
                tokens.add(Term.LPAREN);
            } else if (c == ')') {
                tokens.add(Term.RPAREN);
            } else if (c == '+') {
                tokens.add(Term.PLUS);
            } else if (c == '-' && (i + 1 == len || in.codePointAt(i + 1) > '9' || in.codePointAt(i + 1) < '0')) {
                tokens.add(Term.MINUS);
            } else if (c == '*') {
                if (i + 1 < len && in.codePointAt(i + 1) == '*') {
                    tokens.add(Term.STARSTAR);
                    i++;
                } else {
                    tokens.add(Term.STAR);
                }
            } else if (c == '/') {
                tokens.add(Term.SLASH);
            } else if (c == '!') {
                if (i + 1 < len && in.codePointAt(i + 1) == '=') {
                    tokens.add(Term.NE);
                    i++;
                } else {
                    tokens.add(Term.BANG);
                }
            } else if (c == '&') {
                if (i + 1 < len && in.codePointAt(i + 1) == '&') {
                    tokens.add(Term.AND);
                    i++;
                } else {
                    tokens.add(Term.BITAND);
                }
            } else if (c == '|') {
                if (i + 1 < len && in.codePointAt(i + 1) == '|') {
                    tokens.add(Term.OR);
                    i++;
                } else {
                    tokens.add(Term.BITOR);
                }
            } else if (c == '=' && i + 1 < len && in.codePointAt(i + 1) == '=') {
                tokens.add(Term.EQ);
                i++;
            } else if (c == '@' && (i + 1 == len || Term.DELIMITERS.indexOf(in.codePointAt(i + 1)) >= 0)) {
                tokens.add(Term.AT);
            } else if (c == '>') {
                if (i + 1 < len && in.codePointAt(i + 1) == '=') {
                    tokens.add(Term.GE);
                    i++;
                } else {
                    tokens.add(Term.GT);
                }
            } else if (c == '<') {
                if (i + 1 < len && in.codePointAt(i + 1) == '=') {
                    tokens.add(Term.LE);
                    i++;
                } else {
                    tokens.add(Term.LT);
                }
            } else if (c == '"' || c == '\'') {
                StringBuilder sb = new StringBuilder();
                int quote = c;
                for (++i;i<len;i++) {
                    c = in.codePointAt(i);
                    if (c == '\\' && i + 1 < len) {
                        c = in.codePointAt(++i);
                        switch (c) {
                            case 'n': c = '\n'; break;
                            case 'r': c = '\r'; break;
                            case 't': c = '\t'; break;
                        }
                        sb.appendCodePoint(c);
                    } else if (c == quote) {
                        break;
                    } else {
                        sb.appendCodePoint(c);
                    }
                }
                if (i == len) {
                    throw new IllegalArgumentException("Tokenzation error at " + i + ": EOF");
                } else {
                    tokens.add(Term.newString(sb.toString()));
                }
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                int start = i;
                if (c == '-') {
                    i++;
                }
                boolean integer = true;
                for (++i;i<len;i++) {
                    c = in.codePointAt(i);
                    if (c == '.' && integer) {
                        integer = false;
                    } else if (Term.DELIMITERS.indexOf(c) >= 0) {
                        break;
                    } else if (c < '0' || c > '9') {
                        throw new IllegalArgumentException("Tokenzation error at " + i + ": bad number starting at " + start);
                    }
                }
                if (integer) {
                    tokens.add(Term.newInteger(Integer.valueOf(in.substring(start, i)).toString()));
                } else {
                    tokens.add(Term.newReal(Double.valueOf(in.substring(start, i)).toString()));
                }
                i--;
            } else if (Character.isAlphabetic(c)) {
                StringBuilder sb = new StringBuilder();
                sb.appendCodePoint(c);
                for (++i;i<len;i++) {
                    c = in.codePointAt(i);
                    if (c == '\\' && i + 1 < len) {
                        c = in.codePointAt(++i);
                    } else if (Term.DELIMITERS.indexOf(c) >= 0) {
                        break;
                    } else {
                        sb.appendCodePoint(c);
                    }
                }
                if (c == '(') {
                    tokens.add(Term.newFunction(sb.toString()));
                } else {
                    tokens.add(Term.newName(sb.toString()));
                    i--;
                }
            } else {
                throw new IllegalArgumentException("Tokenzation error at " + i + ": " + (c > 0x20 && c < 0x7f ? "\"" + ((char)c) + "\"" : "U+" + Integer.toHexString(c)));
            }
        }
        return tokens;
    }

}
