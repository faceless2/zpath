package me.zpath;

import java.util.*;
import java.math.*;

class Expr extends Term {

    private final Term lhs, rhs, rrhs;
    private final Term op;

    Expr(Term op, Term lhs, Term rhs) {
        this(op, lhs, rhs, null);
    }

    Expr(Term op, Term lhs, Term rhs, Term rrhs) {
        this.lhs = lhs;
        this.op = op;
        this.rhs = rhs;
        this.rrhs = rrhs;
    }

    @Override public String toString() {
        if (rhs == null) {
            return "expr(" + op + " " + lhs + ")";
        } else if (rrhs != null) {
            return "expr(" + lhs + " " + op + " " + rhs + " : " + rrhs + ")";
        } else {
            return "expr(" + lhs + " " + op + " " + rhs + ")";
        }
    }

    @Override public void log(Configuration.Logger logger) {
        super.log(logger);
        logger.enter();
        lhs.log(logger);
        if (rhs != null) {
            rhs.log(logger);
        }
        if (rrhs != null) {
            rrhs.log(logger);
        }
        logger.exit();
    }

    @Override public boolean isExpr() {
        return true;
    }
    @Override public boolean isBoolean() {
        return op == Term.AND || op == Term.OR;
    }
    @Override public boolean isNumber() {
        return op == Term.PLUS || op == Term.MINUS || op == Term.STAR || op == Term.SLASH;
    }
    @Override public boolean isInteger() {
        return isNumber() && lhs.isInteger() && rhs.isInteger();
    }

    private Object evalTermAsObject(final String name, final Term term, Object node, final List<Object> out, final EvalContext context) {
        final Configuration.Logger logger = context.getLogger();
        try {
            if (logger != null) {
                logger.log(this + " eval " + name + " term=" + term);
                logger.enter();
            }
            term.eval(Collections.<Object>singletonList(node), out, context);
            if (!out.isEmpty()) {
                node = out.iterator().next();
                out.clear();
            } else {
                node = null;
            }
            if (logger != null) {
                logger.log(this + " eval " + name + ": value=" + node + " primitive=" + isPrimitive(node));
            }
        } finally {
            if (logger != null) {
                logger.exit();
            }
        }
        return node;
    }

