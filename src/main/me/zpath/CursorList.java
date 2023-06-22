package me.zpath;

import java.util.*;

/**
 * A list with a cursor and four methods to use it:
 *
 * next() - return the next item in the list, or null if none
 * peek() - as next(), but don't move the cursor
 * tell() - return the current position in the list
 * seek() - set the current position in the list
 */
class CursorList<E> extends AbstractList<E> {

    private final CursorList<E> root;
    private final int start;
    private Object[] data;
    private int size, pos;

    CursorList() {
        this.data = new Object[16];
        this.root = null;
        this.start = 0;
        this.pos = 0;
    }

    CursorList(Iterable<E> list) {
        this();
        for (E e : list) {
            add(e);
        }
    }

    private CursorList(CursorList<E> parent, int off, int len) {
        this.root = parent;
        this.start = off;
        this.size = len;
        this.pos = 0;
    }

    @Override public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    @Override public E set(int i, E value) {
        if (i < 0 || i > size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        E old;
        if (root != null) {
            old = (E)root.data[start + i];
            root.data[start + i] = value;
        } else {
            old = (E)data[i];
            data[i] = value;
        }
        return old;
    }

    @SuppressWarnings("unchecked")
    @Override public boolean add(E value) {
        if (root != null) {
            throw new IllegalStateException("Can't add to view");
        }
        if (size == data.length) {
            data = Arrays.copyOf(data, size + (size>>1));
        }
        data[size++] = value;
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override public E get(int i) {
        if (i < 0 || i > size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        if (root != null) {
            return (E)root.data[start + i];
        } else {
            return (E)data[i];
        }
    }

    public E next() {
        return pos == size() ? null : get(pos++);
    }

    public E peek() {
        return pos == size() ? null : get(pos);
    }

    public CursorList<E> seek(int off) {
        if (off < 0 || off > size()) {
            throw new IllegalArgumentException();
        }
        pos = off;
        return this;
    }

    public int tell() {
        return pos;
    }

    public CursorList<E> sub(int off, int end) {
        if (off < 0 || end < off || end > start + size()) {
            throw new IllegalArgumentException("start="+start+" end="+end+" size="+size());
        }
        return new CursorList<E>(root != null ? root : this, start + off, end - off);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (tell() < 0) {
            sb.append("[\u0332");
        } else {
            sb.append("[");
        }
        for (int i=0;i<size();i++) {
            String s = get(i) == null ? "<NULL>" : get(i).toString();;
            if (s.trim().equals("")) {
                s = " ";
            }
            if (i > 0) {
//                sb.append(' ');
            }
            if (i == tell()) {
                for (int j=0;j<s.length();j++) {
                    sb.appendCodePoint(s.codePointAt(j));
                    sb.append("\u0332");
                }
            } else {
                sb.append(s);
            }
        }
        if (tell() == size()) {
            sb.append("]\u0332");
        } else {
            sb.append("]");
        }
        return sb.toString();
    }
}
