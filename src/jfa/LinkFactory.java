package jfa;



import jdk.incubator.foreign.*;
import jfa.annotations.PtrPtrArg;
import jfa.annotations.RefArg;

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
    public static <T extends Foreign> T link(String libraryName, Class<T> interfaceClass) throws Throwable
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

            System.out.println("Building: " + method.getName());

            LibraryLookup.Symbol symb = libLookup.lookup(method.getName()).orElse(null);
            if (symb == null)
                throw new IllegalArgumentException("Method not found in library: " + method.getName());

            Class retType = method.getReturnType();
            Class[] parameters = method.getParameterTypes();
            Class methRet = retType;
            if (methRet.isArray()) {
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

            classWriter.addMethod(method, retType, memoryLayout);

            methodMap.put(method.getName(), methodHandle);
        }

        return (T)classWriter.build(methodMap);
    }


    private static class ClassWriter<T extends Foreign>
    {
        StringBuilder m_source = new StringBuilder();
        StringBuilder m_moduleSource = new StringBuilder();
        String m_className;
        String m_fullClassName;

        ClassWriter(Class<T> interfaceClass)
        {
            m_className = interfaceClass.getSimpleName() + "_impl";
            String packageName = "jfa.called";
            m_fullClassName = packageName + "." + m_className;

            m_source.append(String.format("""
                    package %s;

                    import %s;                                      
                    import jfa.Utils;  
                    import java.lang.invoke.MethodHandle;
                    import jdk.incubator.foreign.*;
                    import java.util.HashMap;
                    
                    public class %s implements %s {
                        HashMap<String, MethodHandle> m_methods;
                        
                        public %s(HashMap<String, MethodHandle> methods)
                        {
                            m_methods = methods;
                        }

                    """, packageName, interfaceClass.getName(), m_className, interfaceClass.getSimpleName(), m_className));

            m_moduleSource.append(String.format("""
                    module foreign.caller {
                        requires jdk.incubator.foreign;
                        requires passport;
                        requires %s;
                    }
                    """, interfaceClass.getModule().getName()));

        }

        public void addMethod(Method method, Class retType, MemoryLayout[] memoryLayout)
        {
            int v = 1;
            StringBuilder args = new StringBuilder();
            StringBuilder params = new StringBuilder();
            StringBuilder tryArgs = new StringBuilder();
            StringBuilder readReferences = new StringBuilder();
            String strCallReturn = "";
            String strReturn = "";

            if (!void.class.equals(retType))
            {
                strCallReturn = String.format("var ret = (%s)", retType.getSimpleName());
                strReturn = "return ret";
            }

            Annotation[][] paramAnnotations = method.getParameterAnnotations();

            for (Class parameter : method.getParameterTypes())
            {
                args.append(String.format("%s v%d,", parameter.getSimpleName(), v));

                if (parameter.isArray())
                {
                    if (isPtrPtrArg(paramAnnotations[v-1]))
                        tryArgs.append(String.format("var vv%d = Utils.toPtrPTrMS(v%d);", v, v));
                    else
                        tryArgs.append(String.format("var vv%d = Utils.toMS(v%d);", v, v));

                    params.append("vv").append(v).append(".address(),");

                    if (isRefArg(paramAnnotations[v-1]))
                        readReferences.append(String.format("Utils.toArr(v%d, vv%d);\n", v, v));
                }
                else if (parameter.getSimpleName().equals("String"))
                    params.append(String.format("CLinker.toCString(v%d).address(),", v));
                else
                    params.append("v").append(v).append(",");
                ++v;
            }

            args.setLength(args.length() - 1);
            params.setLength(params.length() - 1);
            if (tryArgs.length() > 0)
                tryArgs.insert(0, "(").append(")");

            m_source.append(String.format("""
                                public %s %s(%s)
                                {
                                    try %s {
                                        %s m_methods.get("%s").invokeExact(%s);
                                        %s
                                        %s;
                                    } 
                                    catch(Throwable th)
                                    {
                                        throw new Error(th);
                                    }
                                }
                                
                            """,  retType.getSimpleName(), method.getName(),args,
                    tryArgs,
                    strCallReturn, method.getName(), params,
                    readReferences,
                    strReturn));
        }

        T build(Map<String, MethodHandle> methods) throws Throwable
        {
            m_source.append("\n}");

            Path buildRoot = Path.of(System.getProperty("java.io.tmpdir"), "jfa");
            Path sourceRoot = buildRoot.resolve("jfa").resolve("called");
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
