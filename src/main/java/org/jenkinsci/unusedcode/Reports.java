package org.jenkinsci.unusedcode;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class Reports {
    private final UpdateCenter updateCenter;
    private final Indexer indexer;

    public Reports(UpdateCenter updateCenter, Indexer indexer) {
        super();
        this.updateCenter = updateCenter;
        this.indexer = indexer;
    }

    public void report() {
        log("ignored plugins : " + Analyzer.IGNORED_PLUGINS);
        log("");

        log("<h3 id=unusedMethods>Unused methods in Jenkins</h3>");
        final Set<String> methods = new TreeSet<>(filterOnJenkins(indexer.getMethods()));
        for (final String method : methods) {
            log(formatMethod(method));
        }
        log("");
        log("<h3 id=summary>Summary</h3>");
        log(updateCenter.getPlugins().size() + " published plugins");
        log(methods.size()
                + " unused methods in Jenkins, except getters, setters, except deprecated classes and methods");
        log("Unused deprecated classes, methods and fields are listed in the <a href='https://ci.jenkins-ci.org/view/Infrastructure/job/infra_deprecated-usage-in-plugins/ws/target/output.html#deprecatedApiNotUsed'> deprecated-usage-in-plugins job </a>");
    }

    private static Set<String> filterOnJenkins(Set<String> methods) {
        final Set<String> filtered = new LinkedHashSet<>();
        for (final String method : methods) {
            final boolean jenkinsClass = method.startsWith("jenkins/")
                    || method.startsWith("hudson/");
            // || method.startsWith("org/kohsuke/") : org/kohsuke/stapler/ are false positives ?
            if (jenkinsClass) {
                filtered.add(method);
            }
        }
        return filtered;
    }

    private static String formatMethod(String method) {
        return format(method.replace("java/lang/", "").replace(")V", ")").replace(")L", ") ")
                .replace("(L", "(").replace(";L", ";").replace(";)", ")").replace(".<init>", ""));
    }

    private static String format(String classOrFieldOrMethod) {
        // replace "org/mypackage/Myclass" by "org.mypackage.Myclass"
        return classOrFieldOrMethod.replace('/', '.');
    }

    private static void log(String message) {
        Log.log(message);
    }
}
