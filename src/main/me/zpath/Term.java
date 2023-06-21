package me.zpath;

import java.util.*;

/**
 * A term is a basic unit of the parsed ZPath, which may be a token like "<code>+</code>",
 * a string or number constant, a path like "<code>parent/child</code>", a function, or
 * an expression like "<code>(name=="test" || age+3 &lt; 5)</code>"
 */
public class Term implements Axis {

    /** The whitespace Token */
    public static final Term WS = new Term(" ");
    /** The "." Token */
    public static final Term DOT = new Term(".");
    /** The ".." Token */
    public static final Term DOTDOT = new Term("..");

    /** The "[" Token */
    public static final Term LBRACE = new Term("[");
    /** The "]" Token */
    public static final Term RBRACE = new Term("]");
    /** The "(" Token */
    public static final Term LPAREN = new Term("(");
    /** The ")" Token */
    public static final Term RPAREN = new Term(")");

    // https://en.cppreference.com/w/c/language/operator_precedence

    /** The "!" Token */
    public static final Term BANG = new Term("!", 2);
    /** The "*" Token */
    public static final Term STAR = new Term("*", 3);
    /** The "**" Token */
    public static final Term STARSTAR = new Term("**");
    /** The "/" Token */
    public static final Term SLASH = new Term("/", 3);
    /** The "%" Token */
    public static final Term PERCENT = new Term("%", 3);
    /** The "+" Token */
    public static final Term PLUS = new Term("+", 4);
    /** The "-" Token */
    public static final Term MINUS = new Term("-", 4);
    /** The "&gt;" Token */
    public static final Term GT = new Term(">", 6);
    /** The "&lt;" Token */
    public static final Term LT = new Term("<", 6);
    /** The "&gt;=" Token */
    public static final Term GE = new Term(">=", 6);
    /** The "&lt;=" Token */
    public static final Term LE = new Term("<=", 6);
    /** The "==" Token */
    public static final Term EQ = new Term("==", 7);
    /** The "!=" Token */
    public static final Term NE = new Term("!=", 7);
    /** The "&amp;" Token */
    public static final Term BITAND = new Term("&", 8);
    /** The "^" Token */
    public static final Term BITXOR = new Term("^", 9);
    /** The "|" Token */
    public static final Term BITOR = new Term("|", 10);
    /** The "&amp;&amp;" Token */
    public static final Term AND = new Term("&&", 11);
    /** The "||;" Token */
    public static final Term OR = new Term("||", 12);
    /** The "?" Token */
    public static final Term QUESTION = new Term("?", 13);
    /** The ":" Token */
    public static final Term COLON = new Term(":", 13);
    /** The "," Token */
    public static final Term COMMA = new Term(",", 15);

    private static final int OTHER = 0, DELIM = 1, STRING = 2, INTEGER = 3, REAL = 4, NAME = 5, INDEX = 6, FUNCTION = 7;
    private static final int NOSCORE = -1;

    private final int type;
    final String value;
    private final Number nvalue;
    private final int score;

    private Term(int type, String value, Number nvalue, int score) {
        this.type = type;
        this.value = value;
        this.nvalue = nvalue;
        this.score = score;
    }

    protected Term() {
        this(OTHER, null, null, NOSCORE);
    }

    private Term(String s) {
        this(DELIM, s, null, NOSCORE);
    }

    private Term(String s, int score) {
        this(DELIM, s, null, score);
    }

    int compareTo(Term other) {
        return score - other.score;
    }

    boolean isUnaryOperator() {
        return score > 0 && score <= 2;
    }

    boolean isBinaryOperator() {
        return score > 2;
    }

    boolean isOperator() {
        return isUnaryOperator() || isBinaryOperator();
    }

    static Term newString(String value) {
        return new Term(STRING, value, null, NOSCORE);
    }

    static Term newFunction(String value) {
        return new Term(FUNCTION, value, null, NOSCORE);
    }

