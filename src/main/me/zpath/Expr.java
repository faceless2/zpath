package me.zpath;

import java.util.*;

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
                logger.log(this + " eval " + name + ": value=" + node);
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
                } else if (op == Term.PLUS) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double s = doubleValue(context, ln) + doubleValue(context, rn);
                        if (s == s) {
                            if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                                result = Integer.valueOf((int)s);
                            } else {
                                result = Double.valueOf(s);
                            }
                        }
                    }
                } else if (op == Term.MINUS) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double s = doubleValue(context, ln) - doubleValue(context, rn);
                        if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                            result = Integer.valueOf((int)s);
                        } else {
                            result = Double.valueOf(s);
                        }
                    }
                } else if (op == Term.STAR) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double s = doubleValue(context, ln) * doubleValue(context, rn);
                        if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                            result = Integer.valueOf((int)s);
                        } else {
                            result = Double.valueOf(s);
                        }
                    }
                } else if (op == Term.PERCENT) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double s = doubleValue(context, rn);
                        if (s == s && s != 0) {
                            s = doubleValue(context, ln) % (int)s;
                            if (s == s) {
                                result = Double.valueOf(s);
                            }
                        }
                    }
                } else if (op == Term.SLASH) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        int ri;
                        if (lhs.isInteger() && rhs.isInteger() && (ri=(int)doubleValue(context, rn)) != 0 && ri == doubleValue(context, rn)) {
                            int s = (int)doubleValue(context, ln) / ri;
                            result = Integer.valueOf(s);
                        } else {
                            double s = doubleValue(context, ln) / doubleValue(context, rn);
                            if (s == s && !Double.isInfinite(s)) {
                                result = Double.valueOf(s);
                            }
                        }
                    }
                } else if (op == Term.BITAND) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double ld = doubleValue(context, ln);
                        double rd = doubleValue(context, rn);
                        if (ld == ld && rd == rd && ld == (int)ld && rd == (int)rd) {
                            result = Integer.valueOf((int)ld & (int)rd);
                        }
                    }
                } else if (op == Term.BITOR) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double ld = doubleValue(context, ln);
                        double rd = doubleValue(context, rn);
                        if (ld == ld && rd == rd && ld == (int)ld && rd == (int)rd) {
                            result = Integer.valueOf((int)ld | (int)rd);
                        }
                    }
                } else if (op == Term.CARET) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    if (ln != null && rn != null) {
                        double ld = doubleValue(context, ln);
                        double rd = doubleValue(context, rn);
                        if (ld == ld && rd == rd && ld == (int)ld && rd == (int)rd) {
                            result = Integer.valueOf((int)ld ^ (int)rd);
                        }
                    }
                } else if (op == Term.GE || op == Term.GT || op == Term.LT || op == Term.LE || op == Term.EQ || op == Term.NE) {
                    Object ln = evalTermAsObject("lhs", lhs, node, tmp, context);
                    Object rn = evalTermAsObject("rhs", rhs, node, tmp, context);
                    //
                    // {a:30, b:25} : a >= b, is that true? Different nodes, same value. What about a == b?
                    // XPath solves this by using "eq" and "==" to mean different things, but that's awful.
                    //
                    // We can't have two different nodes being equal just if they have the same value,
                    // in XML (eg) it would mean two text nodes with the same values are the same. So
                    // our rule is:
                    //  * if either side is a string/number/boolean, compare by value.
                    //  * otherwise test for equality only.
                    // 
                    if (ln != null && rn != null) {
                        double v = Double.NaN;
                        if (isPrimitive(ln) || isPrimitive(rn)) {
                            // value comparison
                            double ld = doubleValue(context, ln);
                            double rd = doubleValue(context, rn);
                            if (ld == ld && rd == rd) {
                                v = ld - rd;    // If both sides are numbers, compare as numbers
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
                                    v = booleanValue(context, ln) == booleanValue(context, rn) ? 0 : Double.NaN;
                                }
                            }
                        } else {
                            // instance comparison
                            v = (ln == null ? rn == null : ln.equals(rn)) ? 0 : Double.NaN;
                        }
                        if (v == v && Math.abs(v) <= context.getConfiguration().getMinDouble()) {     // Rounding error
                            v = 0;
                        }
                        if (v > 0) {
                            result = Boolean.valueOf(op == Term.GE || op == Term.GT || op == Term.NE);
                        } else if (v < 0) {
                            result = Boolean.valueOf(op == Term.LE || op == Term.LT || op == Term.NE);
                        } else if (v == 0) {
                            result = Boolean.valueOf(op == Term.GE || op == Term.LE || op == Term.EQ);
                        } else {
                            result = Boolean.valueOf(op == Term.NE);    // 3 < "string" == false
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
        String v;
        if (node == null) {
            v = null;
        } else if (node instanceof CharSequence) {
            v = node.toString();
        } else if (node instanceof Number) {
            double d = ((Number)node).doubleValue();
            if (d == (int)d) {
                v = Integer.toString((int)d);
            } else {
                v = node.toString();
            }
        } else if (node instanceof Boolean) {
            v = node.toString();
        } else {
            v = context.stringValue(node);
        }
        return v;
    }

    static double doubleValue(EvalContext context, Object node) {
        double v = Double.NaN;
        if (node instanceof Number) {
            v = ((Number)node).doubleValue();
        } else if (!(node instanceof CharSequence || node instanceof Boolean || node == null)) {
            v = context.doubleValue(node);
        }
        return v;
    }

    static boolean booleanValue(EvalContext context, Object node) {
        if (node == null) {
            return false;
        } else if (node instanceof Boolean) {
            return ((Boolean)node).booleanValue();
        } else if (node instanceof CharSequence || node instanceof Number) {
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
        return o == null || o instanceof Number || o instanceof String || o instanceof Boolean;
    }


}
