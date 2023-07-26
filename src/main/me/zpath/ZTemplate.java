package me.zpath;

import java.io.*;
import java.nio.*;
import java.nio.file.Path;
import java.nio.file.*;
import java.net.*;
import java.util.*;

/**
 * <p>
 * A ZTemplate is a template system based around ZPath. Text is read in, ZPath expressions
 * in the text are evaluated against a model and substituted into the termplate.
 * See <a href="https://zpath.me/#ztemplate">https://zpath.me/#ztemplate</a> for the
 * specification
 * </p>
 * <p>
 * A ZTemplate object be compiled once and used simulteneously in multiple threads.
 * </p>
 */
public class ZTemplate {

    private final TemplateNode root;
    private final Configuration config;

    private ZTemplate(TemplateNode root, Configuration config) {
        this.root = root;
        this.config = config;
    }

    /**
     * Compile the ZTemplate
     * @param file the File to read the UTF-8 encoded Template from
     * @param config the Configuration to use for the embedded ZPath expressions
     * @throws IllegalArgumentException if the ZPath expressions in the template cannot be parsed
     * @throws IOException if the underlying stream throws an IOException
     * @return a ZTemplate
     */
    public static ZTemplate compile(File file, Configuration config) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Path is null");
        }
        URI uri = file.toURI();
        return compile(Files.newBufferedReader(file.toPath()), config, uri, 0);
    }

    /**
     * Compile the ZTemplate
     * @param reader the reader to read from
     * @param config the Configuration to use for the embedded ZPath expressions
     * @throws IllegalArgumentException if the ZPath expressions in the template cannot be parsed
     * @throws IOException if the underlying stream throws an IOException
     * @return a ZTemplate
     */
    public static ZTemplate compile(Reader reader, Configuration config) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("Reader is null");
        }
        URI uri = URI.create(".");
        return compile(reader, config, uri, 0);
    }

    /**
     * Return a copy of this ZTemplate that will override the Locale from the Configuration
     * with the supplied value. If the supplied Locale is the same as the current locale, return this
     * @param locale the locale
     * @return a copy of this ZTemplate with the overriden locale, or this ZTemplate if the locale is unchanged
     */
    public ZTemplate withLocale(Locale locale) {
        if (locale == null) {
            throw new IllegalArgumentException("Locale is null");
        }
        if (locale.equals(config.getLocale())) {
            return this;
        }
        Configuration conf = new Configuration(config).setLocale(locale);
        return new ZTemplate(root.withConfiguration(conf), conf);
    }

    private static ZTemplate compile(Reader reader, Configuration config, URI uri, int depth) throws IOException {
        if (config == null) {
            config = new Configuration();
        }
        final String filepath = uri.equals(URI.create(".")) ? "" : uri.toString() + " ";
        LineReader in = new LineReader(reader);
        int c;
        StringBuilder sb = new StringBuilder();       // only to give us direct access to the underlying chars
        TemplateNode cursor = new TemplateNode((String)null, 0, 0);
        int lastline = 0, lastcol = 0;

        while ((c=in.read()) >= 0) {
            if (c == '{') {
                c = in.read();
                if (c == '{') {
                    int tmpline = in.line;
                    int tmpcolumn = in.column - 2;;
                    if (sb.length() > 0) {
                        cursor.add(new TemplateNode(sb.toString(), lastline, lastcol));
                        sb.setLength(0);
                    }
                    int quote = 0;
                    do {
                        c = in.read();
                        if (c < 0) {
                            throw new EOFException();
                        } else if (c == '\\') {
                            c = in.read();
                            if (c >= 0) {
                                sb.append('\\');
                                sb.append((char)c);
                            } else {
                                throw new EOFException();
                            }
                        } else if (quote == 0 && (c == '"' || c == '\'')) {
                            quote = c;
                            sb.append((char)c);
                        } else if (quote != 0 && c == quote) {
                            quote = 0;
                            sb.append((char)c);
                        } else if (quote == 0 && c == '}') {
                            c = in.read();
                            if (c == '}') {
                                break;
                            } else {
                                sb.append('}');
                                sb.append((char)c);
                            }
                        } else {
                            sb.append((char)c);
                        }
                    } while (true);

                    int type = 0;       // 0 = substitution, 1 = open, 2 = close, 3 = partial
                    String expression;

                    if (sb.length() > 1 && sb.charAt(0) == '#') {
                        type = 1;
                        expression = sb.subSequence(1, sb.length()).toString().trim();
                    } else if (sb.length() > 1 && sb.charAt(0) == '/') {
                        expression = sb.subSequence(1, sb.length()).toString().trim();
                        type = 2;
                    } else if (sb.length() > 1 && sb.charAt(0) == '>') {
                        expression = sb.subSequence(1, sb.length()).toString().trim();
                        type = 3;
                    } else {
                        expression = sb.toString().trim();
                        type = 0;
                    }

                    if (type == 0 || type == 1) {
                        try {
                            ZPath expr = ZPath.compile(expression, config);
                            TemplateNode n = new TemplateNode(sb.toString(), expr, in.line, in.column);
                            cursor.add(n);
                            if (type == 1) {
                                cursor = n;
                            }
                            lastline = in.line;
                            lastcol = in.column;
                        } catch (RuntimeException e) {
                            throw new IllegalArgumentException("invalid ZPath {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn, e);
                        }
                    } else if (type == 2) {
                        if (cursor.expr == null) {
                            throw new IllegalArgumentException("mismatched close {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn);
                        } else {
                            boolean match = false;
                            try {
                                match = cursor.expr.equals(ZPath.compile(expression));
                            } catch (Exception e) {}
                            if (!match) {
                                throw new IllegalArgumentException("mismatched close {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn + ": doesn't match {{" + cursor.text + "}} from line " + cursor.line+":"+cursor.column);
                            } else {
                                cursor = cursor.parent;
                            }
                        }
                    } else if (type == 3) {
                        if (config.getTemplateIncluder() == null) {
                            throw new IllegalArgumentException("include unavailable for {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn);
                        } else if (depth == config.getTemplateMaxIncludeDepth()) {
                            throw new IllegalArgumentException("include nesting depth " + depth + " for {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn);
                        } else {
                            URI uri2 = uri.resolve(expression);
                            if (uri2 == null) {
                                throw new IllegalArgumentException("include unresolvable for {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn);
                            } else {
                                Reader reader2 = null;
                                try {
                                    try {
                                        reader2 = config.getTemplateIncluder().include(expression, uri2);
                                    } catch (Exception e) {
                                        throw new IllegalArgumentException("include failed for {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn, e);
                                    }
                                    if (reader2 == null) {
                                        throw new IllegalArgumentException("include failed for {{" + sb + "}} at " + filepath + "line " + tmpline + ":" + tmpcolumn);
                                    }
                                    ZTemplate sub = ZTemplate.compile(reader2, config, uri2, depth + 1);
                                    TemplateNode next = null;
                                    for (TemplateNode t = sub.root.first;t!=null;t=next) {
                                        next = t.next;
                                        cursor.add(t);
                                    }
                                } finally {
                                    if (reader2 != null) {
                                        try { reader2.close(); } catch (Exception e) {}
                                    }
                                }
                            }
                        }
                    }
                    sb.setLength(0);
                } else {
                    sb.append('{');
                    sb.append((char)c);
                }
            } else {
                sb.append((char)c);
            }
        }
        if (cursor.parent != null) {
            throw new IllegalArgumentException("mismatched close: missing close for {{" + cursor.text + "}} opened at " + filepath + "line " + cursor.line+":"+cursor.column);
        }
        if (sb.length() > 0) {
            cursor.add(new TemplateNode(sb.toString(), lastline, lastcol));
        }
//        cursor.dump(System.out, "");
        return new ZTemplate(cursor, config);
    }

    /**
     * Apply the model to the ZTemplate, returning a Reader to read the merged
     * template from
     * @param model the Model, which will be passed to any top-level embedded {@link ZPath} expressions
     * @return the Reader to read the combined model from
     */
    public Reader apply(Object model) {
        if (model == null) {
             throw new IllegalArgumentException("Model is null");
        }
        EvalContext context = null;
        for (EvalFactory factory : config.getFactories()) {
            context = factory.create(model, config);
            if (context != null) {
                break;
            }
        }
        if (context == null) {
            throw new IllegalArgumentException("No EvalContext found for " + model.getClass().getName());
        }
        return new TemplateMergingReader(this, model, context);
    }

    /**
     * Apply the model to the ZTemplate, writing the result to the supplied output
     * @param model the Model, which will be passed to any top-level embedded {@link ZPath} expressions
     * @param out the StringBuilder to write the output to
     */
    public void apply(Object model, Appendable out) {
        TemplateMergingReader reader = (TemplateMergingReader)apply(model);
        try {
            reader.transferTo(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * A FilterReader that counts lines and columns
     */
    private static class LineReader extends FilterReader {
        int line, column;
        LineReader(Reader r) {
            super(r);
            line = column = 1;
        }
        public int read() throws IOException {
            int c = super.read();
            if (c == '\n') {
                line++;
                column = 0;
            } else if (c >= 0) {
                column++;
            }
            return c;
        }
    }

    /**
     * A node in a tree of ZPath expressions and text literals
     */
    private static class TemplateNode {
        final String text;
        final ZPath expr;
        final int line, column;
        TemplateNode first, last, next, parent;

        /**
         * Create a new TemplateNode containing text
         * @param text the text
         * @param line the line the text begin at
         * @param column the line the text begin at
         */
        TemplateNode(String text, int line, int column) {
            this(text, null, line, column);
        }

        /**
         * Create a new TemplateNode containing a zpath expression
         * @param text the text the zpath was read from (for logging only)
         * @param expr the expression
         * @param line the line the text begin at
         * @param column the line the text begin at
         */
        TemplateNode(String text, ZPath expr, int line, int column) {
            this.text = text;
            this.expr = expr;
            this.line = line;
            this.column = column;
        }

        TemplateNode parent() {
            return parent;
        }

        TemplateNode first() {
            return first;
        }

        TemplateNode next() {
            return next;
        }

        TemplateNode withConfiguration(Configuration conf) {
            TemplateNode copy = new TemplateNode(text, expr == null ? null : expr.withConfiguration(conf), line, column);
            for (TemplateNode n=first;n!=null;n=n.next) {
                copy.add(n.withConfiguration(conf));
            }
            return copy;
        }

        void add(TemplateNode node) {
            if (last == null) {
                first = last = node;
            } else {
                last.next = node;
                last = node;
            }
            node.parent = this;
        }

        public String toString() {
            String s = text == null ? "<null>" : text.toString().replaceAll("\n", "\\\\n");
            return "{s="+s+" expr="+expr+" empty="+(first==null)+"}";
        }

        void dump(Appendable out, String prefix) throws IOException {
            out.append(prefix);
            out.append(toString());
            out.append("\n");
            if (first != null) {
                prefix += " ";
                for (TemplateNode c=first;c!=null;c=c.next) {
                   c.dump(out, prefix);
                }
            }
        }
    }

    /**
     * A Reader that will evaluate expressions and merge them with the TreeNode
     */
    private static class TemplateMergingReader extends Reader {
        private final EvalContext evalcontext;
        private final ZTemplate template;
        private final Object modelroot;
        private TemplateContext ctx;        // The read context (the top of the stack)
        private String buf;                     // The text buffer being read from
        private int off;                        // How far into that text buffer we are
        private int round;
        private long bytecount;

        /**
         * @param template the source template
         * @param model the model being merged
         * @param evalcontext the EvalContext
         */
        TemplateMergingReader(ZTemplate template, Object model, EvalContext evalcontext) {
            this.modelroot = model;
            this.template = template;
            this.evalcontext = evalcontext;
            this.buf = "";
            this.ctx = new TemplateContext(model, template.root, evalcontext, -1, null);
        }

        @Override public void close() throws IOException {
        }

        @Override public int read() throws IOException {
            if (off == buf.length()) {
                fill();
                if (off == buf.length()) {
                    return -1;
                }
            }
            return buf.charAt(off++);
        }

        @Override public int read(char[] buf, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (this.off == this.buf.length()) {
                fill();
                if (this.off == this.buf.length()) {
                    return -1;
                }
            }
            len = Math.min(len, this.buf.length() - this.off);
            this.buf.getChars(this.off, len, buf, off);
            this.off += len;
            return len;
        }

        public long transferTo(Writer out) throws IOException {         // This is defined in Java 10
            return transferTo((Appendable)out);
        }

        private long transferTo(Appendable out) throws IOException {    // This is not
            long biglen = 0;
            while (true) {
                if (off == buf.length()) {
                    fill();
                    if (off == buf.length()) {
                        break;
                    }
                }
                int len = buf.length() - off;
                out.append(buf, off, len);
                off += len;
                biglen += len;
            }
            return biglen;
        }

        /**
         * Fill out internal buffer. Traverses the tree and/or the iterator on the current node
         */
        private void fill() {
            // Phases:
            // * {current=root, end=root, ctx=modelroot, p=DESCEND}
            //
            // if (node is childless-eval and phase=descend) {
            //    set phase=ascend
            //    set buf to eval string
            // } else if (node 
            // If we hit a childless-eval we're {current=content, end=root, ctx=modelroot, p=DESCEND}
            //   * eval node, get the string, copy it to buf
            //   * ctx = ctx.next() - move to next sibling, going up if need be
            // 
            // 1. split the current node, so we have
            // * iterate(root, root, DESCEND)

            // This is where all the template logic is.
            // We start with a TemplateContext, which has : urrent node, halt node, model, and some context data
            // Every time we call "next" on that node it moves to the next evaluation context - the next node in the tree.
            // At that point
            //  * if it's a text node, use the text
            //  * if it's a zpath expression which evaluates as a value - {{ value }} - then substitute the value
            //  * if it's a zpath expression which evaluates as a subtree - {{# value }} - then:
            //    * evaluate the expression against the current context. For each value in the result
            //      * if it is a primitive value, evaluate all children of the current node against the current context
            //      * otherwise, evaluate all children of the current node against that value
            // 
            while (off == buf.length() && round++ <= template.config.getTemplateMaxIterations() && (ctx = ctx.next()) != null) {
                // System.out.println("** NEXT: ctx="+ctx);
                if (ctx.isText()) {
                    // Current context is a text-constant node
                    buf = ctx.node.text;
                    off = 0;
                } else if (ctx.isExprValue()) {
                    // Current context is an expr node with no children; evaluate as a text constant
                    Result result = ctx.eval();
                    if (result.all().size() > 0) {
                        Object n = evalcontext.value(result.first());
                        if (n != null) {
                            buf = n.toString();
                            if (buf == null) {
                                buf = "";
                            }
                            if (template.config.isTemplateHTMLEscape()) {
                                buf = Expr.encodeXML((String)buf, true, null).toString();
                            }
                            off = 0;
                        }
                    }
                } else if (ctx.isExprTree()) {
                    // Current context is an expr node with children; get the result from evaluating the expression
                    // against this context's model, then then repeat all our children once for each non-null,
                    // non-false item in that result.
                    //
                    // The model for each child will either be the item in the result (if it is a tree node)
                    // or the current context's model otherwise.
                    Result result = ctx.eval();
                    TemplateContext prevctx = null, nextctx = ctx;
                    ctx.setAscending();
                    List<Object> all = result.all();
                    // Build a list of TemplateContext models which evaluate our subtree, once for each item in the result. 
                    for (int i=0;i<all.size();i++) {
                        Object o = all.get(i);
                        if (o == null || Boolean.FALSE.equals(o)) {
                            // object is null or false - skip
                        } else {
                            boolean intree = o == modelroot || evalcontext.parent(o) != null;      // true if "o" is in the model tree
                            TemplateContext newctx = new TemplateContext(intree ? o : nextctx.model, ctx.node(), evalcontext, i, all);
                            if (i == 0) {
                                ctx = newctx;
                            }
                            if (i + 1 == all.size()) {
                                newctx.setNext(nextctx);
                            }
                            if (prevctx != null) {
                                prevctx.setNext(newctx);
                            }
                            prevctx = newctx;
                        }
                    }
                }
            }
            bytecount += buf.length();
            if (bytecount > template.config.getTemplateMaxOutputSize()) {
                throw new IllegalStateException("Maximum output size exceeded: " + bytecount);
            }
            if (round > template.config.getTemplateMaxIterations()) {
                throw new IllegalStateException("Maximum iterations exceeded: " + round);
            }
        }
    }

    private static class TemplateContext {
        private final EvalContext evalcontext;          // for evaluating any expression
        private final int contextIndex;                 // for evaluating any expression
        private final List<Object> contextObjects;      // for evaluating any expression
        private final Object model;                     // for evaluating any expression
        private TemplateNode node;                      // the current position in the tree
        private boolean ascending;                      // will next() ascend or descend from node?
        private final TemplateNode halt;                // if ascending and next() gets to this node, we halt
        private TemplateContext next;                   // when this model halts, which context to continue with

        /**
         * @param model the model against which any expressions within subroot should be evaluated against
         * @param subroot the root of this subsection of the tree
         * @param evalcontext for evaluation of expressions
         * @param contxtIndex for evaluation of expressions
         * @param contextObjects for evaluation of expressions
         */
        TemplateContext(Object model, TemplateNode subroot, EvalContext evalcontext, int contextIndex, List<Object> contextObjects) {
            this.model = model;
            this.node = this.halt = subroot;
            this.evalcontext = evalcontext;
            this.contextIndex = contextIndex;
            this.contextObjects = contextObjects;
        }

        /**
         * Iterate to the next point in the tree, either in this TemplateContext or the next
         * @return the next template context (may be this one) or null if traversal is complete
         */
        TemplateContext next() {
            if (!ascending && node.first != null) {
                node = node.first;
            } else if (node.next != null) {   
                node = node.next;
                ascending = false;
            } else if (node.parent != null) {   
                ascending = true;
                node = node.parent;
            } else {
                throw new Error();
            }
            if (node != halt) {
                return this;
            } else if (next == null) {
                return null;
            } else {
                return next.next();
            }
        }

        /**
         * Return the current point in the tree
         */
        TemplateNode node() {
            return node;
        }

        /**
         * Make sure in the call to next() we don't descend into node
         */
        void setAscending() {
            ascending = true;
        }

        /**
         * Set the next TemplateContext to iterate through after this one
         */
        void setNext(TemplateContext next) {
            this.next = next;
        }

        /**
         * Return true if the TemplateContext is at a text location
         */
        boolean isText() {
            return node.expr == null;
        }

        /**
         * Return true if the TemplateContext is at a point to evaluate an expression as a value
         */
        boolean isExprValue() {
            return !ascending && node.expr != null && node.first == null;
        }

        /**
         * Return true if the TemplateContext is at a point to evaluate an expression as a subtree
         */
        boolean isExprTree() {
            return !ascending && node.expr != null && node.first != null;
        }

        /**
         * Evaluate the express, return the result
         */
        Result eval() {
            evalcontext.setContext(contextIndex, contextObjects);
            return node.expr.eval(model, evalcontext);
        }

        public String toString() {
            return "{node="+node+" halt="+halt+" ascending="+ascending+" model="+model+"}";
        }
    }
    
}
