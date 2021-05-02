/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
package jpassport;

import jdk.incubator.foreign.*;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.RefArg;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PassportFactory
{
    private static int Class_ID = 1;

    /**
     * Call this method to generate the library linkage.
     *
     * @param libraryName The library name (the file name of the shared library without extension on all platforms,
     *                    without lib prefix on Linux and Mac).
     * @param interfaceClass The class to wrap.
     * @param <T>
     * @return A class linked to call into a DLL or SO using the Foreign Linker.
     */
    public synchronized static <T extends Passport> T link(String libraryName, Class<T> interfaceClass) throws Throwable
    {
        if (!Passport.class.isAssignableFrom(interfaceClass)) {
            throw new IllegalArgumentException("Interface (" + interfaceClass.getSimpleName() + ") of library=" + libraryName + " does not extend " + Passport.class.getSimpleName());
        } else {
            return buildClass(libraryName, interfaceClass);
        }
    }

    private static <T extends Passport> T buildClass(String libName, Class<T> interfaceClass) throws Throwable
    {
        LibraryLookup libLookup = LibraryLookup.ofLibrary(libName);
        Method[] methods = interfaceClass.getDeclaredMethods();

        List<Method> interfaceMethods = Arrays.stream(methods).filter(method -> (method.getModifiers() & Modifier.STATIC) == 0).toList();
        Set<Class> extraImports = findAllExtraImports(interfaceMethods);
        ClassWriter classWriter = new ClassWriter(interfaceClass, extraImports);
        Map<String, MethodHandle> methodMap = new HashMap<>();

        for (Method method : interfaceMethods) {
            LibraryLookup.Symbol symb = libLookup.lookup(method.getName()).orElse(null);
            if (symb == null)
                throw new IllegalArgumentException("Method not found in library: " + method.getName());

            Class retType = method.getReturnType();
            Class[] parameters = method.getParameterTypes();
            Class methRet = retType;

            if (!methRet.isPrimitive())
                methRet= MemoryAddress.class;

            for (int n = 0; n < parameters.length; ++n) {
                if (!parameters[n].isPrimitive())
                    parameters[n] = MemoryAddress.class;
            }

            MemoryLayout[] memoryLayout = Arrays.stream(parameters).map(PassportFactory::classToMemory).toArray(MemoryLayout[]::new);

            FunctionDescriptor fd;
            if (void.class.equals(retType))
                fd = FunctionDescriptor.ofVoid(memoryLayout);
            else
                fd = FunctionDescriptor.of(classToMemory(retType), memoryLayout);

            MethodHandle methodHandle = CLinker.getInstance().
                    downcallHandle(symb.address(),
                            MethodType.methodType(methRet, parameters),
                            fd);

            classWriter.addMethod(method, retType);

            methodMap.put(method.getName(), methodHandle);
        }

        return (T)classWriter.build(methodMap);
    }

    /**
     * This will search the interface method for return types and arguments that should be imported.
     * These will all be Records.
     *
     * @param interfaceMethods All of the methods in the interfacee
     * @return The list of Record types that should be imported.
     */
    private static Set<Class> findAllExtraImports(List<Method> interfaceMethods) {
        Set<Class> extraImports = new HashSet<>();
        for (Method m : interfaceMethods) {
            Class retType = m.getReturnType();
            Class[] params = m.getParameterTypes();

            if (!isValidArgType(retType))
                throw new PassportException("Types in the interface must by primitive, arrays of primitives, String, or Records. " + retType.getSimpleName() + " not supported.");

            List<Class> invalid = Arrays.stream(params).filter(p -> !isValidArgType(p)).collect(Collectors.toList());
            if (!invalid.isEmpty())
                throw new PassportException("Types in the interface must by primitive, arrays of primitives, String, or Records. " + invalid.get(0).getSimpleName() + " not supported.");

            if (retType.isRecord() || (retType.isArray() && retType.getComponentType().isRecord()))
                extraImports.add(retType);
            Arrays.stream(params).filter(Class::isRecord).forEach(extraImports::add);
            Arrays.stream(params).filter(Class::isArray).map(Class::getComponentType).filter(Class::isRecord).forEach(extraImports::add);
        }

        extraImports.remove(String.class);
        extraImports.remove(MemoryAddress.class);
        //In case any of the Records are made up of Records then this will pick those up to
        for (Class c : extraImports)
        {
            if (c.isRecord())
                extraImports.addAll(findSubRecords(c));
        }

        return extraImports;
    }

    /**
     * Search all Record types recursively to make sure we import and handle all Record types needed.
     * @param record A record class to search for other records
     * @return All of the sub-Records.
     */
    static Set findSubRecords(Class record)
    {
        Set<Class> subRecords = new HashSet<>();
        for (Field f : record.getDeclaredFields()) {
            if (f.getType().isRecord())
            {
                subRecords.add(f.getType());
                subRecords.addAll(findSubRecords(f.getType()));
            }
        }
        return subRecords;
    }

    /**
     * At the moment the only argument types that are supported are:
     * Primitive
     * Primitive[]
     * Primitive[][]
     * Record
     * String
     * MemoryAddress
     *
     * @param c The type to check
     * @return Is the type something we can work with
     */
    private static boolean isValidArgType(Class c)
    {
        if (c.isPrimitive())
            return true;
        if (c.isRecord())
            return true;
        if (c.isArray() && (c.componentType().isPrimitive() || c.getComponentType().isRecord()))
            return true;
        if (MemoryAddress.class.equals(c) || String.class.equals(c))
            return true;
        return c.isArray() && c.getComponentType().isArray() && c.getComponentType().getComponentType().isPrimitive();
    }

    private static MemoryLayout classToMemory(Class type)
    {
        if (double.class.equals(type))
            return CLinker.C_DOUBLE;
        if (int.class.equals(type))
            return CLinker.C_INT;
        if (float.class.equals(type))
            return CLinker.C_FLOAT;
        if (short.class.equals(type))
            return CLinker.C_SHORT;
        if (byte.class.equals(type))
            return CLinker.C_CHAR;
        if (long.class.equals(type))
            return CLinker.C_LONG_LONG;

        return CLinker.C_POINTER;
    }
}
