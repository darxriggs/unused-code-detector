package org.jenkinsci.unusedcode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Analyzer {
    // python-wrapper has wrappers for all extension points and descriptors,
    // they are just wrappers and not real usage
    public static final Set<String> IGNORED_PLUGINS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("python-wrapper.hpi")));

    private final Set<String> methods;
    private final Hierarchy coreHierarchy;
    private final Hierarchy pluginHierarchy;
    private final ClassVisitor classVisitor = new CallersClassVisitor();

    public Analyzer(Indexer indexer) {
        super();
        this.methods = indexer.getMethods();
        this.coreHierarchy = indexer.getHierarchy();
        this.pluginHierarchy = new Hierarchy(coreHierarchy);
    }

    private void analyzeWar(File file) throws IOException {
        final WarReader warReader = new WarReader(file, false);
        try {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                // ignore bad class com/ibm/icu/impl/data/LocaleElements_zh__PINYIN.class
                if (!fileName.equals("com/ibm/icu/impl/data/LocaleElements_zh__PINYIN.class")) {
                    analyzeClass(warReader.getInputStream());
                }
                fileName = warReader.nextClass();
            }
        } finally {
            warReader.close();
        }

        final WarReader warReader2 = new WarReader(file, false);
        try {
            String fileName = warReader2.nextJelly();
            while (fileName != null) {
                analyzeJelly(warReader2.getInputStream());
                fileName = warReader2.nextJelly();
            }
        } finally {
            warReader2.close();
        }
    }

    public void analyzeCore(File file) throws IOException {
        analyzeWar(file);
    }

    public void analyzePlugin(File file) throws IOException {
        if (IGNORED_PLUGINS.contains(file.getName())) {
            return;
        }
        Log.log("analyzing " + file.getName());
        final WarReader warReader = new WarReader(file, false);
        try {
            String fileName = warReader.nextClass();
            while (fileName != null) {
                // ignore bad class com/ibm/icu/impl/data/LocaleElements_zh__PINYIN.class
                if (!fileName.equals("com/ibm/icu/impl/data/LocaleElements_zh__PINYIN.class")) {
                    indexClass(warReader.getInputStream());
                }
                fileName = warReader.nextClass();
            }
        } finally {
            warReader.close();
        }

        analyzeWar(file);

        // final InputStream input = new FileInputStream(file);
        // final JarReader jarReader = new JarReader(input);
        // try {
        // String fileName = jarReader.nextClass();
        // while (fileName != null) {
        // indexClass(jarReader.getInputStream());
        // fileName = jarReader.nextClass();
        // }
        // } finally {
        // jarReader.close();
        // input.close();
        // }
    }

    private void indexClass(InputStream input) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        pluginHierarchy.registerHierarchyOfClass(classReader);
    }

    private void analyzeJelly(InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
        final byte[] buffer = new byte[1024];
        int len = input.read(buffer);
        while (len != -1) {
            output.write(buffer, 0, len);
            len = input.read(buffer);
        }
        final String string = new String(output.toByteArray(), StandardCharsets.UTF_8);

        final HashSet<String> methods2;
        synchronized (methods) {
            methods2 = new HashSet<>(methods);
        }
        for (final String method : methods2) {
            String methodName = method.substring(0, method.lastIndexOf('('));
            methodName = methodName.substring(methodName.lastIndexOf('.') + 1);
            if (string.contains(methodName)) {
                methods.remove(method);
            }
        }
    }

    private void analyzeClass(InputStream input) throws IOException {
        final ClassReader classReader = new ClassReader(input);
        classReader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    void methodCalled(String className, String name, String desc) {
        // Calls to java and javax are ignored first
        if (!JavaHelper.isJavaClass(className)) {
            methods.removeAll(pluginHierarchy.getPolymorphicMethods(className, name, desc));
        }
    }

    private class CallersClassVisitor extends ClassVisitor {
        CallersClassVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            // asm javadoc says to return a new instance each time
            return new CallersMethodVisitor();
        }
    }

    /**
     * Implementation of ASM Method Visitor.
     */
    private class CallersMethodVisitor extends MethodVisitor {
        CallersMethodVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // log("\t" + owner + " " + name + " " + desc);
            methodCalled(owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            // log("\t" + owner + " " + name + " " + desc);
            methodCalled(owner, name, desc);
        }

        // searching unused fields would cause many false positives
        // because some fields are used directly in jelly views (such as
        // HistoryPageFilter.hasUpPage)
        // or are extensions points (such as OldDataMonitor.runDeleteListener)

        // private boolean isFieldRead(int opcode) {
        // return opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC;
        // }
        //
        // void fieldCalled(String className, String name, String desc) {
        // // Calls to java and javax are ignored first
        // if (!Indexer.isJavaClass(className)) {
        // final String field = Indexer.getFieldKey(className, name, desc);
        // fields.remove(field);
        // // final List<String> superClassAndInterfaces = superClassAndInterfacesByClass
        // // .get(className);
        // // if (superClassAndInterfaces != null) {
        // // for (final String superClassOrInterface : superClassAndInterfaces) {
        // // fieldCalled(superClassOrInterface, name, desc);
        // // }
        // // }
        // }
        // }
        //
        // @Override
        // public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        // if (isFieldRead(opcode)) {
        // // log("\t" + owner + " " + name + " " + desc);
        // fieldCalled(owner, name, desc);
        // }
        // }
    }
}
