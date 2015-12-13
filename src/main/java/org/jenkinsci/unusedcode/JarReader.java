package org.jenkinsci.unusedcode;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarReader implements Closeable {
    private final ZipInputStream zipInputStream;
    private ZipEntry entry;

    public JarReader(InputStream input) throws IOException {
        super();
        this.zipInputStream = new ZipInputStream(new BufferedInputStream(input, 50 * 1024));
    }

    public String nextClass() throws IOException {
        return nextEntry(".class");
    }

    String nextEntry(String extension) throws IOException {
        entry = zipInputStream.getNextEntry();
        while (entry != null && !entry.getName().endsWith(extension)) {
            entry = zipInputStream.getNextEntry();
        }
        if (entry != null) {
            return entry.getName();
        }
        return null;
    }

    public InputStream getInputStream() throws IOException {
        return zipInputStream;
    }

    @Override
    public void close() throws IOException {
        zipInputStream.close();
    }
}
