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
                logger.log(this + " eval " + name + ": value=" + node + " parentable=" + context.isParent(node));
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
                    boolean b = booleanValueRequired(context, ln);
                    if (b) {
                        result = evalTermAsObject("truevalue", rhs, node, tmp, context);
                    } else {
                        result = evalTermAsObject("falsevalue", rrhs, node, tmp, context);
                    }
                } else if (op == Term.BANG) {
                    Object ln = evalTermAsObject("op", lhs, node, tmp, context);
                    boolean b = booleanValueRequired(context, ln);
                    result = Boolean.valueOf(!b);
                } else if (op == Term.TILDE) {
                    Number ln = numberValue(context, evalTermAsObject("lhs", lhs, node, tmp, context));
                    if (ln != null) {
                        result = Integer.valueOf(~ln.intValue());
                    }
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
                } else if (op == Term.GE || op == Term.GT || op == Term.LT || op == Term.LE || op == Term.EQ || op == Term.NE) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    double v = compare(ln, rn, context);
                    if (v > 0) {
                        result = Boolean.valueOf(op == Term.GE || op == Term.GT || op == Term.NE); 
                    } else if (v < 0) {
                        result = Boolean.valueOf(op == Term.LE || op == Term.LT || op == Term.NE);
                    } else if (v == 0) {
                        result = Boolean.valueOf(op == Term.GE || op == Term.LE || op == Term.EQ);
                    } else {
                        result = Boolean.valueOf(op == Term.NE);
                    }
                } else if (op == Term.AND) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    boolean eq = ln != null && rn != null && booleanValueRequired(context, ln) && booleanValueRequired(context, rn); 
                    result = Boolean.valueOf(eq);
                } else if (op == Term.OR) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    boolean eq = ln != null && booleanValueRequired(context, ln);
                    if (!eq) {
                        Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                        eq = rn != null && booleanValueRequired(context, rn);
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

    static String stringValue0(EvalContext context, List<Object> nodes) {
        return nodes.size() == 1 ? stringValue(context, nodes.get(0)) : null;
    }
    static Number numberValue0(EvalContext context, List<Object> nodes) {
        return nodes.size() == 1 ? numberValue(context, nodes.get(0)) : null;
    }
    static Boolean booleanValue0(EvalContext context, List<Object> nodes) {
        return nodes.size() == 1 ? booleanValue(context, nodes.get(0)) : null;
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

    static Boolean booleanValue(EvalContext context, Object node) {
        if (node instanceof Boolean) {
            return (Boolean)node;
        } else if (isPrimitive(node)) {
            return null;
        }
        return context.booleanValue(node);
    }

    static boolean booleanValueRequired(EvalContext context, Object node) {
        Boolean b = booleanValue(context, node);
        if (b != null) {
            return b.booleanValue();
        } else {
            return node != null && node != EvalContext.NULL && context.value(node) != null;
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

    // Return 0=equal, >0 = a>b, <0 = a<b, NaN a!=b
    @SuppressWarnings("unchecked")
    static double compare(Object a, Object b, EvalContext context) {
        if (a == null && b == null) {
            return Double.NaN;
        } else if (a == null || b == null) {
            return Double.NaN;
        } else if (a == b) {
            return 0;
        } else {
            // Both items are primitives, either in tree or literals
            // Must check numbers first, as we can't use equals() if we want to handle rounding error
            Number na = numberValue(context, a);
            Number nb = numberValue(context, b);
            if (na != null && nb != null) {
                return compare(na, nb, context);
            }
            String sa = stringValue(context, a);
            String sb = stringValue(context, b);
            if (sa != null && sb != null) {
                return sa.compareTo(sb);
            }
            Boolean ba = booleanValue(context, a);
            Boolean bb = booleanValue(context, b);
            if (ba != null && bb != null) {
                return ba.equals(bb) ? 0 : Double.NaN;
            }
            if (a.getClass() == b.getClass()) {
                if (a instanceof Comparable) {
                    return ((Comparable)a).compareTo(b);
                } else if (a.equals(b)) {
                    return 0;
                } else {
                    return Double.NaN;
                }
            }
            return a.equals(b) ? 0 : Double.NaN;
        }
    }

    static int compare(Number a, Number b, EvalContext context) {
        // FFS, Number is not Comparable...
        int v;
        if (a instanceof BigDecimal || b instanceof BigDecimal || (a instanceof BigInteger && (b instanceof Double || b instanceof Float)) || (b instanceof BigInteger && (a instanceof Double || a instanceof Float))) {
            a = a instanceof BigDecimal ? a : new BigDecimal(a.toString());
            b = b instanceof BigDecimal ? b : new BigDecimal(b.toString());
            v = ((BigDecimal)a).compareTo((BigDecimal)b);
        } else if (b instanceof Double || b instanceof Float || a instanceof Double || a instanceof Float) {
            double d = a.doubleValue() - b.doubleValue();
            if (Math.abs(d) < context.getConfiguration().getMinDouble()) {
                d = 0;
            }
            v = d < 0 ? -1 : d > 0 ? 1 : 0;
        } else if (b instanceof BigInteger || b instanceof BigInteger) {
            a = a instanceof BigInteger ? a : new BigInteger(a.toString());
            b = b instanceof BigInteger ? b : new BigInteger(b.toString());
            v = ((BigInteger)a).compareTo((BigInteger)b);
        } else {
            v = Long.compare(a.longValue(), b.longValue());
        }
        return v;
    }

    // the rules
    //
    // [a]              true if a exists at all, even if its null
    // [a == true]      true if a exists and the value of a == true
    // [!!a]            true if a exists and is not false or null
    // [a == b]         true if a == b or if a and b are both boolean

}
