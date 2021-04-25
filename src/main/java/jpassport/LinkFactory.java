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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LinkFactory
{
    private static int Class_ID = 1;

    /**
     * Call this method to generate the library linkage.
     *
     * @param libraryName The name of the dll or SO file (no extension).
     * @param interfaceClass The class to wrap.
     * @param <T>
     * @return A class linked to call into a DLL or SO using the Foreign Linker.
     */
    public synchronized static <T extends Foreign> T link(String libraryName, Class<T> interfaceClass) throws Throwable
    {
        if (!Foreign.class.isAssignableFrom(interfaceClass)) {
            throw new IllegalArgumentException("Interface (" + interfaceClass.getSimpleName() + ") of library=" + libraryName + " does not extend " + Foreign.class.getSimpleName());
        } else {
            return buildClass(libraryName, interfaceClass);
        }
    }

    private static <T extends Foreign> T buildClass(String libName, Class<T> interfaceClass) throws Throwable
    {
        LibraryLookup libLookup = LibraryLookup.ofLibrary(libName);
        Method[] methods = interfaceClass.getDeclaredMethods();
        ClassWriter classWriter = new ClassWriter(interfaceClass);
        Map<String, MethodHandle> methodMap = new HashMap<>();

        for (Method method : methods) {
            if ((method.getModifiers() & Modifier.STATIC) != 0)
                continue;

            LibraryLookup.Symbol symb = libLookup.lookup(method.getName()).orElse(null);
            if (symb == null)
                throw new IllegalArgumentException("Method not found in library: " + method.getName());

            Class retType = method.getReturnType();
            Class[] parameters = method.getParameterTypes();
            Class methRet = retType;
            if (methRet.isArray() || retType.equals(String.class)) {
                methRet= MemoryAddress.class;
            }

            for (int n = 0; n < parameters.length; ++n) {
                if (parameters[n].isArray() ||parameters[n].getSimpleName().equals("String")) {
                    parameters[n] = MemoryAddress.class;
                }
            }

            MemoryLayout[] memoryLayout = Arrays.stream(parameters).map(LinkFactory::classToMemory).toArray(MemoryLayout[]::new);

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
     * This class is to write out a java file that we will compile to access a foreign library
     * @param <T>
     */
    private static class ClassWriter<T extends Foreign>
    {
        StringBuilder m_source = new StringBuilder();
        StringBuilder m_moduleSource = new StringBuilder();
        StringBuilder m_initSource = new StringBuilder();
        String m_className;
        String m_fullClassName;
        int m_ID;

        ClassWriter(Class<T> interfaceClass)
        {
            m_ID = Class_ID;
            m_className = interfaceClass.getSimpleName() + "_impl";
            String packageName = "jpassport.called_" + m_ID;
            m_fullClassName = packageName + "." + m_className;

            m_source.append(String.format("""
                    package %s;

                    import %s;                                      
                    import jpassport.Utils;  
                    import java.lang.invoke.MethodHandle;
                    import jdk.incubator.foreign.*;
                    import java.util.HashMap;
                    
                    public class %s implements %s {
                        HashMap<String, MethodHandle> m_methods;
                        
                        public %s(HashMap<String, MethodHandle> methods)
                        {
                            m_methods = methods;
                            init();
                        }

                    """, packageName, interfaceClass.getName(), m_className, interfaceClass.getSimpleName(), m_className));

            m_initSource.append("""
                    private void init(){
                    """);

            m_moduleSource.append(String.format("""
                    module foreign.caller {
                        requires jdk.incubator.foreign;
                        requires jpassport;
                        requires %s;
                    }
                    """, interfaceClass.getModule().getName()));

        }

        public void addMethod(Method method, Class retType)
        {
            StringBuilder args = new StringBuilder();
            StringBuilder params = new StringBuilder();
            StringBuilder tryArgs = new StringBuilder();
            StringBuilder postCall = new StringBuilder();
            StringBuilder preCall = new StringBuilder();

            String strCallReturn = "";
            String strReturn = "";

            if (!void.class.equals(retType))
            {
                if (retType.equals(String.class))
                {
                    strCallReturn = "var ret = (MemoryAddress)";
                    strReturn = "return CLinker.toJavaStringRestricted(ret)";
                }
                else
                {
                    strCallReturn = String.format("var ret = (%s)", retType.getSimpleName());
                    strReturn = "return ret";
                }
            }

            Annotation[][] paramAnnotations = method.getParameterAnnotations();
            int v = 1;
            boolean bHasAllocatedMemory = false;

            for (Class parameter : method.getParameterTypes())
            {
                args.append(String.format("%s v%d,", parameter.getSimpleName(), v));

                if (parameter.isArray())
                {
                    bHasAllocatedMemory = true;
                    if (isPtrPtrArg(paramAnnotations[v-1]))
                        preCall.append(String.format("var vv%d = Utils.toPtrPTrMS(scope, v%d);", v, v));
                    else
                        preCall.append(String.format("var vv%d = Utils.toMS(scope, v%d);\n", v, v));

                    params.append("vv").append(v).append(".address(),");

                    if (isRefArg(paramAnnotations[v-1]))
                        postCall.append(String.format("Utils.toArr(v%d, vv%d);\n", v, v));
                }
                else if (parameter.getSimpleName().equals("String"))
                {
                    bHasAllocatedMemory = true;
                    tryArgs.append(String.format("var vv%d = CLinker.toCString(v%d);", v, v));
                    params.append("vv").append(v).append(".address(),");
                }
                else
                    params.append("v").append(v).append(",");
                ++v;
            }

            args.setLength(args.length() - 1);
            params.setLength(params.length() - 1);
            if (bHasAllocatedMemory)
                tryArgs.append("var scope = NativeScope.unboundedScope();");
            if (tryArgs.length() > 0)
                tryArgs.insert(0, "(").append(")");

            m_source.append(String.format("""
                                private MethodHandle m_%s;
                                public %s %s(%s)
                                {
                                    try %s {
                                        %s
                                        %s m_%s.invokeExact(%s);
                                        %s
                                        %s;
                                    } 
                                    catch(Throwable th)
                                    {
                                        throw new Error(th);
                                    }
                                }
                                
                            """,
                    method.getName(),
                    retType.getSimpleName(), method.getName(),args,
                    tryArgs,
                    preCall,
                    strCallReturn, method.getName(), params,
                    postCall,
                    strReturn));

            m_initSource.append(String.format("m_%s = m_methods.get(\"%s\");\n", method.getName(), method.getName()));
        }

        T build(Map<String, MethodHandle> methods) throws Throwable
        {
            m_initSource.append("}");
            m_source.append(m_initSource);
            m_source.append("\n}");

            Path buildRoot = Utils.getBuildFolder();
            Path sourceRoot = buildRoot.resolve("jpassport").resolve("called_" + m_ID);
            if (Files.exists(sourceRoot))
                Utils.deleteFolder(sourceRoot);
            Files.createDirectories(sourceRoot);
            Path sourceFile = sourceRoot.resolve(m_className + ".java");
            Files.writeString(sourceFile, m_source);
            Path moduleFile = buildRoot.resolve("module-info.java");
            Files.writeString(moduleFile, m_moduleSource);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            compiler.run(null, null, null,
                    "--module-path", System.getProperty("jdk.module.path"),
                    moduleFile.toString(), sourceFile.toString());

            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] {buildRoot.toUri().toURL()});
            Class<T> foreignImpl = (Class<T>) Class.forName(m_fullClassName, true, classLoader);
            return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
        }

        private boolean isRefArg(Annotation[] paramAnnotations)
        {
            return Arrays.stream(paramAnnotations).map(Annotation::annotationType).anyMatch(RefArg.class::equals);
        }

        private boolean isPtrPtrArg(Annotation[] paramAnnotations)
        {
            return Arrays.stream(paramAnnotations).map(Annotation::annotationType).anyMatch(PtrPtrArg.class::equals);
        }
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
