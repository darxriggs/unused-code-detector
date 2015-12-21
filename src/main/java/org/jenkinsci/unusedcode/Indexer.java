package org.jenkinsci.unusedcode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class Indexer {
    private static final char SEPARATOR = '.';

    // see http://java.sun.com/javase/6/docs/platform/serialization/spec/serialTOC.html for
    // read/write*,
    // values() and valueOf(String) for enum
    // (non synthetics methods as said in spec),
    private static final Set<String> IGNORED_METHODS = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList("<clinit>", "main", "readResolve",
                    "readObject", "readExternal", "writeObject", "writeExternal", "writeReplace",
                    "values", "valueOf",
                    // xstream AbstractSingleValueConverter:
                    "fromString", "canConvert", "marshal", "unmarshal")));

    private static final Set<String> IGNORED_CLASSES = Collections.emptySet();
    // Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
    // // no longer ignored because jelly files are read: "hudson/Functions",
    // // could be ignored but used only in ui-samples-plugin:
    // // "jenkins/util/groovy/AbstractGroovyViewModule"
    // )));

    private final Set<String> methods = new LinkedHashSet<>();
    private final Set<String> synchronizedMethods = Collections.synchronizedSet(methods);
    private final Hierarchy hierarchy = new Hierarchy();

    public static byte[] readJenkinsCoreFile(File coreWarFile) throws ZipException, IOException {
        final ZipFile zipFile = new ZipFile(coreWarFile);
        try {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String fileName = entry.getName();
                if (fileName.startsWith("WEB-INF/lib/jenkins-core") && fileName.endsWith(".jar")) {
                    final InputStream input = zipFile.getInputStream(entry);
                    final ByteArrayOutputStream output = new ByteArrayOutputStream(
                            (int) entry.getSize());
                    final byte[] buffer = new byte[50 * 1024];
                    int len = input.read(buffer);
                    while (len != -1) {
                        output.write(buffer, 0, len);
                        len = input.read(buffer);
                    }
                    return output.toByteArray();
                }
            }
        } finally {
            zipFile.close();
        }
        throw new IllegalArgumentException("jenkins-core file not found");
    }

    public static String getMethodKey(String className, String name, String desc) {
        return className + SEPARATOR + name + desc;
    }

    public void indexJar(InputStream input) throws IOException {
        final JarReader jarReader = new JarReader(input);
        try {
            String fileName = jarReader.nextClass();
            while (fileName != null) {
                indexClass(jarReader.getInputStream());
                fileName = jarReader.nextClass();
            }
        } finally {
            jarReader.close();
        }
    }

    public Set<String> getMethods() {
        return synchronizedMethods;
    }

    public Hierarchy getHierarchy() {
        return hierarchy;
    }

    private void indexClass(InputStream input) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        hierarchy.registerHierarchyOfClass(classReader);
        if (IGNORED_CLASSES.contains(classReader.getClassName())) {
            return;
        }
        final CalledClassVisitor calledClassVisitor = new CalledClassVisitor();
        classReader.accept(calledClassVisitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES);
        methods.addAll(calledClassVisitor.getMethods());
        JavaHelper.excludeJavaMethods(classReader, methods);
    }

    /**
     * Implementation of ASM ClassVisitor.
     */
    private static class CalledClassVisitor extends ClassVisitor {
        private static final int OPCODE_CLASS_FILTERED = Opcodes.ACC_INTERFACE
                | Opcodes.ACC_ANNOTATION | Opcodes.ACC_DEPRECATED;
        private static final int OPCODE_METHOD_FILTERED = Opcodes.ACC_SYNTHETIC
                | Opcodes.ACC_DEPRECATED;
        // do not ignore private methods (currently 3 of them): | Opcodes.ACC_PRIVATE

        private final Set<String> methods = new HashSet<>();
        private String currentClass;

        CalledClassVisitor() {
            super(Opcodes.ASM5);
        }

        public Set<String> getMethods() {
            return methods;
        }

        private boolean isClassFiltered(int asmAccess) {
            return (asmAccess & OPCODE_CLASS_FILTERED) != 0;
        }

        private boolean isMethodOrFieldFiltered(int asmAccess) {
            return (asmAccess & OPCODE_METHOD_FILTERED) != 0;
        }

        private boolean isDefaultConstructorFiltered(int access, String name, String desc) {
            return "<init>".equals(name) && "()V".equals(desc);
        }

        private boolean isPublicAccessorFiltered(int access, String name, String desc) {
            // getter without parameter or setter with one parameter
            return isGetter(name, desc) || isSetter(name, desc);
        }

        private boolean isGetter(String name, String desc) {
            return name.startsWith("get") && desc.startsWith("()")
                    && Type.getReturnType(desc) != Type.VOID_TYPE || name.startsWith("is")
                    && desc.startsWith("()") && Type.getReturnType(desc) == Type.BOOLEAN_TYPE;
        }

        private boolean isSetter(String name, String desc) {
            return name.startsWith("set") && Type.getReturnType(desc) == Type.VOID_TYPE
                    && Type.getArgumentTypes(desc).length == 1;
        }

        private boolean isDoMethodFiltered(int access, String name, String desc) {
            // methods like
            // doLaunchSlaveAgent(org.kohsuke.stapler.StaplerRequest;org.kohsuke.stapler.StaplerResponse)
            // are called by UI using <form method="post" action="launchSlaveAgent"> in jelly
            return name.startsWith("do");
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            // log(name + " extends " + superName + " {");
            if (isClassFiltered(access) || name.endsWith("/Messages")) {
                // some classes are ignored:
                // annotations and interfaces do not call methods
                // and there are Messages classes by Jenkins package generated for translations
                currentClass = null;
            } else {
                currentClass = name;
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            if (currentClass != null && !isMethodOrFieldFiltered(access)
                    && !isDefaultConstructorFiltered(access, name, desc)
                    && !isPublicAccessorFiltered(access, name, desc)
                    && !isDoMethodFiltered(access, name, desc) && !IGNORED_METHODS.contains(name)) {
                methods.add(getMethodKey(currentClass, name, desc));
            }
            return null;
        }

        // searching unused fields would cause many false positives
        // because some fields are used directly in jelly views (such as
        // HistoryPageFilter.hasUpPage)
        // or are extensions points (such as OldDataMonitor.runDeleteListener)

        // private static final int OPCODE_FIELD_INLINEABLE = Opcodes.ACC_FINAL |
        // Opcodes.ACC_STATIC;
        // private boolean isFieldInlineable(int access, String desc) {
        // if ((access & OPCODE_FIELD_INLINEABLE) != 0) {
        // final Type type = Type.getType(desc);
        // return type.getSort() != Type.OBJECT
        // || "java.lang.String".equals(type.getClassName());
        // }
        // return false;
        // }
        //
        // @Override
        // public FieldVisitor visitField(int access, String name, String desc, String signature,
        // Object value) {
        // if (currentClass != null && !isMethodOrFieldFiltered(access)
        // && !isFieldInlineable(access, desc)) {
        // fields.add(getFieldKey(currentClass, name, desc));
        // }
        // return null;
        // }
        //
        // public static String getFieldKey(String className, String name, String desc) {
        // return className + SEPARATOR + name; // + SEPARATOR + desc;
        // // desc (ie type) of a field is not necessary to identify the field.
        // // it is ignored since it would only clutter reports
        // }
    }
}
