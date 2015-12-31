package org.jenkinsci.unusedcode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

public final class JavaHelper {
    private static final int VISIBILITY_PUBLIC_OR_PROTECTED = Modifier.PUBLIC | Modifier.PROTECTED;
    private static final Map<String, Set<String>> javaMethodListByClassMap = new HashMap<>();
    private static final Set<String> javaLangObjectMethods = getJavaMethods(
            Type.getInternalName(Object.class));

    private JavaHelper() {
        super();
    }

    public static boolean isJavaClass(String asmClassName) {
        // if starts with java/ or javax/, then it's a class of core java
        return asmClassName.startsWith("java/") || asmClassName.startsWith("javax/");
    }

    public static void excludeJavaMethods(ClassReader classReader, Set<String> methods) {
        // exclude methods which implements or overrides a java method,
        // for example doFilter, equals, hashCode
        if (isJavaClass(classReader.getSuperName())) {
            excludeJavaMethods(methods, classReader.getSuperName());
        } else {
            methods.removeAll(javaLangObjectMethods);
        }
        for (final String interfaceName : classReader.getInterfaces()) {
            excludeJavaMethods(methods, interfaceName);
        }
    }

    private static void excludeJavaMethods(Set<String> methods, String asmClassName) {
        if (!methods.isEmpty() && isJavaClass(asmClassName)) {
            final Set<String> javaMethods = getJavaMethods(asmClassName);
            final Iterator<String> iterator = methods.iterator();
            while (iterator.hasNext()) {
                final String method = iterator.next();
                for (final String javaMethod : javaMethods) {
                    if (method.endsWith(javaMethod)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

    private static Set<String> getJavaMethods(String asmClassName) {
        Set<String> methods = javaMethodListByClassMap.get(asmClassName);
        if (methods == null) {
            methods = getJavaMethodsFromClass(asmClassName);
            javaMethodListByClassMap.put(asmClassName, methods);
        }
        return methods;
    }

    private static Set<String> getJavaMethodsFromClass(String asmClassName) {
        Class<?> clazz;
        try {
            clazz = Class.forName(Type.getObjectType(asmClassName).getClassName());
        } catch (final Throwable t) { // NOPMD
            final String msg = "Can not load " + Type.getObjectType(asmClassName).getClassName();
            Log.log(msg);
            return Collections.emptySet();
        }
        final Set<Class<?>> classes = new HashSet<>();
        while (clazz != null) {
            classes.add(clazz);
            if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
                for (final Class<?> clazz2 : clazz.getInterfaces()) {
                    classes.add(clazz2);
                    classes.addAll(Arrays.asList(clazz2.getInterfaces()));
                }
            }
            clazz = clazz.getSuperclass();
        }
        final Set<String> methods = new HashSet<>();
        for (final Class<?> clazz2 : classes) {
            for (final Method method : clazz2.getDeclaredMethods()) {
                if ((method.getModifiers() & VISIBILITY_PUBLIC_OR_PROTECTED) != 0) {
                    final String methodKey = method.getName() + Type.getMethodDescriptor(method);
                    if (!methods.contains(methodKey)) {
                        methods.add(methodKey);
                    }
                }
            }
        }
        return methods;
    }
}