    static Term newName(String value) {
        return new Term(NAME, value, null, NOSCORE);
    }

    static Term newInteger(int value) {
        return new Term(INTEGER, Integer.toString(value), value, NOSCORE);
    }

    static Term newReal(double value) {
        return new Term(REAL, Double.toString(value), value, NOSCORE);
    }

    static Term newIndex(int value) {
        return new Term(INDEX, Integer.toString(value), value, NOSCORE);
    }

    public String toString() {
        if (isFunction()) {
            return value + "(";
        } else if (isIndex()) {
            return "#" + value;
        } else if (isString()) {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            for (int i=0;i<value.length();i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '"': sb.append("\\\""); break;
                    default: sb.append(c);
                }
            }
            sb.append('"');
            return sb.toString();
        } else {
            return value;
        }
    }

    /**
     * Return true if this Term is a known delimiter
     * @return true if it's a delimiter 
     */
    public boolean isDelim() {
        return type == DELIM;
    }

    /**
     * Return true if this Term is a string
     * @return true if it's a string
     */
    public boolean isString() {
        return type == STRING;
    }

    /**
     * Return true if this Term is an integer
     * @return true if it's an integer
     */
    public boolean isInteger() {
        return type == INTEGER;
    }

    /**
     * Return true if this Term is an real number
     * @return true if it's a real
     */
    public boolean isReal() {
        return type == REAL;
    }

    /**
     * Return true if this Term is an index
     * @return true if it's an index
     */
    public boolean isIndex() {
        return type == INDEX;
    }

    /**
     * Return true if this Term is an integer or a real number 
     * @return true if it's a number
     */
    public boolean isNumber() {
        return isInteger() || isReal();
    }

    /** 
     * Return true if this Term is a boolean
     * @return true if it's a boolean
     */
    public boolean isBoolean() {
        return false;
    }

    /**
     * Return true if this Term is a name (an unquoted string) 
     * @return true if it's a name
     */
    public boolean isName() {
        return type == NAME;
    }

    /**
     * Return true if this Term is a function 
     * @return true if it's a function
     */
    public boolean isFunction() {
        return type == FUNCTION;
    }

    /**
     * Return true if this Term is a path 
     * @return true if it's a function
     */
    public boolean isPath() {
        return false;
    }

    /**
     * Return true if this Term is an expression 
     * @return true if it's a function
     */
    public boolean isExpr() {
        return false;
    }

    /**
     * If this term is a number, return the value as a double
     * @return the value of the term as a double
     * @throws IllegalStateException if its not a double
     */
    public double doubleValue() {
        if (isNumber()) {
            return nvalue.doubleValue();
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * If this term is a number, return the value as a integer
     * @return the value of the term as an integer
     * @throws IllegalStateException if its not a integer
     */
    public int intValue() {
        if (isNumber()) {
            return nvalue.intValue();
        } else {
            throw new IllegalStateException();
        }
    }

    int indexValue() {
        if (isIndex()) {
            return nvalue.intValue();
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * If this term is a string, return the value as a string
     * @return the value of the term as a string
     * @throws IllegalStateException if its not a string
     */
    String stringValue() {
        if (isString()) {
            return value;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Evaluate this Term.
     * @param in the set of Nodes that are the current context - will never be empty
     * @param out if this Term evaluates to one or more Nodes, they should be added to this collection
     * @param config the configuration
     */
    @Override public List<Node> eval(final List<Node> in, List<Node> out, Configuration config) {
        Node n = null;
        if (isString()) {
            n = Node.create(value);
        } else if (isInteger()) {
            n = Node.create(Integer.parseInt(value));
        } else if (isReal()) {
            n = Node.create(Double.parseDouble(value));
        }
        // Index types have been converted to paths already. They could get here if we allowed eg **[#1 == "x"], but we don't
        if (n != null) {
            out.add(n);
        }
        return out;
    }

}
