package me.zpath;

import java.util.*;

class Expr extends Term {

    private static final double MINDOUBLE = 0.0000001;
    final Term lhs, rhs, rrhs;
    final Term op;

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
            return "(" + op + " " + lhs + ")";
        } else if (rrhs != null) {
            return "(" + lhs + " " + op + " " + rhs + " : " + rrhs + ")";
        } else {
            return "(" + lhs + " " + op + " " + rhs + ")";
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

    private Node evalTermAsNode(final String name, final Term term, Node node, final List<Node> out, final Configuration config) {
        final Configuration.Logger logger = config.getLogger();
        try {
            if (logger != null) {
                logger.log(this + " eval " + name + " term=" + term);
                logger.enter();
            }
            term.eval(Collections.<Node>singletonList(node), out, config);
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

    @Override public List<Node> eval(final List<Node> in, final List<Node> out, final Configuration config) {
        final Configuration.Logger logger = config.getLogger();
        for (Node node : in) {
            try {
                if (logger != null) {
                    logger.log(this + " eval " + node);
                    logger.enter();
                }

                Node result = null;
                if (op == Term.QUESTION) {
                    Node ln = evalTermAsNode("test", lhs, node, out, config);
                    boolean b = ln != null && ln.booleanValue();
                    if (b) {
                        result = evalTermAsNode("truevalue", rhs, node, out, config);
                    } else {
                        result = evalTermAsNode("falsevalue", rrhs, node, out, config);
                    }
                } else if (op == Term.BANG) {
                    Node ln = evalTermAsNode("op", lhs, node, out, config);
                    boolean b = ln != null && ln.booleanValue();
                    result = Node.create(!b);
                } else if (op == Term.PLUS) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        double s = ln.doubleValue() + rn.doubleValue();
                        if (s == s) {
                            if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                                result = Node.create((int)s);
                            } else {
                                result = Node.create(s);
                            }
                        }
                    }
                } else if (op == Term.MINUS) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        double s = ln.doubleValue() - rn.doubleValue();
                        if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                            result = Node.create((int)s);
                        } else {
                            result = Node.create(s);
                        }
                    }
                } else if (op == Term.STAR) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        double s = ln.doubleValue() * rn.doubleValue();
                        if (lhs.isInteger() && rhs.isInteger() && s == (int)s) {
                            result = Node.create((int)s);
                        } else {
                            result = Node.create(s);
                        }
                    }
                } else if (op == Term.PERCENT) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        double s = rn.doubleValue();
                        if (s == s && s != 0) {
                            s = ln.doubleValue() % (int)s;
                            if (s == s) {
                                result = Node.create(s);
                            }
                        }
                    }
                } else if (op == Term.SLASH) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        int ri;
                        if (lhs.isInteger() && rhs.isInteger() && (ri=(int)rn.doubleValue()) != 0 && ri == rn.doubleValue()) {
                            int s = (int)ln.doubleValue() / ri;
                            result = Node.create(s);
                        } else {
                            double s = ln.doubleValue() / rn.doubleValue();
                            if (s == s && !Double.isInfinite(s)) {
                                result = Node.create(s);
                            }
                        }
                    }
                } else if (op == Term.GE || op == Term.GT || op == Term.LT || op == Term.LE) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    if (ln != null && rn != null) {
                        double v = Double.NaN;
                        if (lhs.isNumber() && rhs.isNumber()) {
                            v = ln.doubleValue() - rn.doubleValue();
                        } else if (lhs.isString() && rhs.isString()) {
                            v = ln.stringValue().compareTo(rn.stringValue());
                        } else if (lhs.isBoolean() && rhs.isBoolean()) {
                            v = ln.booleanValue() == rn.booleanValue() ? 0 : Double.NaN;
                        }
                        if (Math.abs(v) <= config.getMinDouble()) {     // Rounding error
                            v = 0;
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
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    boolean eq;
                    if (lhs.isNumber() || rhs.isNumber()) {
                        eq = ln != null && rn != null && Math.abs(ln.doubleValue() - rn.doubleValue()) <= config.getMinDouble();
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
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                    boolean eq = ln != null && rn != null && ln.booleanValue() && rn.booleanValue(); 
                    result = Node.create(eq);
                } else if (op == Term.OR) {
                    Node ln = evalTermAsNode("lhs", lhs, node, out, config);
                    boolean eq = ln != null && ln.booleanValue();
                    if (!eq) {
                        Node rn = evalTermAsNode("rhs", rhs, node, out, config);
                        eq = rn != null && rn.booleanValue();
                    }
                    result = Node.create(eq);
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

}
