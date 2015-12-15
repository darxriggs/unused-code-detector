package org.jenkinsci.unusedcode;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;

public class Hierarchy {
    private final Hierarchy superHierarchy;
    private final Map<String, String> superClassByClassMap = new HashMap<String, String>();
    private final Map<String, Set<String>> subClassListByClassMap = new HashMap<String, Set<String>>();

    public Hierarchy() {
        this(null);
    }

    public Hierarchy(Hierarchy superHierarchy) {
        super();
        this.superHierarchy = superHierarchy;
    }

    public void registerHierarchyOfClass(ClassReader classReader) {
        final String asmClassName = classReader.getClassName();
        final String asmSuperClassName = classReader.getSuperName();
        if (!JavaHelper.isJavaClass(asmSuperClassName)) {
            // java and javax classes are not analyzed
            registerSuperClass(asmSuperClassName, asmClassName);
            registerSubClass(asmSuperClassName, asmClassName);
        }
        for (final String asmInterfaceName : classReader.getInterfaces()) {
            if (!JavaHelper.isJavaClass(asmInterfaceName)) {
                registerSubClass(asmInterfaceName, asmClassName);
            }
        }
    }

    private void registerSuperClass(String asmSuperClassName, String asmClassName) {
        this.superClassByClassMap.put(asmClassName, asmSuperClassName);
    }

    private void registerSubClass(String asmSuperClassName, String asmClassName) {
        Set<String> subClassList = this.subClassListByClassMap.get(asmSuperClassName);
        if (subClassList == null) {
            subClassList = new HashSet<String>(1);
            this.subClassListByClassMap.put(asmSuperClassName, subClassList);
        }
        subClassList.add(asmClassName);
    }

    private Set<String> getAllSubClasses(String className) {
        Set<String> subClassList = subClassListByClassMap.get(className);
        if (subClassList == null) {
            return Collections.emptySet();
        }
        final Set<String> allSubClasses = new HashSet<String>(subClassList);
        while (!subClassList.isEmpty()) {
            final Set<String> subClasses = new HashSet<String>();
            for (final String subClass : subClassList) {
                final Set<String> subSubClassList = subClassListByClassMap.get(subClass);
                if (subSubClassList != null) {
                    subClasses.addAll(subSubClassList);
                }
            }
            allSubClasses.addAll(subClasses);
            subClassList = subClasses;
        }
        return allSubClasses;
    }

    public Set<String> getPolymorphicMethods(String className, String name, String desc) {
        final Set<String> polymorphicMethods = new HashSet<>(1);
        // method directly on class
        addSuperMethodsOrItself(className, name, desc, polymorphicMethods);

        // Management of dynamic call on an object
        // (the class is only known at runtime given inheritance and polymorphism).

        // super-classes and super-super-classes
        // (sometimes a method of a super-class is called by a method of its sub-class
        // or a method of a super-class is called via an instance of a sub-class)
        String superClass = superClassByClassMap.get(className);
        while (superClass != null && !JavaHelper.isJavaClass(superClass)) {
            addSuperMethodsOrItself(superClass, name, desc, polymorphicMethods);
            superClass = superClassByClassMap.get(superClass);
        }
        // sub-classes and sub-sub-classes
        // (sometimes a method defined and called on a super-class is overrided in a sub-class)
        for (final String subClass : getAllSubClasses(className)) {
            addSuperMethodsOrItself(subClass, name, desc, polymorphicMethods);

            // sometimes a method of a super-class is called via an interface of a sub-class
            String superClass2 = superClassByClassMap.get(subClass);
            while (superClass2 != null && !superClass2.equals(className)
                    && !JavaHelper.isJavaClass(superClass2)) {
                addSuperMethodsOrItself(superClass2, name, desc, polymorphicMethods);
                superClass2 = superClassByClassMap.get(superClass2);
            }
        }
        return polymorphicMethods;
    }

    private void addSuperMethodsOrItself(String className, String name, String desc,
            Set<String> output) {
        if (superHierarchy != null) {
            output.addAll(superHierarchy.getPolymorphicMethods(className, name, desc));
        } else {
            output.add(getMethodKey(className, name, desc));
        }
    }

    private static String getMethodKey(String className, String name, String desc) {
        return Indexer.getMethodKey(className, name, desc);
    }
}
