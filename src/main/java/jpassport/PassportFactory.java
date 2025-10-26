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


import jpassport.annotations.NativeLibrary;
import jpassport.annotations.NativeName;
import jpassport.annotations.NotRequired;
import jpassport.annotations.Critical;
import jpassport.codebuilder.ArgClassification;
import jpassport.codebuilder.CBConstants;
import jpassport.codebuilder.PassportBuilder;
import jpassport.pointers.FunctionPtr;
import jpassport.pointers.NamedLookup;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * This is the main entry point for connecting your interface to native code.
 *
 *
 */
public class PassportFactory
{
    /** This map is used to hold the method handles classes need as they are starting.
     * It's part of the mechanism that allows method handles to be static and final
     */
    private static final HashMap<Class<? extends Passport>, HashMap<String, MethodHandle>> mappedHandles = new HashMap<>();

    /**
     * Call this method to generate the library linkage. This version of the method will write the java file and compile
     * it. As a result, the start-up is significantly slower that {@link #link(String, Class) link()}, but the code
     * can be written to disk for later optimization. To write the code to disk use:
     * System.setProperty("jpassport.build.home", [location]);
     *
     * @param libraryName The library name (the file name of the shared library without extension on all platforms,
     *                    without lib prefix on Linux and Mac). Use null to load system method calls (ex. malloc)
     * @param interfaceClass The class to wrap.
     * @param <T> Any interface that extends Passport
     * @return A class linked to call into a DLL or SO using the Foreign Linker.
     */
    public synchronized static <T extends Passport> T link_written(String libraryName, Class<T> interfaceClass) throws Throwable
    {
        return link_written(libraryName, interfaceClass, false);
    }

    public synchronized static <T extends Passport> T link_written(String libraryName, Class<T> interfaceClass, boolean withDebug) throws Throwable
    {
        if (!Passport.class.isAssignableFrom(interfaceClass)) {
            throw new IllegalArgumentException(
                    String.format("Interface (%s) of library=%s does not extend %s",
                            interfaceClass.getSimpleName(), libraryName, Passport.class.getSimpleName()));
        } else {
            return writeClass(libraryName, interfaceClass, withDebug);
        }
    }

    /**
     * Call this method to generate the library linkage. This will return a dynamically generated implementation
     * of interfaceClass. The returned class is created using the Class file API, so it only exists in memory.
     * This call creates the class significantly faster than
     * {@link #link_written(String, Class)}. There should be little performance difference between the two
     * returned classes.
     *
     * @param libraryName The library name (the file name of the shared library without extension on all platforms,
     *                    without lib prefix on Linux and Mac). Use null to load system method calls (ex. malloc)
     * @param interfaceClass The class to wrap.
     * @param <T> Any interface that extends Passport
     * @return A class linked to call into a DLL or SO using the Foreign Linker.
     */
    public synchronized static <T extends Passport> T link(String libraryName, Class<T> interfaceClass) throws Throwable
    {
        return link(libraryName, interfaceClass, false);
    }

    public synchronized static <T extends Passport> T link(String libraryName, Class<T> interfaceClass, boolean withDebug) throws Throwable
    {
        if (!Passport.class.isAssignableFrom(interfaceClass)) {
            throw new IllegalArgumentException(
                    String.format("Interface (%s) of library=%s does not extend %s",
                            interfaceClass.getSimpleName(), libraryName, Passport.class.getSimpleName()));
        } else {
            return buildClass(libraryName, interfaceClass, withDebug);
        }
    }

    /**
     * This method should not be called. It is only used internally while the classes are being built
     */
    public static MethodHandle getHandle(Class<? extends Passport> interfaceClass, Class<? extends Passport> implClass, String name)
    {
        if (!mappedHandles.containsKey(interfaceClass) && implClass != null)
        {
            var annotation = implClass.getAnnotation(NativeLibrary.class);
            if (annotation == null)
                throw new PassportException("Prebuild classes must contain the NativeLibrary annotation");

            loadMethodHandles((annotation).name(), interfaceClass);
        }

        if (!mappedHandles.get(interfaceClass).containsKey(name))
            return null;

        var ret = mappedHandles.get(interfaceClass).remove(name);
        if (mappedHandles.get(interfaceClass).isEmpty())
            mappedHandles.remove(interfaceClass);
        return ret;
    }

    private static <T extends Passport> T writeClass(String libName, Class<T> interfaceClass, boolean withDebug) throws Throwable
    {
        HashMap<String, MethodHandle> handles = loadMethodHandles(libName, interfaceClass);
        PassportWriter<T> classWriter = new PassportWriter<>(interfaceClass, libName, withDebug);

        return classWriter.build(handles);
    }

    private static <T extends Passport> T buildClass(String libName, Class<T> interfaceClass, boolean withDebug) throws Throwable {
        HashMap<String, MethodHandle> handles = loadMethodHandles(libName, interfaceClass);
        PassportBuilder<T> classWriter = new PassportBuilder<>(interfaceClass, withDebug);

        return classWriter.build(handles);
    }

