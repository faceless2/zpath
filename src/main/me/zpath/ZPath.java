package me.zpath;

import java.util.*;

/**
 * ZPath is a syntax for traversing a structure object, like a JSON object or XML, and retrieving
 * matching nodes. It's similar in concept to XPath and JSONPath and JMESPath, but with an eye
 * on legibility and consistency.
 */
public class ZPath {

    // Delimiters break parsing of a name component in a path
    static final String PATH_DELIMITERS = " \t\r\n()[]/,=&|!<>#";
    static final String NUMBER_DELIMITERS = PATH_DELIMITERS + "*/+-&|!=<>";

    private final List<Term> terms;
    private final Configuration config;

    private ZPath(List<Term> terms, Configuration config) {
        this.terms = terms;
        this.config = config;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<terms.size();i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(terms.get(i).toString());
        }
        return sb.toString();
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

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        return o instanceof ZPath && toString().equals(o.toString());
    }

    /**
     * Return the Configuration used to compile this ZPath
     */
    public Configuration getConfiguration() {
        return config;
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
        final Configuration.Logger logger = config.getLogger();
        if (logger != null) {
            logger.log("ZPath.compile \"" + value + "\"");
        }
        CursorList<Term> in = tokenize(value);
        if (logger != null) {
            logger.enter();
            logger.log("tokens:");
            logger.enter();
            logger.log(in.toString());
            logger.exit();
            logger.exit();
        }
        // Trim whitespace from start and end
        if (in.size() > 0 && in.get(0) == Term.WS) {
            in.remove(0);
        }
        if (in.size() > 0 && in.get(in.size() - 1) == Term.WS) {
            in.remove(in.size() - 1);
        }
        if (logger != null) {
            logger.enter();
            logger.log("ast:");
            logger.enter();
        }
        try {
            List<Term> terms = new ArrayList<Term>();
            terms.add(parseExpression(in, config));
            while (in.peek() == Term.COMMA) {
                in.next();
                while (in.peek() == Term.WS) {
                    in.next();
                }
                terms.add(parseExpression(in, config));
            }
            if (in.tell() != in.size()) {
                throw error(in, "bad token");
            }
            if (logger != null) {
                for (Term term : terms) {
                    term.log(logger);
                }
            }
            return new ZPath(terms, config);
        } finally {
            if (logger != null) {
                logger.exit();
                logger.exit();
            }
        } 
    }

    /**
     * Evaluate this ZPath against the supplied object. Calls <code>eval(object, null)</code>
     * @return the Result
     */
    public Result eval(final Object node) {
        return eval(node, null);
    }

    /**
     * <p>
     * Evaluate this ZPath against the supplied object, using the specified {@link EvalContext}.
     * If the context is null, {@link Configuration#getFactories} will be scanned for one that
     * can create an {@link EvalContext} for the supplied object.
     * If none are found, throws {@link IllegalArgumentException}
     * </p><p>
     * The return value is {@link Result} containing a list of objects that match the specified expression.
     * They will be either reachable via the {@link EvalContext} from the supplied object,
     * or String, Number, or Boolean constants if the ZPath evaluates to that type of object.
     * </p>
     * @param object the context object
     * @param context the context to evaluate that object in, or null to find one that matches
     * @return the Result
     */
    public Result eval(final Object node, EvalContext context) {
        if (node == null) {
            throw new IllegalArgumentException("Node is null");
        }
        if (context == null) {
            for (EvalFactory factory : config.getFactories()) {
                context = factory.create(node, config);
                if (context != null) {
                    break;
                }
            }
        }
        if (context == null) {
            throw new IllegalArgumentException("No EvalFactory for " + node.getClass().getName());
        }
        List<Object> out = new ArrayList<Object>();
        try {
            if (context.getLogger() != null) {
                context.getLogger().log("ZPath.eval " + node);
                context.getLogger().enter();
            }
            for (Term term : terms) {
                term.eval(Collections.<Object>singletonList(node), out, context);
            }
            if (context.getLogger() != null) {
                if (out.isEmpty()) {
                    context.getLogger().log("result: no matches");
                } else {
                    context.getLogger().log("result:");
                    context.getLogger().enter();
                    for (Object n : out) {
                        context.getLogger().log(n.toString());
                    }
                    context.getLogger().exit();
                }
            }
        } finally {
            if (context.getLogger() != null) {
                context.getLogger().exit();
            }
        }
        for (int i=0;i<out.size();i++) {
            if (out.get(i) == EvalContext.NULL) {
                out.set(i, null);
            }
        }
        return new Result(this, out, context);
    }

    private static IllegalStateException error(CursorList<Term> in, String err) {
        return new IllegalStateException("Error: " + err + " " + in);
    }

    private static Path parsePath(CursorList<Term> in, Configuration config) {
        int i, len = in.size();
        List<Axis> out = new ArrayList<Axis>();
        Term t;
        boolean first = true, slash = false, root = false;
        StringBuilder sb = new StringBuilder();
        while ((t=in.next()) != null) {
//            System.out.println("* t="+t);
            if (t.isName()) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    String name = t.value;
                    for (int j=0;j<name.length();j++) {
                        char c = name.charAt(j);
                        if (c == '\n') {
                            sb.append("\\n");
                        } else if (c == '\r') {
                            sb.append("\\r");
                        } else if (c == '\t') {
                            sb.append("\\t");
                        } else if (c == '\\' || PATH_DELIMITERS.indexOf(c) >= 0) {
                            sb.append("\\");
                            sb.append(c);
                        } else {
                            sb.append(c);
                        }
                    }
                    int index = in.peek() != null && in.peek().isIndex() ? in.next().indexValue() : Axis.ANYINDEX;
                    if (index >= 0) {
                        sb.append("#" + index);
                    }
                    out.add(Axis.axisKey(t.value, index));
                }
                root = slash = false;
            } else if (t.isIndex()) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else if (in.peek() != null && in.peek().isIndex()) {         // Matches #1#2, required for bags of items keyed on integer (eg CBOR)
                    int index = in.next().indexValue();
                    sb.append("#" + t.indexValue());
                    sb.append("#" + index);
                    out.add(Axis.axisKey(t.indexValue(), index));
                } else {
                    out.add(Axis.axisKey(null, t.indexValue()));
                    sb.append("#" + t.indexValue());
                }
                root = slash = false;
            } else if (t == Term.STAR) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    int index = in.peek() != null && in.peek().isIndex() ? in.next().indexValue() : Axis.ANYINDEX;
                    out.add(Axis.axisKey(EvalContext.WILDCARD, index));
                    sb.append(t.toString());
                }
                root = slash = false;
            } else if (t == Term.STARSTAR) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    out.add(Axis.SELFORANYDESCENDENT);
                    sb.append(t.toString());
                }
                root = slash = false;
            } else if (t == Term.DOTDOT) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    out.add(Axis.PARENT);
                    sb.append(t.toString());
                }
                root = slash = false;
            } else if (t == Term.DOT) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    out.add(Axis.SELF);
                    sb.append(t.toString());
                }
                root = slash = false;
            } else if (t.isFunction()) {
                if (!first && !slash) {
                    throw error(in, "bad path");
                } else {
                    in.seek(in.tell() - 1);
                    Term function = parseFunction(in, config, true);
                    out.add(function);
                    sb.append(function.toString());
                }
                root = slash = false;
            } else if (t == Term.LBRACE) {
                if (!first && slash && !root) {
                    throw error(in, "bad path");
                } else {
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
                            throw error(in, "expression failed");
                        }
                        out.add(Axis.axisMatch(ex));
                        sb.append('[');
                        sb.append(ex);
                        sb.append(']');
                    } else {
                        in.seek(start - 1);
                        throw error(in, "mismatched brace");
                    }
                }
                slash = false;
            } else if (t == Term.SLASH) {
                if (slash) {
                    throw error(in, "bad path");
                } else if (first) {
                    out.add(Axis.ROOT);
                    root = true;
                }
                sb.append('/');
                slash = true;
            } else {
                in.seek(in.tell() - 1);
                break;
            }
            first = false;
        }
        if (slash && !root) {
            throw error(in, "bad path: ends with slash");
        } else if (out.isEmpty()) {
            return null;
        } else {
            return new Path(out, sb.toString());
        }
    }

    private static Term parseFunction(CursorList<Term> in, Configuration config, boolean path) {
        Term t;
        List<Term> args = new CursorList<Term>();
        String name = in.next().value;
        int start = in.tell();
        while ((t=in.peek()) != null && t != Term.RPAREN) {
            if (t == Term.WS) {
                in.next();
            } else {
                args.add(parseExpression(in, config));
                if ((t=in.peek()) == Term.COMMA) {
                    in.next();
                } else if (t != Term.RPAREN) {
                    throw error(in.seek(start), "invalid function arguments for \"" + name + "\"");
                }
            }
        }
        if (t == null) {
            throw error(in, "missing rparen");
        } else {
            in.next();
        }
        // Try and find the function at compile time if we can,
        // but some are specific to the EvaluationContext so
        // accept null
        Function function = null;
        for (Function f : config.getFunctions()) {
            if (f.matches(name)) {
                function = f;
                break;
            }
        }
        if (function != null && !function.verify(name, args)) {
            throw error(in.seek(in.tell() - 1), "invalid function arguments for \"" + name + "\"");
        }
        return new FunctionAxis(function, name, args, path);
    }

    private static Term parseOperand(CursorList<Term> in, Configuration config) {
        Term t = in.peek();
        if (t.isInteger() || t.isReal() || t.isString()) {
            return in.next();
        } else if (t.isFunction()) {
            return parseFunction(in, config, false);
        } else {
            return parsePath(in, config);
        }
    }

    /**
     * Parse a single expression from the list "in"
     */
    private static Term parseExpression(CursorList<Term> in, Configuration config) {
        final int tell = in.tell();
        Stack<Term> stack = new Stack<Term>();
        List<Term> out = new ArrayList<Term>();
        // First convert infix->postfix with stack.
        // Shunting yard algo with special handling for middle term of ternary a?b:c (wrap b in paren), unary,
        // Also identify where what is usually an operator (*, / etc) is actually an operand because its part of a path
        Term t;
        Term operand = null, operator = null;
        boolean expectingOperand = true;
        while ((t=in.next()) != null) {
            final Term ft = t;
            if (t == Term.COMMA) {
                in.seek(in.tell() - 1);     // backup
                break;
            } else if (t == Term.WS) {
                operand = operator = null;
                // noop
            } else if (t == Term.LPAREN) {
                operand = operator = null;
                stack.add(t);
                expectingOperand = true;
            } else if (t == Term.RPAREN || t == Term.COLON) {
                operand = operator = null;
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
                    in.seek(in.tell() - 1);     // read an extra rparen - backup
                    break;
                }
                t = ft;
                expectingOperand = false;
            } 
            if (t.isUnaryOperator()) {
                stack.add(t);
                expectingOperand = true;
            } else if (t.isBinaryOperator() && !expectingOperand) {
                operator = t;
                if (operand != null) {
                    throw error(in.seek(tell), "invalid expression: no whitespace between " + operand + " and '" + operator + "'");
                }
                while (!stack.isEmpty() && (t=stack.peek()).isOperator() && t.compareTo(operator) <= 0) {
                    out.add(stack.pop());
                }
                stack.add(operator);
                if (operator == Term.QUESTION) {
                    stack.add(Term.LPAREN);
                }
                expectingOperand = true;
            } else if (t != Term.WS && t != Term.LPAREN && t != Term.RPAREN) { 
                in.seek(in.tell() - 1);
                operand = parseOperand(in, config);
                if (operand != null) {
                    if (operator != null) {
                        throw error(in.seek(tell), "invalid expression: no whitespace between " + operator + " and '" + operand + "'");
                    }
                    out.add(operand);
                    expectingOperand = false;
                } else {
                    // not an operand; break;
                    break;
                }
            }
//            System.out.println("* " + ft + ": stack="+stack+" out="+out+" expectingOperand="+expectingOperand);
        }
        while (!stack.isEmpty()) {
            out.add(stack.pop());
        }

        // Now convert our postfix stack to single term
        for (int i=0;i<out.size();i++) {
            t = out.get(i);
            if (t == Term.QUESTION) {
                // do nothing
                stack.add(t);
            } else if (t == Term.COLON && stack.size() >= 4) {
                Term c = stack.pop();
                Term q = stack.pop();
                Term b = stack.pop();
                Term a = stack.pop();
                if (q != Term.QUESTION) {
                    throw error(in.seek(tell), "invalid ternary expression: (" + a + " " + q + " " + b + " : " + c + ")");
                }
                t = new Expr(q, a, b, c);
                stack.add(t);
            } else if (t.isUnaryOperator() && stack.size() >= 1) {
                Term a = stack.pop();
                t = new Expr(t, a, null);
                stack.add(t);
            } else if (t.isBinaryOperator() && stack.size() >= 2) {
                Term b = stack.pop();
                Term a = stack.pop();
                t = new Expr(t, a, b);
                stack.add(t);
            } else if (t.isOperator()) {
                throw error(in.seek(tell), "invalid expression");
            } else {
                stack.add(t);
            }
        }
        if (stack.size() != 1) {
            throw error(in.seek(tell), "invalid expression");
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
            } else if (c == '?') {
                tokens.add(Term.QUESTION);
            } else if (c == '%') {
                tokens.add(Term.PERCENT);
            } else if (c == ',') {
                tokens.add(Term.COMMA);
            } else if (c == ':') {
                tokens.add(Term.COLON);
            } else if (c == ',') {
                tokens.add(Term.COMMA);
            } else if (c == '#' && i + 1 < len && in.codePointAt(i + 1) <= '9' && in.codePointAt(i + 1) >= '0') {
                int start = ++i;
                for (;i<len;i++) {
                    c = in.codePointAt(i);
                    if (PATH_DELIMITERS.indexOf(c) >= 0) {
                        break;
                    } else if (c < '0' || c > '9') {
                        throw error(in, i, "bad index");
                    }
                }
                tokens.add(Term.newIndex(Integer.parseInt(in.substring(start, i))));
                i--;
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
                    if (i + 2 < len && in.codePointAt(i + 2) == '=') {
                        tokens.add(Term.NEE);
                        i += 2;
                    } else {
                        tokens.add(Term.NE);
                        i++;
                    }
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
            } else if (c == '^') {
                tokens.add(Term.CARET);
            } else if (c == '=' && i + 1 < len && in.codePointAt(i + 1) == '=') {
                if (i + 2 < len && in.codePointAt(i + 2) == '=') {
                    tokens.add(Term.EEQ);
                    i += 2;
                } else {
                    tokens.add(Term.EQ);
                    i++;
                }
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
                    throw error(in, i, "EOF");
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
                    } else if (NUMBER_DELIMITERS.indexOf(c) >= 0) {
                        break;
                    } else if (c < '0' || c > '9') {
                        throw error(in, i, "bad number");
                    }
                }
                if (integer) {
                    tokens.add(Term.newInteger(Integer.parseInt(in.substring(start, i))));
                } else {
                    tokens.add(Term.newReal(Double.parseDouble(in.substring(start, i))));
                }
                i--;
            } else if (c == '\\' || Character.isAlphabetic(c) || c == '@') {
                StringBuilder sb = new StringBuilder();
                for (;i<len;i++) {
                    c = in.codePointAt(i);
                    if (c == '\\' && i + 1 < len) {
                        c = in.codePointAt(++i);
                    } else if (c != '*' && PATH_DELIMITERS.indexOf(c) >= 0) {
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
                throw error(in, i, "invalid character " + (c > 0x20 && c < 0x7f ? "\"" + ((char)c) + "\"" : "U+" + Integer.toHexString(c)));
            }
        }
        return tokens;
    }

    private static IllegalArgumentException error(String s, int index, String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tokenization error at " + index + "/" + s.length() + ": " + msg + " in \"");
        for (int i=0;i<s.length();i++) {
            int cp = s.codePointAt(i);
            String t;
            if (cp == '\n') {
                t = "\\n";
            } else if (cp == '\r') {
                t = "\\r";
            } else if (cp == '\t') {
                t = "\\t";
            } else if (cp == '\\') {
                t = "\\\\";
            } else if (Character.isLetterOrDigit(cp) || (cp >= 0x20 && cp <= 0x7f)) {
                t = Character.toString(cp);
            } else {
                t = "\\u" + Integer.toHexString(cp);
            }
            if (i == index) {
                for (int j=0;j<t.length();) {
                    cp = t.codePointAt(j);
                    sb.appendCodePoint(cp);
                    sb.append("\u0332");
                    j += cp < 0x10000 ? 2 : 1;
                }
            } else {
                sb.append(t);
            }
        }
        sb.append('"');
        return new IllegalArgumentException(sb.toString());
    }

}
