package jpassport;

import jpassport.annotations.Ptr;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.RefArg;
import jpassport.annotations.StructPadding;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static jpassport.Utils.Platform.Windows;

public class ClassWriter <T extends Passport>
{
    private final StringBuilder m_source = new StringBuilder();
    private final StringBuilder m_moduleSource = new StringBuilder();
    private final StringBuilder m_initSource = new StringBuilder();
    private final String m_className;
    private final String m_fullClassName;
    private final int m_ID;

    private static final Map<Class, String> typeToName = new HashMap<>()
    {
        {
            put(byte.class, "Byte");
            put(short.class, "Short");
            put(int.class, "Int");
            put(long.class, "Long");
            put(float.class, "Float");
            put(double.class, "Double");
        }
    };

    private static int Class_ID = 1;

    ClassWriter(Class<T> interfaceClass, Set<Class> extraImports)
    {
        m_ID = Class_ID++;
        m_className = interfaceClass.getSimpleName() + "_impl";
        String packageName = "jpassport.called_" + m_ID;
        m_fullClassName = packageName + "." + m_className;
        String structLayouts = buildStructLayouts(extraImports);

        m_source.append(String.format("""
                    package %s;

                    %s
                    import %s;
                    import jpassport.Utils;
                    import java.lang.invoke.MethodHandle;
                    import jdk.incubator.foreign.*;
                    import jdk.incubator.foreign.MemoryLayout.PathElement;
                    import java.util.HashMap;
                    
                    public class %s implements %s {
                        HashMap<String, MethodHandle> m_methods;
                        %s
                        
                        public %s(HashMap<String, MethodHandle> methods)
                        {
                            m_methods = methods;
                            init();
                        }

                    """,
                packageName,
                buildExtraImports(extraImports),
                interfaceClass.getName(),
                m_className, interfaceClass.getSimpleName(),
                structLayouts,
                m_className));

        m_source.append(buildStructConverter(extraImports));
        m_source.append(buildStructReader(extraImports));

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

    /**
     * Any extra Records that we want to work with must be imported into the class.
     * @param imports All of the record types.
     * @return The import statements for the records.
     */
    public String buildExtraImports(Set<Class> imports)
    {
        StringBuilder strImports = new StringBuilder();
        for (Class c : imports)
            strImports.append("import ").append(c.getName()).append(";\n");
        return strImports.toString();
    }

    /**
     * This code will build all of the code required to convert a Record object into a MemoryLayout.
     * A MemoryLayout is the Java wrapper around a C struct.
     *
     * @param records All of the record types that we need to handle.
     * @return The code to create all of the required MemoryLayouts
     */
    public String buildStructLayouts(Set<Class> records)
    {
        //We need to build all of the MemoryLayouts separately and bundle them together later.
        //If any one record requires other records then we need to make sure the code is ordered
        //properly so there are no forward references.
        HashMap<Class, List<Class>> requiresMap = new HashMap<>();
        HashMap<Class, StringBuilder> layoutMap = new HashMap<>();

        for (Class c : records)
        {
            if (!c.isRecord())
                continue;

            if (!requiresMap.containsKey(c))
                requiresMap.put(c, new ArrayList<>());

            StringBuilder sb = new StringBuilder();
            layoutMap.put(c, sb);

            sb.append(String.format("private static final GroupLayout %sLayout = MemoryLayout.ofStruct(\n", c.getSimpleName()));

            for (Field f : c.getDeclaredFields())
            {
                int paddingBits = getPaddingBits(f);

                // negative indicates pre-padding
                if (paddingBits < 0)
                    sb.append(String.format("\t\tMemoryLayout.ofPaddingBits(%d),\n", -paddingBits));

                Class type = f.getType();
                if (type.isPrimitive())
                {
                    if (int.class.equals(type))
                        sb.append(String.format("\t\tCLinker.C_INT.withName(\"%s\"),\n", f.getName()));
                    else if (long.class.equals(type))
                        sb.append(String.format("\t\tCLinker.C_LONG_LONG.withName(\"%s\"),\n", f.getName()));
                    else if (float.class.equals(type))
                        sb.append(String.format("\t\tCLinker.C_FLOAT.withName(\"%s\"),\n", f.getName()));
                    else if (double.class.equals(type))
                        sb.append(String.format("\t\tCLinker.C_DOUBLE.withName(\"%s\"),\n", f.getName()));
                }
                else if (type.isRecord())
                {
                    requiresMap.get(c).add(type);
                    boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPtr)
                        sb.append(String.format("\t\tCLinker.C_POINTER.withName(\"%s\"),\n", f.getName()));
                    else
                        sb.append(String.format("\t\t%sLayout.withName(\"%s\"),\n",type.getSimpleName(), f.getName()));
                }
                else if (String.class.equals(type))
                    sb.append(String.format("\t\tCLinker.C_POINTER.withName(\"%s\"),\n", f.getName()));

                if (paddingBits > 0)
                    sb.append(String.format("\t\tMemoryLayout.ofPaddingBits(%d),\n", paddingBits));
            }
            sb.setLength(sb.length() - 2);
            sb.append(");\n\n");
        }

        StringBuilder allStructs = new StringBuilder();
        // This code attempts to only write out Records that have all Records they depend on written out first.
        //Without this code we could get compile failures because of forward references.
        while (!requiresMap.isEmpty())
        {
            List<Class> allRecords = new ArrayList<>();
            allRecords.addAll(requiresMap.keySet());
            for (Class c : allRecords)
            {
                List<Class> required = requiresMap.get(c);
                if (required.stream().anyMatch(layoutMap::containsKey))
                    continue;

                allStructs.append(layoutMap.remove(c));
                requiresMap.remove(c);
            }
        }

        return allStructs.toString();
    }

