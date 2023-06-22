package me.zpath;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * A ZTemplate is a template system based around ZPath. Text is read in, ZPath expressions
 * in the text are evaluated against a model and substituted into the termklate.
 * <ul>
 * <li>
 *  A ZPath expression included as
 *  <code>{{ <i>expr</i> }}</code> will be evalated and the output inserted into the code
 * </li>
 * <li>
 *  A ZPath expression included as
 *  <code>{{# <i>expr</i> }} ... {{/ <i>expr</i> }}</code> will be evaluated, and the
 *  content between the open/close tags evaluated once for each matching node.
 * </li>
 * </ul>
 * <p>
 * ZPath expressions can be surrounded in whitespace to remove ambiguity. This is necessary
 * if your expression begins with a #, eg <code>{{ #0 }}</code>.
 * </p>
 * A ZTemplate can be compiled once and used simulteneously in multiple threads.
 */
public class ZTemplate {

    private final TemplateNode root;
    private final Configuration config;
    private boolean htmlEscape = true;

    private ZTemplate(TemplateNode root, Configuration config) {
        this.root = root;
        this.config = config;
    }

    /**
     * Compile the ZTemplate. Calls <code>compile(reader, null)</code>
     * @param reader the reader to read from
     * @return a ZTemplate
     * @throws IllegalArgumenmtException if the ZPath expressions in the template cannot be parsed
     * @throws IOException if the underlying stream throws an IOException
     */
    public static ZTemplate compile(Reader reader) throws IOException {
        return compile(reader, null);
    }

    /**
     * Compile the ZTemplate
     * @param reader the reader to read from
     * @param config the Configuration to use for the embedded ZPath expressions
     * @throws IllegalArgumenmtException if the ZPath expressions in the template cannot be parsed
     * @throws IOException if the underlying stream throws an IOException
     * @return a ZTemplate
     */
    public static ZTemplate compile(Reader reader, Configuration config) throws IOException {
        if (config == null) {
            config = Configuration.getDefault();
        }

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

                    int type = 0;       // 0 = substitution, 1 = open, 2 = close
                    String expression;

                    if (sb.length() > 1 && sb.charAt(0) == '#') {
                        type = 1;
                        expression = sb.subSequence(1, sb.length()).toString().trim();
                    } else if (sb.length() > 1 && sb.charAt(0) == '/') {
                        expression = sb.subSequence(1, sb.length()).toString().trim();
                        type = 2;
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
                            throw new IllegalArgumentException("invalid ZPath {{" + sb + "}} at line " + tmpline + ":" + tmpcolumn, e);
                        }
                    } else if (type == 2) {
                        if (cursor.expr == null) {
                            throw new IllegalArgumentException("mismatched close {{" + sb + "}} at line " + tmpline + ":" + tmpcolumn);
                        } else {
                            boolean match = false;
                            try {
                                match = cursor.expr.equals(ZPath.compile(expression));
                            } catch (Exception e) {}
                            if (!match) {
                                throw new IllegalArgumentException("mismatched close {{" + sb + "}} at line " + tmpline + ":" + tmpcolumn + ": doesn't match {{" + cursor.text + "}} from line " + cursor.line+":"+cursor.column);
                            } else {
                                cursor = cursor.parent;
                            }
                        }
                    }
                    sb.setLength(0);
                } else {
                    sb.append((char)c);
                }
            } else {
                sb.append((char)c);
            }
        }
        if (sb.length() > 0) {
            cursor.add(new TemplateNode(sb.toString(), lastline, lastcol));
        }
        return new ZTemplate(cursor, config);
    }

    /**
     * Set whether this ZTemplate should escape any ZPath string expresions in a way
     * that makes them suitable for HTML. The default is true
     * @param htmlEscape whether to escape Strings written to the output to make them suitable for HTML
     * @return this
     */
    public ZTemplate htmlEscape(boolean htmlEscape) {
        this.htmlEscape = htmlEscape;
        return this;
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

        void dump(Appendable out, String prefix, Set<Object> seen) throws IOException {
            out.append(prefix);
            out.append(toString());
            out.append("\n");
            if (first != null) {
                prefix += " ";
                for (TemplateNode c=first;c!=null;c=c.next) {
                   c.dump(out, prefix, seen);
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
        private TemplateContext ctx;        // The read context (the top of the stack)
        private String buf;                     // The text buffer being read from
        private int off;                        // How far into that text buffer we are

        /**
         * @param template the source template
         * @param model the model being merged
         * @param evalcontext the EvalContext
         */
        TemplateMergingReader(ZTemplate template, Object model, EvalContext evalcontext) {
            this.template = template;
            this.evalcontext = evalcontext;
            this.ctx = new TemplateContext(null, model, template.root, null);
            this.buf = "";
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
            do {
//                System.out.println("## CTX="+ctx);
                if (ctx == null) {
                    return;
                } else if (ctx.cursor == ctx.start && ctx.children != null && ctx.index < ctx.children.size()) {
                    ctx.model = ctx.children.get(ctx.index++);
                    if (ctx.cursor.first() != null) {
                        ctx.cursor = ctx.cursor.first();
                    }
                } else if (ctx.cursor == ctx.start && ctx.children != null) {
                    // Iterator complete, remove ctx
                    TemplateNode here = ctx.cursor;
                    ctx = ctx.parent;
                    ctx.descend = false;
                    continue;
                } else if (ctx.descend && ctx.cursor.first() != null) {
                    ctx.cursor = ctx.cursor.first();
                } else if (ctx.cursor.next() != null) {
                    ctx.cursor = ctx.cursor.next();
                    ctx.descend = true;
                } else if (ctx.cursor.parent() != null) {
                    TemplateNode c = ctx.cursor;
                    do {
                        c = c.parent();
                    } while (c != ctx.start && c.next() == null);
                    ctx.cursor = c;
                    ctx.descend = false;
                    continue;
                }
                if (ctx.cursor.parent == null) {
                    return;
                    // We're back at the root
                } else if (ctx.cursor.expr == null) {
                    buf = ctx.cursor.text;
                    off = 0;
                } else if (ctx.cursor.first == null) {
                    // Node has no children; display the node value
                    evalcontext.setContext(ctx.index - 1, ctx.children);
                    Result result = ctx.cursor.expr.eval(ctx.model, evalcontext);
    //                System.out.println("## EVAL: ctx="+ctx+" expr="+cursor.expr+" o="+out);
                    if (result.all().size() > 0) {
                        Object n = evalcontext.unwrap(result.first());
                        if (n != null) {
                            buf = n.toString();
                            if (buf == null) {
                                buf = "";
                            }
                            if (template.htmlEscape) {
                                buf = Expr.encodeXML((String)buf, true, null).toString();
                            }
                            off = 0;
                        }
                    }
                } else {
                    // Node has children
                    List<Object> out = ctx.cursor.expr.eval(ctx.model).all();
//                    System.out.println("## EVAL: ctx="+ctx+" o="+out);
                    ctx = new TemplateContext(ctx, ctx.model, ctx.cursor, out);
                }
            } while (off == buf.length());
        }

        // Context as we travers around the TemplateNode tree.
        // Forms a stack (it has a pointer to parent eEmplateContext)
        private static class TemplateContext {
            final TemplateContext parent;
            final TemplateNode start;
            final List<Object> children;
            int index;
            boolean descend;
            TemplateNode cursor;
            Object model; 
            TemplateContext(TemplateContext parent, Object model, TemplateNode start, List<Object> children) {
                this.parent = parent;
                this.start = start;
                this.cursor = start;
                this.model = model;
                this.children = children;
                this.descend = true;
            }
            public String toString() {
                return "{start="+start+" cursor="+cursor+" model="+model+" i="+(children==null?"null":index+"/"+children.size()) + " desc="+descend+"}";
            }
        }
    }
    
}