    @Override public List<Object> eval(final List<Object> in, final List<Object> out, final EvalContext context) {
        final Configuration.Logger logger = context.getLogger();
        // This particular type of axis can only ever be evaluated on a single node.
        // a/b[...] - evaluated once for each b, separately
        // a/b/eval(...) - evaluated once for each b, separately
        // syntax doesn't allow multiple nodes as input

        assert in.size() == 1;
        for (Object node : in) {
            List<Object> tmp = out.subList(out.size(), out.size());     // But, just in case, do this.
            try {
                if (logger != null) {
                    logger.log(this + " eval " + node);
                    logger.enter();
                }

                Object result = null;
                if (op == Term.QUESTION) {
                    Object ln = evalTermAsObject("test", lhs, node, tmp, context);
                    boolean b = booleanValue(context, ln);
                    if (b) {
                        result = evalTermAsObject("truevalue", rhs, node, tmp, context);
                    } else {
                        result = evalTermAsObject("falsevalue", rrhs, node, tmp, context);
                    }
                } else if (op == Term.BANG) {
                    Object ln = evalTermAsObject("op", lhs, node, tmp, context);
                    boolean b = booleanValue(context, ln);
                    result = Boolean.valueOf(!b);
                } else if (op == Term.PLUS || op == Term.MINUS || op == Term.STAR || op == Term.SLASH || op == Term.PERCENT) {
                    Number ln = numberValue(context, evalTermAsObject("lhs", lhs, node, tmp, context));
                    Number rn = numberValue(context, evalTermAsObject("rhs", rhs, node, tmp, context));
                    if (ln != null && rn != null) {
                        if (ln instanceof BigDecimal || rn instanceof BigDecimal || ln instanceof Double || rn instanceof Double || ln instanceof Float || rn instanceof Float || (op == Term.SLASH && (!lhs.isNumber() || !rhs.isNumber()))) {
                            // Floating point required if either side is floating point, OR if either
                            // side is a non-constant expression and we're dividing.

                            if (ln instanceof BigDecimal || rn instanceof BigDecimal || ln instanceof BigInteger || rn instanceof BigInteger) {
                                // Ugh, no BigDecimal.valueOf(Number). This is going to be super rare, forget optimising
                                BigDecimal bln = ln instanceof BigDecimal ? (BigDecimal)ln : new BigDecimal(ln.toString());
                                BigDecimal brn = rn instanceof BigDecimal ? (BigDecimal)rn : new BigDecimal(rn.toString());
                                if (op == Term.PLUS) {
                                    result = bln.add(brn);
                                } else if (op == Term.MINUS) {
                                    result = bln.subtract(brn);
                                } else if (op == Term.STAR) {
                                    result = bln.multiply(brn);
                                } else if (op == Term.SLASH) {
                                    result = bln.divide(brn);
                                } else {
                                    result = bln.remainder(brn);
                                }
                                result = bln;
                            } else if (op == Term.PLUS) {
                                result = ln.doubleValue() + rn.doubleValue();
                            } else if (op == Term.MINUS) {
                                result = ln.doubleValue() - rn.doubleValue();
                            } else if (op == Term.STAR) {
                                result = ln.doubleValue() * rn.doubleValue();
                            } else if (op == Term.SLASH) {
                                result = ln.doubleValue() / rn.doubleValue();
                            } else {
                                result = ln.doubleValue() % rn.doubleValue();
                            }
                        } else if (ln instanceof BigInteger || rn instanceof BigInteger) {
                            // Other value is at worse a long
                            BigInteger bln = ln instanceof BigInteger ? (BigInteger)ln : BigInteger.valueOf(ln.longValue());
                            BigInteger brn = rn instanceof BigInteger ? (BigInteger)rn : BigInteger.valueOf(rn.longValue());
                            if (op == Term.PLUS) {
                                result = bln.add(brn);
                            } else if (op == Term.MINUS) {
                                result = bln.subtract(brn);
                            } else if (op == Term.STAR) {
                                result = bln.multiply(brn); 
                            } else if (op == Term.SLASH) {
                                result = bln.divide(brn);
                            } else {
                                result = bln.remainder(brn);
                            }
                        } else {
                            long l;
                            if (op == Term.PLUS) {
                                l = ln.longValue() + rn.longValue();
                            } else if (op == Term.MINUS) {
                                l = ln.longValue() - rn.longValue();
                            } else if (op == Term.STAR) {
                                l = ln.longValue() * rn.longValue();
                            } else if (op == Term.SLASH) {
                                l = ln.longValue() / rn.longValue();
                            } else {
                                l = ln.longValue() % rn.longValue();
                            }
                            if (l == (int)l) {
                                result = Integer.valueOf((int)l);
                            } else {
                                result = Long.valueOf(l);
                            }
                        }
                    }
                } else if (op == Term.BITAND || op == Term.BITOR || op == Term.CARET) {
                    Number ln = numberValue(context, evalTermAsObject("lhs", lhs, node, tmp, context));
                    Number rn = numberValue(context, evalTermAsObject("rhs", rhs, node, tmp, context));
                    if (ln != null && rn != null) {
                        int li = ln.intValue();
                        int ri = rn.intValue();
                        if (op == Term.BITAND) {
                            result = Integer.valueOf(li & ri);
                        } else if (op == Term.BITOR) {
                            result = Integer.valueOf(li | ri);
                        } else {
                            result = Integer.valueOf(li ^ ri);
                        }
                    }
                } else if (op == Term.GE || op == Term.GT || op == Term.LT || op == Term.LE || op == Term.EQ || op == Term.NE || op == Term.EEQ || op  == Term.NEE) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    //
                    // {a:30, b:25} : a >= b, is that true? Different nodes, same value. What about a == b?
                    // XPath solves this by using "eq" and "==" to mean different things, but that's awful.
                    // Ultimately we can't ignore this and can't pick a correct option, so use "==" for ".equals()"
                    // and "===" for identity-equality. Meaning depends on the underlying model,
                    // for Json deserializers that use Collections, they'll be the same.
                    //
                    // We can't have two different nodes being equal just if they have the same value,
                    // in XML (eg) it would mean two text nodes with the same values are the same. So
                    // our rule is:
                    //  * if either side is a string/number/boolean, compare by value.
                    //  * otherwise test for equality only.
                    // 
                    boolean strict = op == Term.EEQ || op == Term.NEE;  // if set we must compare on identity equals
                    if (ln != null && rn != null) {
                        double v = Double.NaN;
                        if (strict ? isPrimitive(ln) && isPrimitive(rn) : isPrimitive(ln) || isPrimitive(rn)) {
                            // value comparison
                            Number ld = numberValue(context, ln);
                            Number rd = numberValue(context, rn);
                            if (ld != null && rd != null) {     // Both numbers
                                // FFS, Number is not Comparable...
                                if (ld instanceof BigDecimal || rd instanceof BigDecimal || (ld instanceof BigInteger && (rd instanceof Double || rd instanceof Float)) || (rd instanceof BigInteger && (ld instanceof Double || ld instanceof Float))) {
                                    ld = ld instanceof BigDecimal ? ld : new BigDecimal(ld.toString());
                                    rd = rd instanceof BigDecimal ? rd : new BigDecimal(rd.toString());
                                    v = ((BigDecimal)ld).compareTo((BigDecimal)rd);
                                } else if (rd instanceof Double || rd instanceof Float || ld instanceof Double || ld instanceof Float) {
                                    double d = ld.doubleValue() - rd.doubleValue();
                                    if (Math.abs(d) < context.getConfiguration().getMinDouble()) {
                                        d = 0;
                                    }
                                    v = d < 0 ? -1 : d > 0 ? 1 : 0;
                                } else if (rd instanceof BigInteger || rd instanceof BigInteger) {
                                    ld = ld instanceof BigInteger ? ld : new BigInteger(ld.toString());
                                    rd = rd instanceof BigInteger ? rd : new BigInteger(rd.toString());
                                    v = ((BigInteger)ld).compareTo((BigInteger)rd);
                                } else {
                                    v = Long.compare(ld.longValue(), rd.longValue());
                                }
                            } else {
                                String ls = stringValue(context, ln);
                                String rs = stringValue(context, rn);
                                if (ls != null && rs != null) { // If both sides are strings, compare as strings
                                    v = ls.compareTo(rs);
                                } else {
                                    // Careful now.
                                    // We don't have a boolean constant natively, but we can generate one
                                    // * (1 == 1) == (1 > 0)            true, both sides are native booleans
                                    // * (1 == 1) == "test"             false, only one side is a boolean
                                    // * (1 == 1) == a                  true regardless of the value of "a", because a exists: same as "!!a" or "a"
                                    // For the latter it would have to be (1 == 1) == value(a)
                                    // * a == 1                         false, although we're collapsing a to true and 1 to true.
                                    // So at least one side has to be an actual boolean here otherwise we fail.
                                    if (ln instanceof Boolean || rn instanceof Boolean) {
                                        v = booleanValue(context, ln) == booleanValue(context, rn) ? 0 : Double.NaN;
                                    } else {
                                        v = Double.NaN;
                                    }
                                }
                            }
                        } else if (strict) {
                            // strict instance comparison
                            v = (ln == null ? rn == null : ln == rn) ? 0 : Double.NaN;
                        } else {
                            // non-strict instance comparison
                            v = (ln == null ? rn == null : ln.equals(rn)) ? 0 : Double.NaN;
                        }
                        if (v == v && Math.abs(v) <= context.getConfiguration().getMinDouble()) {     // Rounding error
                            v = 0;
                        }
                        if (v > 0) {
                            result = Boolean.valueOf(op == Term.GE || op == Term.GT || op == Term.NE || op == Term.NEE);
                        } else if (v < 0) {
                            result = Boolean.valueOf(op == Term.LE || op == Term.LT || op == Term.NE || op == Term.NEE);
                        } else if (v == 0) {
                            result = Boolean.valueOf(op == Term.GE || op == Term.LE || op == Term.EQ || op == Term.EEQ);
                        } else {
                            result = Boolean.valueOf(op == Term.NE || op == Term.NEE);    // 3 < "string" == false
                        }
                    } else if (ln == null && rn == null) {
                        // Are two missing nodes equal to eachother? No
                        result = op == Term.NE;
                    } else {
                        // Missing is never equal to not-missing
                        result = op == Term.NE;
                    }
                } else if (op == Term.AND) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    boolean eq = ln != null && rn != null && booleanValue(context, ln) && booleanValue(context, rn); 
                    result = Boolean.valueOf(eq);
                } else if (op == Term.OR) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    boolean eq = ln != null && booleanValue(context, ln);
                    if (!eq) {
                        Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                        eq = rn != null && booleanValue(context, rn);
                    }
                    result = Boolean.valueOf(eq);
                }
                if (result != null) {
                    out.add(result);
                }
                if (logger != null) {
                    if (result == null) {
                        logger.log(this + " eval is null");
                    } else {
                        logger.log(this + " eval = " + result);
                    }
                }
            } finally {
                if (logger != null) {
                    logger.exit();
                }
            }
        }
        return out;
    }

    static String stringValue(EvalContext context, Object node) {
        if (node instanceof CharSequence) {
            return node.toString();
        } else if (isPrimitive(node)) {
            return null;
        }
        return context.stringValue(node);
    }

    static Number numberValue(EvalContext context, Object node) {
        if (node instanceof Number) {
            return (Number)node;
        } else if (isPrimitive(node)) {
            return null;
        }
        return context.numberValue(node);
    }

    static boolean booleanValue(EvalContext context, Object node) {
        if (node == null) {
            return false;
        } else if (node instanceof Boolean) {
            return ((Boolean)node).booleanValue();
        } else if (isPrimitive(node)) {
            return true;
        } else {
            return context.booleanValue(node);
        }
    }

    static StringBuilder encodeXML(String s, boolean attribute, StringBuilder sb) {
        if (sb == null) {
            sb = new StringBuilder();
        }
        int len = s.length();
        for (int i=0;i<len;) {
            final int c = s.codePointAt(i);
            if (c < 0x80) {      // ASCII range: test most common case first
                if (c < 0x20 && (c != '\t' && c != '\r' && c != '\n')) {
                    // Illegal XML character, even encoded. Skip or substitute
                    sb.append("&#xfffd;");   // Unicode replacement character
                } else {
                    switch(c) {
                      case '&':  sb.append("&amp;"); break;
                      case '>':  sb.append("&gt;"); break;
                      case '<':  sb.append("&lt;"); break;
                      case '\'':  if (attribute) { sb.append("&apos;"); break; }
                      case '\"':  if (attribute) { sb.append("&quot;"); break; }
                      default:   sb.append((char)c);
                    }
                }
            } else if ((c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff) {
                // Illegal XML character, even encoded. Skip or substitute
                sb.append("&#xfffd;");   // Unicode replacement character
            } else {
                sb.append("&#x");
                sb.append(Integer.toHexString(c));
                sb.append(';');
            }
            i += c < 0x10000 ? 1 : 2;
        }
        return sb;
    }

    static boolean isPrimitive(Object o) {
        return o == null || o instanceof Number || o instanceof CharSequence || o instanceof Boolean;
    }


}
