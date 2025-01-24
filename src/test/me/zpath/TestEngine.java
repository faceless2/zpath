package me.zpath;

interface TestEngine {
    /**
     * For the specified type, parse the data and return it, or return null if we don't support it
     */
    Object load(String type, String data) throws Exception;                                                     

    /**
     * For the supplied parent, return the specified child.
     * @param key if not null, the access is by string, otherwise it is an indexed child
     * @param index which matching child (zero for the first)
     * @return the child - if a string/double/boolean/null primitive it should return the primitive
     * @throws RuntimeException if the child is not found
     */
    Object child(Object parent, String key, int index);
}           
