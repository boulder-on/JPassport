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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class PassportFactory
{
    /**
     * Call this method to generate the library linkage.
     *
     * @param libraryName The library name (the file name of the shared library without extension on all platforms,
     *                    without lib prefix on Linux and Mac).
     * @param interfaceClass The class to wrap.
     * @param <T> Any interface that extends Passport
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
        HashMap<String, MethodHandle> handles = loadMethodHandles(libName, interfaceClass);
        PassportWriter<T> classWriter = new PassportWriter<>(interfaceClass);

        return classWriter.build(handles);
    }

    /**
     * This methods looks up all of the methods in the requested native library that match non-static
     * methods in the given interface class.
     *
     * @param libName Name of the native library to load.
     * @param interfaceClass The interface class to use as a reference for loading methods.
     * @return A map of Name to method handle pairs for the methods in the interface class.
     */
    public static HashMap<String, MethodHandle> loadMethodHandles(String libName, Class<? extends Passport> interfaceClass)
    {
        LibraryLookup libLookup = LibraryLookup.ofLibrary(libName);
        CLinker linker = CLinker.getInstance();

        List<Method> interfaceMethods = getDeclaredMethods(interfaceClass);
        HashMap<String, MethodHandle> methodMap = new HashMap<>();

        for (Method method : interfaceMethods) {
            LibraryLookup.Symbol symb = libLookup.lookup(method.getName()).orElse(null);
            if (symb == null)
                throw new IllegalArgumentException("Method not found in library: " + method.getName());

            Class<?> retType = method.getReturnType();
            Class<?>[] parameters = method.getParameterTypes();
            Class<?> methRet = retType;

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

            MethodHandle methodHandle = linker.
                    downcallHandle(symb.address(),
                            MethodType.methodType(methRet, parameters),
                            fd);

            methodMap.put(method.getName(), methodHandle);
        }

        return methodMap;
    }

    static List<Method> getDeclaredMethods(Class<?> interfaceClass) {
        Method[] methods = interfaceClass.getDeclaredMethods();
        return Arrays.stream(methods).filter(method -> (method.getModifiers() & Modifier.STATIC) == 0).toList();
    }


    private static MemoryLayout classToMemory(Class<?> type)
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
