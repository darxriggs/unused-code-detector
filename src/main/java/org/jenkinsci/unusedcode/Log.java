package org.jenkinsci.unusedcode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public final class Log {
    // relative to user dir
    private static final File HTML_FILE = new File("target/output.html");
    private static final Writer FILE_WRITER = openFileWriter();

    private Log() {
        super();
    }

    private static Writer openFileWriter() {
        try {
            final Writer writer = new FileWriter(HTML_FILE);
            writer.append("<html><head>");
            writer.append("<title>Unused code in Jenkins api</title>");
            writer.append(
                    "<style>body { font-family: Arial, Helvetica, sans-serif; font-size: 12px; }</style>");
            writer.append("</head><body>");
            return writer;
        } catch (final IOException e) {
            System.out.println("Unable to open " + HTML_FILE + " to write output");
            return null;
        }
    }

    public static void log(String message) {
        System.out.println(message);
        System.out.flush();
        if (FILE_WRITER != null) {
            try {
                FILE_WRITER.write(message);
                FILE_WRITER.write("<br/>");
                FILE_WRITER.write('\n');
                FILE_WRITER.flush();
            } catch (final IOException e) {
                return;
            }
        }
    }

    public static void print(String message) {
        System.out.print(message);
        System.out.flush();
        if (FILE_WRITER != null) {
            try {
                FILE_WRITER.write(message);
                FILE_WRITER.flush();
            } catch (final IOException e) {
                return;
            }
        }
    }

    public static void closeLog() {
        try {
            FILE_WRITER.append("</body></html>");
            FILE_WRITER.close();
            System.out.println("Output written to " + HTML_FILE.getPath());
        } catch (final IOException e) {
            return;
        }
    }
}