        /**
         * This method looks up the methods in the requested native library that match non-static
         * methods in the given interface class.
         *
         * @param libName Name of the native library to load, or null if the methods will be system method (ex. malloc).
         * @param interfaceClass The interface class to use as a reference for loading methods.
         * @return A map of Name to method handle pairs for the methods in the interface class.
         */
    public static HashMap<String, MethodHandle> loadMethodHandles(String libName, Class<? extends Passport> interfaceClass)
    {
        if (libName != null) {
            if (Utils.getPlatform().equals(Utils.Platform.Windows) && !libName.endsWith(".dll"))
                libName = libName + ".dll";

            File libPath = new File(libName);
            System.load(libPath.getAbsolutePath());
        }
        Linker cLinker = Linker.nativeLinker();

        List<Method> interfaceMethods = getDeclaredMethods(interfaceClass);
        HashMap<String, MethodHandle> methodMap = new HashMap<>();

        mappedHandles.put(interfaceClass, methodMap);

        //if no library name is given then it must be a system library
        SymbolLookup lookup = libName == null ? cLinker.defaultLookup() : SymbolLookup.loaderLookup();

        for (Method method : interfaceMethods) {
            Class<?> retType = method.getReturnType();
            Class<?>[] parameters = method.getParameterTypes();
            boolean hasErrorCapture = parameters.length > 0 && parameters[0].equals(ErrorCapture.class);

            if (!hasErrorCapture && Arrays.asList(parameters).contains(ErrorCapture.class))
                throw new PassportException("ErrorCapture must be the first argument of the method: " + method.getName());

            for (int n = 1; n < parameters.length; ++n)
            {
                if (parameters[n].equals(ErrorCapture.class))
                    throw new PassportException("ErrorCapture must be the first argument of an interface function: " + method.getName());
            }

            for (int n = 0; n < parameters.length; ++n) {
                if (!(parameters[n].isPrimitive() || parameters[n].isEnum()) && !isSpecialClass(parameters[n]))
                    parameters[n] = MemorySegment.class;
            }

            MemoryLayout[] memoryLayout = Arrays.stream(parameters)
                    .filter(p -> !isSpecialClass(p)).
                    map(PassportFactory::classToMemory).toArray(MemoryLayout[]::new);

            FunctionDescriptor fd;
            if (void.class.equals(retType))
                fd = FunctionDescriptor.ofVoid(memoryLayout);
            else
                fd = FunctionDescriptor.of(classToMemory(retType), memoryLayout);

            String nativeName = method.getName();
            if (method.isAnnotationPresent(NativeName.class))
            {
                var nn = method.getAnnotation(NativeName.class);
                nativeName = nn.name();
            }

            var addr = lookup.find(nativeName).orElse(null);
            if (addr == null && method.getAnnotation(NotRequired.class) == null)
                throw new PassportException("Could not find method in library: " + method.getName());


            if (addr != null) {

                MethodHandle methodHandle;

                List<Linker.Option> options = new ArrayList<>();
                if (method.getAnnotation(Critical.class) != null)
                    options.add(Linker.Option.critical(true));

                if (hasErrorCapture)
                    options.add(Linker.Option.captureCallState(ErrorCapture.getErrNames()));

                Linker.Option[] linkeroptions = options.toArray(new Linker.Option[0]);
                if (linkeroptions.length == 0)
                    methodHandle = cLinker.downcallHandle(addr, fd);
                else
                    methodHandle = cLinker.downcallHandle(addr, fd, linkeroptions);

                methodMap.put(method.getName(), methodHandle);
            }
        }
        loadNames(interfaceClass);

        //methodMap is emptied by the class when it is created.
        return new HashMap<>(methodMap);
    }

    /**
     * Special classes in this contect refers to method arguments that actually should not get
     * pushed to the native method (Arena and ErrorCapture)
     * @param c The Class to check.
     * @return True if the given class type will be passed to the native method.
     */
    private static boolean isSpecialClass(Class<?> c)
    {
        return Arena.class.equals(c) || ErrorCapture.class.equals(c);
    }

    /**
     * This method looks up {@link NamedLookup} fields in the native library and sets their
     * values to the appropriate address in the library.
     *
     * @param interfaceClass The interface class to use as a reference for loading methods.
     */
    private static void loadNames(Class<? extends Passport> interfaceClass)
    {
        List<Field> names = getDeclaredNames(interfaceClass);

        SymbolLookup lookup = SymbolLookup.loaderLookup();

        for (Field field : names) {
            try {
                var named = ((NamedLookup)field.get(interfaceClass));
                var addr = lookup.find(named.name());
                if (addr.isEmpty() && field.getAnnotation(NotRequired.class) == null)
                    throw new PassportException("Could not find field in library: " + named.name());
                else if (addr.isEmpty())
                    named.setAddress(MemorySegment.NULL);
                addr.ifPresent(named::setAddress);
            } catch (IllegalAccessException e) {
                throw new PassportException("Could not find field in library: " + field.getName());
            }
        }
    }

