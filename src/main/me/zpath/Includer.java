package me.zpath;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.Path;

/**
 * The Includer interface deals with included files from a ZTemplate
 */
public interface Includer {

    /**
     * Return a Reader for an included file, or null if it can't be resolved
     * @param path the included path, exactly as specified in <code>{{&lt; path}}</code> 
     * @param uri the inculded path as a URI, resolved against the root URI of the original input
     */
    public Reader include(String path, URI rootpath) throws IOException;

    /**
     * Return an Includer which fill read resourced from the file system
     * @param root if not null, the root file to resolve relative paths against, otherwise they will be resolved against the current directory
     * @return a new Includer
     */
    public static Includer getDefault(final File root) {
        return new Includer() {
            @Override public Reader include(String path, URI uri) throws IOException {
                if (!uri.isAbsolute()) {
                    uri = (root != null ? root.toPath() : Paths.get(".")).toUri().resolve(uri);
                }
                Path p = Path.of(uri);
                if (p != null && Files.isReadable(p)) {
                    return Files.newBufferedReader(p);
                }
                return null;
            }
        };
    }

}