    private int getPaddingBits(Field field)
    {
        Annotation[] annotations = field.getAnnotationsByType(StructPadding.class);
        int paddingBytes = 0;

        if (annotations.length > 0)
        {
            StructPadding sp = ((StructPadding) annotations[0]);
            paddingBytes = sp.bytes();

            Utils.Platform p = Utils.getPlatform();
            if (Windows.equals(p) && sp.windowsBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.windowsBytes();
            else if (Utils.Platform.Mac.equals(p) && sp.macBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.macBytes();
            else if (Utils.Platform.Linux.equals(p) && sp.linuxBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.linuxBytes();
        }

        return paddingBytes * 8;
    }

    /**
     * This code is used to write the code that converts Record classes into MemorySegments that can be passed
     * into methods.
     *
     * @param records all of the Record types we need to support.
     * @return The code that converts Records into MemorySegments
     */
    public String buildStructConverter(Set<Class> records)
    {
        StringBuilder sb = new StringBuilder();

        for (Class c : records)
        {
            if (!c.isRecord())
                continue;

            sb.append(String.format("""
                        private MemorySegment store%1$s(NativeScope scope, %1$s rec) {
                            GroupLayout layout = %1$sLayout;
                            MemorySegment memStruct = scope.allocate(layout.byteSize());
                            
                    """,
                    c.getSimpleName()));


            for (Field f : c.getDeclaredFields())
            {
                Class type = f.getType();
                if (type.isPrimitive())
                    sb.append(String.format("\t\tMemoryAccess.set%2$sAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\")), rec.%1$s());\n", f.getName(), typeToName.get(type)));
                else if (type.isRecord())
                {
                    boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPtr)
                        sb.append(String.format("\t\tMemoryAccess.setAddressAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\")), store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName()));
                    else
                        sb.append(String.format("\t\tmemStruct.asSlice(layout.byteOffset(MemoryLayout.PathElement.groupElement(\"%1$s\"))).copyFrom(store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName()));
                }
                else if (String.class.equals(type))
                    sb.append(String.format("\t\tMemoryAccess.setAddressAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\")), CLinker.toCString(rec.%1$s(), scope).address());\n", f.getName()));
            }
            sb.append("\n\t\treturn memStruct;\n\t}\n\n");
        }

        return sb.toString();
    }

    /**
     * If there is a Record class that needs to be read in then this method writes the code to convert the MemorySegment
     * back into a Record
     * @param records All of the record types to make readers for.
     * @return The code to read all of the Record types.
     */
    private String buildStructReader(Set<Class> records)
    {
        StringBuilder sb = new StringBuilder();

        for (Class c : records)
        {
            if (!c.isRecord())
                continue;

            sb.append(String.format("""
                        private %1$s read%1$s(MemorySegment memStruct) {
                            GroupLayout layout = %1$sLayout;
                    """,
                    c.getSimpleName()));


            for (Field f : c.getDeclaredFields())
            {
                Class type = f.getType();
                if (type.isPrimitive())
                    sb.append(String.format("\t\tvar %1$s = MemoryAccess.get%2$sAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\")));\n", f.getName(), typeToName.get(type)));
                else if (type.isRecord())
                {
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPointer)
                        sb.append(String.format("\t\tvar %1$s = read%2$s(MemoryAccess.getAddressAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\"))).asSegmentRestricted(%2$sLayout.byteSize()));\n", f.getName(), type.getSimpleName()));
                    else
                        sb.append(String.format("\t\tvar %1$s = read%2$s(memStruct.asSlice(layout.byteOffset(MemoryLayout.PathElement.groupElement(\"%1$s\"))));\n", f.getName(), type.getSimpleName()));
                }
                else if (String.class.equals(type))
                    sb.append(String.format("\t\tvar %1$s = CLinker.toJavaStringRestricted(MemoryAccess.getAddressAtOffset(memStruct, layout.byteOffset(PathElement.groupElement(\"%1$s\"))));\n", f.getName()));
            }

            // Building the Record class to return
            sb.append(String.format("\t\treturn new %s(", c.getSimpleName()));
            for (Field f : c.getDeclaredFields()) {
                sb.append(f.getName()).append(",");
            }
            sb.setLength(sb.length() - 1);
            sb.append(");\n\t}\n\n");
        }

        return sb.toString();
    }

    /**
     * This method is used to create the code to support a single interface method.
     * @param method The interface method to implement
     * @param retType The return type of the method.
     */
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
                strReturn = "return CLinker.toJavaStringRestricted(ret);";
            }
            else
            {
                strCallReturn = String.format("var ret = (%s)", retType.getSimpleName());
                strReturn = "return ret;";
            }
        }

        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        int v = 1;
        boolean bHasAllocatedMemory = false;

        for (Class parameter : method.getParameterTypes())
        {
            args.append(String.format("%s v%d,", parameter.getSimpleName(), v));

            if (isArrayOfPrimitives(parameter) || is2DArrayOfPrimitives(parameter))
            {
                bHasAllocatedMemory = true;
                if (isPtrPtrArg(paramAnnotations[v-1]))
                    preCall.append(String.format("var vv%1$d = Utils.toPtrPTrMS(scope, v%1$d);", v));
                else
                    preCall.append(String.format("var vv%1$d = Utils.toMS(scope, v%1$d);\n", v));

                params.append("vv").append(v).append(".address(),");

                if (isRefArg(paramAnnotations[v-1]))
                    postCall.append(String.format("Utils.toArr(v%1$d, vv%1$d);\n", v));
            }
            else if (String.class.equals(parameter))
            {
                bHasAllocatedMemory = true;
                tryArgs.append(String.format("var vv%1$d = CLinker.toCString(v%1$d);", v));
                params.append("vv").append(v).append(".address(),");
            }
            else if (parameter.isRecord())
            {
                bHasAllocatedMemory = true;
                preCall.append(String.format("var vv%1$d = store%2$s(scope, v%1$d).address();\n", v, parameter.getSimpleName()));
                params.append("vv").append(v).append(",");
            }
            else if (parameter.isArray() && parameter.getComponentType().isRecord())
            {
                bHasAllocatedMemory = true;
                Class recordType = parameter.getComponentType();
                preCall.append(String.format("var vv%1$d = store%2$s(scope, v%1$d[0]);\n", v, recordType.getSimpleName()));
                params.append("vv").append(v).append(".address(),");

                if (isRefArg(paramAnnotations[v-1]))
                {
                    postCall.append(String.format("v%1$d[0] = read%2$s(vv%1d);", v, recordType.getSimpleName()));
                }
            }
            else
                params.append("v").append(v).append(",");
            ++v;
        }

        args.setLength(args.length() - 1);
        if (params.length() > 0)
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
                                        %s
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

        m_initSource.append(String.format("\t\tm_%s = m_methods.get(\"%s\");\n", method.getName(), method.getName()));
    }

    T build(Map<String, MethodHandle> methods) throws Throwable
    {
        m_initSource.append("\t}");
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

    private boolean isArrayOfPrimitives(Class c)
    {
        return c.isArray() && c.getComponentType().isPrimitive();
    }

    private boolean is2DArrayOfPrimitives(Class c)
    {
        return c.isArray() && c.getComponentType().isArray() && isArrayOfPrimitives(c.getComponentType());
    }
}