    /**
     * Given an object and method name this will return a memory address that
     * corresponds to a method pointer that can be passed to native code.
     * The method ob.methodName() cannot be static.
     *
     * @param ob The object that the method belongs to.
     * @param methodName The name of the method.
     * @return A pointer to a method handle that can be passed to native code. When the native code calls the function
     * pointer, ob.methodName() will be called.
     * @throws IllegalArgumentException if there is no method with the given name, or there is more
     * than 1 method with the given name.
     */
    public static FunctionPtr createCallback(Object ob, String methodName)
    {
        var methods = getDeclaredMethods(ob.getClass());

        methods = methods.stream().filter(m -> m.getName().equals(methodName)).toList();
        if (methods.isEmpty())
            throw new IllegalArgumentException("Could not find method " + methodName + " in class " + ob.getClass().getName());
        else if (methods.size() > 1)
            throw new IllegalArgumentException("Multiple overloads of method " + methodName + " in class " + ob.getClass().getName());

        Method callbackMethod = methods.getFirst();

        Class<?> retType = callbackMethod.getReturnType();
        Class<?>[] parameters = callbackMethod.getParameterTypes();

        if (!retType.isPrimitive() && retType != MemorySegment.class)
            throw new IllegalArgumentException("Callback method must return void, primitives, or MemoryAddress, not " + retType.getName());


        for (Class<?> parameter : parameters) {
            if (!parameter.isPrimitive() && parameter != MemorySegment.class)
                throw new IllegalArgumentException("Callback parameters must be primitives or MemoryAddress, not " + parameter.getName());
        }

        MemoryLayout[] memoryLayout = Arrays.stream(parameters).map(PassportFactory::classToMemory).toArray(MemoryLayout[]::new);
        FunctionDescriptor fd;
        if (void.class.equals(retType))
            fd = FunctionDescriptor.ofVoid(memoryLayout);
        else
            fd = FunctionDescriptor.of(classToMemory(retType), memoryLayout);

        try {
            var handle = MethodHandles.publicLookup().findVirtual(ob.getClass(), methodName, MethodType.methodType(retType, parameters));
            var handleToCall = handle.bindTo(ob);

            var scope = Arena.ofAuto();
            return new FunctionPtr(scope, Linker.nativeLinker().upcallStub(handleToCall, fd, scope));
        }
        catch (NoSuchMethodException | IllegalAccessException ex)
        {
            throw new Error("Failed to create callback method", ex);
        }
    }

    public static List<Method> getDeclaredMethods(Class<?> interfaceClass) {
        Method[] methods = interfaceClass.getDeclaredMethods();
        List<Method> allMethods = new ArrayList<>(Arrays.asList(methods));

        // Support multiple inheritance of other interfaces that extend Passport
        var ifc = interfaceClass.getInterfaces();
        for (var parent : ifc)
        {
            //recursively find all methods we need to implement
            if (parent.isInterface() && isPassport(parent))
                allMethods.addAll(getDeclaredMethods(parent));
        }

        return allMethods.stream().
                filter(method -> !Modifier.isStatic(method.getModifiers())).
                filter(method -> !method.isDefault()).toList();
    }

    private static boolean isPassport(Class<?> c)
    {
        return Arrays.asList(c.getInterfaces()).contains(Passport.class);
    }

    static List<Field> getDeclaredNames(Class<?> interfaceClass) {
        Field[] fields = interfaceClass.getDeclaredFields();
        return Arrays.stream(fields).
                filter(field -> field.getType().equals(NamedLookup.class)).toList();
    }


    private static MemoryLayout classToMemory(Class<?> type)
    {
        if (double.class.equals(type))
            return ValueLayout.JAVA_DOUBLE;
        if (int.class.equals(type))
            return ValueLayout.JAVA_INT;
        if (float.class.equals(type))
            return ValueLayout.JAVA_FLOAT;
        if (short.class.equals(type))
            return ValueLayout.JAVA_SHORT;
        if (byte.class.equals(type))
            return ValueLayout.JAVA_BYTE;
        if (long.class.equals(type))
            return ValueLayout.JAVA_LONG;
        if (boolean.class.equals(type))
            return ValueLayout.JAVA_BOOLEAN;
        if (char.class.equals(type))
            return ValueLayout.JAVA_CHAR;

        var ctype = ArgClassification.classify(type);
        if (ctype == ArgClassification.enum_long)
            return ValueLayout.JAVA_LONG;
        else if (ctype == ArgClassification.enum_int || ctype == ArgClassification.enum_ordinal)
            return ValueLayout.JAVA_INT;

        return ValueLayout.ADDRESS;
    }

}
