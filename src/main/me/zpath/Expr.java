package me.zpath;

import java.util.*;

class Calc extends Term {

    final Term lhs, rhs;
    final Term op;

    Calc(Term lhs, Term op, Term rhs) {
        this.lhs = lhs;
        this.op = op;
        this.rhs = rhs;
    }

    @Override public String toString() {
        return "(" + lhs + " " + op + " " + rhs + ")";
    }

    @Override public void dump(Configuration config) {
        config.debug("calc: " + this);
        config = config.debugIndent();
        lhs.dump(config);
        op.dump(config);
        if (rhs != null) {
            lhs.dump(config);
        }
    }

    @Override public boolean isCalc() {
        return true;
    }
    @Override public boolean isNumber() {
        return op == Term.PLUS || op == Term.MINUS || op == Term.STAR || op == Term.SLASH;
    }
    @Override public boolean isInteger() {
        return isNumber() && lhs.isInteger() && rhs.isInteger();
    }
    @Override public boolean isBoolean() {
        return op == Term.AND || op == Term.OR;
    }

    @Override public void eval(final Collection<Node> in, final Collection<Node> out, Configuration config) {
        for (Node node : in) {
            Node ln = null, rn = null;
            if (config.isDebug()) {
                config.debug(this + " test: eval lhs");
            }
            lhs.eval(Collections.<Node>singleton(node), out, config.debugIndent());
            if (!out.isEmpty()) {
                ln = out.iterator().next();
                out.clear();
            }
            if (rhs != null) {
                if (config.isDebug()) {
                    config.debug(this + " test: eval rhs");
                }
                rhs.eval(Collections.<Node>singleton(node), out, config.debugIndent());
                if (!out.isEmpty()) {
                    rn = out.iterator().next();
                    out.clear();
                }
            }
            if (config.isDebug()) {
                config.debug(this + " test: lhs=" + lhs);
                config.debug(this + " test: rhs=" + rhs);
            }

            Node result = null;
            if (op == Term.BANG) {
                boolean b = ln != null && ln.booleanValue();
                result = Node.create(!b);
            } else if (op == Term.PLUS) {
                if (ln != null && rn != null) {
                    double s = ln.doubleValue() + rhs.doubleValue();
                    if (s == s) {
                        result = Node.create(s);
                    }
                }
            } else if (op == Term.MINUS) {
                if (ln != null && rn != null) {
                    double s = lhs.doubleValue() - rhs.doubleValue();
                    if (s == s) {
                        result = Node.create(s);
                    }
                }
            } else if (op == Term.SLASH) {
                if (ln != null && rn != null) {
                    int ri;
                    if (lhs.isInteger() && rhs.isInteger() && (ri=rhs.intValue()) != 0) {
                        int s = lhs.intValue() / ri;
                        result = Node.create(s);
                    } else {
                        double s = lhs.doubleValue() / rhs.doubleValue();
                        if (s == s && !Double.isInfinite(s)) {
                            result = Node.create(s);
                        }
                    }
                }
            } else if (op == Term.GE || op == Term.GT || op == Term.LT || op == Term.LE) {
                if (ln != null && rn != null) {
                    double v = Double.NaN;
                    if (lhs.isNumber() && rhs.isNumber()) {
                        v = ln.doubleValue() - rn.doubleValue();
                    } else if (lhs.isString() && rhs.isString()) {
                        v = ln.stringValue().compareTo(rn.stringValue());
                    } else if (lhs.isBoolean() && rhs.isBoolean()) {
                        v = ln.booleanValue() == rn.booleanValue() ? 0 : Double.NaN;
                    }
                    if (v > 0) {
                        result = Node.create(op == Term.GE || op == Term.GT);
                    } else if (v < 0) {
                        result = Node.create(op == Term.LE || op == Term.LT);
                    } else if (v == 0) {
                        result = Node.create(op == Term.GE || op == Term.LE);
                    }
                }
            } else if (op == Term.EQ || op == Term.NE) {
                boolean eq;
                if (lhs.isNumber() || rhs.isNumber()) {
                    eq = ln != null && rn != null && ln.doubleValue() == rn.doubleValue();
                } else if (lhs.isString() || rhs.isString()) {
                    String s1 = ln == null ? null : ln.stringValue();
                    String s2 = rn == null ? null : rn.stringValue();
                    eq = s1 != null && s1.equals(s2);
                } else if (lhs.isBoolean() || rhs.isBoolean()) {
                    eq = ln != null && rn != null && ln.booleanValue() == rn.booleanValue();
                } else {
                    eq = ln != null && ln.equals(rn);
                }
                result = Node.create(op == Term.NE ? !eq : eq);
            } else if (op == Term.AND) {
                boolean eq = ln != null && rn != null && ln.booleanValue() && rn.booleanValue(); 
                result = Node.create(eq);
            } else if (op == Term.OR) {
                boolean eq = (ln != null && ln.booleanValue()) || (rn != null && rn.booleanValue()); 
                result = Node.create(eq);
            }
            if (result != null) {
                out.add(result);
            }
            if (config.isDebug()) {
                if (result == null) {
                    config.debug(this + " eval is null");
                } else {
                    config.debug(this + " eval = " + result);
                }
            }
        }
    }

}
