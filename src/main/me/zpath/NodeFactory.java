package me.zpath;

import java.util.*;

/**
 * A NodeFactory creates Nodes from objects.
 */
public interface NodeFactory {
    /**
     * Create a new Node, or return null if this factory doesn't apply to this type of object
     * @param proxy the object to proxy as a Node
     * @param config the Configuration, which can be modified (say be adding function) to better support the specified object type
     */
    public Node create(Object proxy, Configuration config);
}
