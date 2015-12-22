package org.jenkinsci.unusedcode;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Main {
    // experimental update center has more plugins but are often older
    private static final String UPDATE_CENTER_URL =
    // "http://updates.jenkins-ci.org/experimental/update-center.json";
    "http://updates.jenkins-ci.org/update-center.json";

    public static void main(String[] args) throws Exception {
        final long start = System.currentTimeMillis();
        log("<h2> Finds and reports unused methods in Jenkins api </h2>"
                + " (including in latest published plugins and potential usage in jelly files, except getters, setters and fields, except deprecated classes and methods, except unit tests)");
        final UpdateCenter updateCenter = new UpdateCenter(new URL(UPDATE_CENTER_URL));
        log("Downloaded update-center.json");
        updateCenter.download();
        log("All files are up to date (" + updateCenter.getPlugins().size() + " plugins)");

        log("Indexing api in Jenkins");
        final byte[] bytes = Indexer.readJenkinsCoreFile(updateCenter.getCore().getFile());
        final Indexer indexer = new Indexer();
        indexer.indexJar(new ByteArrayInputStream(bytes));

        Log.log("Analyzing usage in core and plugins");
        analyze(updateCenter.getCore(), updateCenter.getPlugins(), indexer);

        new Reports(updateCenter, indexer).report();

        log("duration : " + (System.currentTimeMillis() - start) + " ms at "
                + DateFormat.getDateTimeInstance().format(new Date()));
        Log.closeLog();
    }

    private static void analyze(final JenkinsFile core, List<JenkinsFile> plugins,
            final Indexer indexer) throws InterruptedException, ExecutionException {
        final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime()
                .availableProcessors());
        final List<Future<Object>> futures = new ArrayList<>(plugins.size() + 1);
        final Callable<Object> coreTask = new Callable<Object>() {
            @Override
            public Object call() throws IOException {
                final Analyzer analyzer = new Analyzer(indexer);
                analyzer.analyzeCore(core.getFile());
                return null;
            }
        };
        futures.add(executorService.submit(coreTask));
        for (final JenkinsFile plugin : plugins) {
            final Callable<Object> task = new Callable<Object>() {
                @Override
                public Object call() throws IOException {
                    final Analyzer analyzer = new Analyzer(indexer);
                    try {
                        analyzer.analyzePlugin(plugin.getFile());
                    } catch (final EOFException e) {
                        Log.log("deleting " + plugin.getFile().getName()
                                + " and skipping, because " + e.toString());
                        plugin.getFile().delete();
                    }
                    return null;
                }
            };
            futures.add(executorService.submit(task));
        }

        for (final Future<Object> future : futures) {
            future.get();
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        // wait for threads to stop
        Thread.sleep(100);
        log("");
        log("");
    }

    private static void log(String message) {
        Log.log(message);
    }
}
