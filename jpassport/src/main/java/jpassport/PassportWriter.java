package jpassport;

import jdk.incubator.foreign.MemoryAddress;
import jpassport.annotations.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static jpassport.Utils.Platform.Windows;

/***
 * Given an interface class that extends Passport this class will generate a class that implements the interface
 * and allows calls through to a native library.
 *
 * This class can dynamically create, compile, and hand back the class, or just create the source code so you
 * can compile later. If you use the PassportFactory then you do not need to use this class at all.
 *
 * In order to write out a class you will:
 *
 * new PassportWriter(MyInterface.class).writeModule(Path.of("out/testing"));
 *
 * At this point your class is written, so you can compile it yourself. The created class still required JPassport to run.
 *
 * MyInterface mi = new MyInterface_impl(PassportFactory.loadMethodHandles(libName, MyInterface.class));
 *
 * @param <T> A class that extends Passport
 */
public class PassportWriter<T extends Passport>
{
    private final StringBuilder m_source = new StringBuilder();
    private final StringBuilder m_moduleSource = new StringBuilder();
    private final StringBuilder m_initSource = new StringBuilder();
    private final String m_className;
    private final String m_fullClassName;

    private static int Class_ID = 1; //Used to make unique package names

    private static final Map<Class<?>, String> typeToName = new HashMap<>()
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

    private static final Map<Class<?>, String> typeToCName = new HashMap<>()
    {
        {
            put(byte.class, "C_CHAR");
            put(short.class, "C_SHORT");
            put(int.class, "C_INT");
            put(long.class, "C_LONG_LONG");
            put(float.class, "C_FLOAT");
            put(double.class, "C_DOUBLE");
        }
    };

    public PassportWriter(Class<T> interfaceClass)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl");
    }

    /**
     * Create a class based on the interface given.
     * @param interfaceClass The Passport interface
     * @param packageName The package to make the class for
     * @param className The class name to build
     */
    public PassportWriter(Class<T> interfaceClass, String packageName, String className)
    {
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);
        Set<Class<?>> extraImports = findAllExtraImports(interfaceMethods);
        m_className = className;
        m_fullClassName = packageName + "." + m_className;
        String structLayouts = buildStructLayouts(extraImports);

        var verParts = Version.getVersionParts();


        m_source.append(String.format("""
                    package %s;

                    %s
                    import %s;
                    import jpassport.Utils;
                    import java.lang.invoke.MethodHandle;
                    import jdk.incubator.foreign.*;
                    import java.util.HashMap;
                    import static jdk.incubator.foreign.MemoryAccess.*;
                    import static jdk.incubator.foreign.MemoryLayout.PathElement.*;
                    import static jdk.incubator.foreign.CLinker.*;

                    /**
                    This is code generated by the JPassport library
                    https://github.com/boulder-on/JPassport
                    **/                                       
                    public class %s implements %s {
                        HashMap<String, MethodHandle> m_methods;
                        %s
                        
                        public static final int[] GENERATED_BY = {%d, %d, %d};
                        public static final int JAVA_VERSION = %d;
                        
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
                verParts[0], verParts[1], verParts[2],
                Runtime.version().version().get(0),
                m_className));

        m_source.append(buildStoreStructFunction(extraImports));
        m_source.append(buildReadStructFunction(extraImports));

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

        for (Method method : interfaceMethods) {
            addMethod(method, method.getReturnType());
        }
    }

    /**
     * Any extra Records that we want to work with must be imported into the class.
     * @param imports All of the record types.
     * @return The import statements for the records.
     */
    public String buildExtraImports(Set<Class<?>> imports)
    {
        StringBuilder strImports = new StringBuilder();
        for (Class<?> c : imports)
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
    public String buildStructLayouts(Set<Class<?>> records)
    {
        //We need to build all of the MemoryLayouts separately and bundle them together later.
        //If any one record requires other records then we need to make sure the code is ordered
        //properly so there are no forward references.
        HashMap<Class<?>, List<Class<?>>> requiresMap = new HashMap<>();
        HashMap<Class<?>, StringBuilder> layoutMap = new HashMap<>();

        for (Class<?> c : records)
        {
            if (!c.isRecord())
                continue;

            if (!requiresMap.containsKey(c))
                requiresMap.put(c, new ArrayList<>());

            StringBuilder sbLayout = new StringBuilder();
            StringBuilder sbOffsets = new StringBuilder();
            layoutMap.put(c, sbLayout);

            sbLayout.append(String.format("private static final GroupLayout %sLayout = MemoryLayout.structLayout(\n", c.getSimpleName()));

            //Build an array cached with the required byte offsets. This reduces the overhead on calls using structs ~60%-75%
            sbOffsets.append(String.format("\tprivate static final long[] %sLayoutOffsets = new long[] {\n", c.getSimpleName()));

            for (Field f : c.getDeclaredFields())
            {
                int paddingBits = getPaddingBits(f);

                sbOffsets.append(String.format("\t\t%1$sLayout.byteOffset(groupElement(\"%2$s\")),\n", c.getSimpleName(), f.getName()));

                // negative indicates pre-padding
                if (paddingBits < 0)
                    sbLayout.append(String.format("\t\tMemoryLayout.paddingLayout(%d),\n", -paddingBits));

                Class<?> type = f.getType();
                if (type.isPrimitive())
                {
                    sbLayout.append(String.format("\t\t%s.withName(\"%s\"),\n",typeToCName.get(type), f.getName()));
                }
                else if (type.isRecord())
                {
                    requiresMap.get(c).add(type);
                    boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPtr)
                        sbLayout.append(String.format("\t\tC_POINTER.withName(\"%s\"),\n", f.getName()));
                    else
                        sbLayout.append(String.format("\t\t%sLayout.withName(\"%s\"),\n",type.getSimpleName(), f.getName()));
                }
                else if (String.class.equals(type))
                    sbLayout.append(String.format("\t\tC_POINTER.withName(\"%s\"),\n", f.getName()));
                else if (type.isArray())
                {
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                    if (arrays.length > 0)
                    {
                        int length = ((Array) arrays[0]).length();
                        Class<?> arrType = type.getComponentType();

                        sbLayout.append(String.format("\t\tMemoryLayout.sequenceLayout(%d, %s).withName(\"%s\"),\n", length, typeToCName.get(arrType), f.getName()));
                    }
                    else if (isPointer)
                        sbLayout.append(String.format("\t\tC_POINTER.withName(\"%s\"),\n", f.getName()));
                    else
                        throw new PassportException("Record arrays must be defined with either an Array or Ptr annotation");
                }

                if (paddingBits > 0)
                    sbLayout.append(String.format("\t\tMemoryLayout.paddingLayout(%d),\n", paddingBits));
            }
            sbLayout.setLength(sbLayout.length() - 2);
            sbLayout.append(");\n\n");
            sbOffsets.append("\t};\n\n");

            sbLayout.append(sbOffsets);
        }

        StringBuilder allStructs = new StringBuilder();
        // This code attempts to only write out Records that have all Records they depend on written out first.
        //Without this code we could get compile failures because of forward references.
        while (!requiresMap.isEmpty())
        {
            List<Class<?>> allRecords = new ArrayList<>(requiresMap.keySet());
            for (Class<?> c : allRecords)
            {
                List<Class<?>> required = requiresMap.get(c);
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
    public String buildStoreStructFunction(Set<Class<?>> records)
    {
        StringBuilder sb = new StringBuilder();

        for (Class<?> c : records)
        {
            if (!c.isRecord())
                continue;

            sb.append(String.format("""
                        private MemorySegment store%1$s(SegmentAllocator scope, %1$s rec) {
                            return store%1$s(scope, new %1$s[] {rec});
                        };
                            
                        private MemorySegment store%1$s(SegmentAllocator scope, %1$s[] recs) {
                            long size = %1$sLayout.byteSize();
                            MemorySegment memStruct = scope.allocate(size);
                            
                            long offset = 0;
                            for (%1$s rec : recs) {
                    """,
                    c.getSimpleName()));

            int Element = 0;
            for (Field f : c.getDeclaredFields())
            {
                Class<?> type = f.getType();
                String offset = String.format("%sLayoutOffsets[%d] + offset", c.getSimpleName(), Element++);

                if (type.isPrimitive())
                    sb.append(String.format("\t\tset%2$sAtOffset(memStruct, %3$s, rec.%1$s());\n", f.getName(), typeToName.get(type), offset));
                else if (type.isRecord())
                {
                    boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPtr)
                        sb.append(String.format("\t\tsetAddressAtOffset(memStruct, %3$s, store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName(), offset));
                    else
                        sb.append(String.format("\t\tmemStruct.asSlice(%3$s).copyFrom(store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName(), offset));
                }
                else if (String.class.equals(type))
                    sb.append(String.format("\t\tsetAddressAtOffset(memStruct, %2$s, toCString(rec.%1$s(), scope).address());\n", f.getName(), offset));
                else if (type.isArray())
                {
                    Class<?> arrType = type.getComponentType();
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                    if (arrType.isPrimitive())
                    {
                        if (arrays.length > 0)
                            sb.append(String.format("\t\tmemStruct.asSlice(%2$s).copyFrom(MemorySegment.ofArray(rec.%1$s()));\n", f.getName(), offset));
                        else if (isPointer)
                            sb.append(String.format("\t\tsetAddressAtOffset(memStruct, %2$s, Utils.toMS(scope, rec.%1$s()).address());\n", f.getName(), offset));
                    }

                }
            }
            sb.append("\t\toffset += size;\n\t}\n");
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
    private String buildReadStructFunction(Set<Class<?>> records)
    {
        StringBuilder sb = new StringBuilder();

        for (Class<?> c : records)
        {
            if (!c.isRecord())
                continue;

            sb.append(String.format("""
                        private %1$s read%1$s(MemorySegment memStruct, %1$s rec) {
                            GroupLayout layout = %1$sLayout;
                    """,
                    c.getSimpleName()));

            int Element = 0;

            for (Field f : c.getDeclaredFields())
            {
                String offset = String.format("%sLayoutOffsets[%d]", c.getSimpleName(), Element++);

                Class<?> type = f.getType();
                if (type.isPrimitive())
                    sb.append(String.format("\t\tvar %1$s = get%2$sAtOffset(memStruct, %3$s);\n", f.getName(), typeToName.get(type), offset));
                else if (type.isRecord())
                {
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPointer)
                        sb.append(String.format("\t\tvar %1$s = read%2$s(Utils.slice(memStruct.scope(), getAddressAtOffset(memStruct, %3$s), %2$sLayout.byteSize()), rec.%1$s());\n", f.getName(), type.getSimpleName(), offset));
//                        sb.append(String.format("\t\tvar %1$s = read%2$s(getAddressAtOffset(memStruct, %3$s).asSegment(%2$sLayout.byteSize(), memStruct.scope()), rec.%1$s());\n", f.getName(), type.getSimpleName(), offset));
                    else
                        sb.append(String.format("\t\tvar %1$s = read%2$s(memStruct.asSlice(%3$s), rec.%1$s());\n", f.getName(), type.getSimpleName(), offset));
                }
                else if (String.class.equals(type))
                    sb.append(String.format("\t\tvar %1$s = Utils.readString(getAddressAtOffset(memStruct, %2$s));\n", f.getName(), offset));
                else if (type.isArray())
                {
                    Class<?> arrType = type.getComponentType();
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                    if (arrType.isPrimitive())
                    {
                        if (arrays.length > 0) {
                            int length = ((Array) arrays[0]).length();
                            sb.append(String.format("\t\tvar %1$s = memStruct.asSlice(%4$s, %2$d * %3$s.BYTES).to%3$sArray();\n", f.getName(), length, typeToName.get(arrType), offset));
                        }
                        else if (isPointer)
                        {
                            sb.append(String.format("\t\tint %1$sSize = rec.%1$s().length;\n", f.getName()));
                            sb.append(String.format("\t\tvar %1$s = Utils.toArr%2$s(getAddressAtOffset(memStruct, %3$s), %1$sSize);\n", f.getName(), typeToName.get(arrType), offset));
                        }
                    }
                }
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
    public void addMethod(Method method, Class<?> retType)
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
                strReturn = "return Utils.readString(ret);";
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

        for (Class<?> parameter : method.getParameterTypes())
        {
            args.append(String.format("%s v%d,", parameter.getSimpleName(), v));

            if (isArrayOfPrimitives(parameter) || is2DArrayOfPrimitives(parameter))
            {
                bHasAllocatedMemory = true;
                if (isPtrPtrArg(paramAnnotations[v-1]))
                    preCall.append(String.format("var vv%1$d = Utils.toPtrPTrMS(allocator, v%1$d);", v));
                else
                    preCall.append(String.format("var vv%1$d = Utils.toMS(allocator, v%1$d);\n", v));

                params.append("Utils.toAddr(vv").append(v).append("),");

                if (isRefArg(paramAnnotations[v-1]))
                    postCall.append(String.format("Utils.toArr(v%1$d, vv%1$d);\n", v));
            }
            else if (String.class.equals(parameter))
            {
                bHasAllocatedMemory = true;
                preCall.append(String.format("var vv%1$d = v%1$d == null ? MemoryAddress.NULL : toCString(v%1$d, allocator).address();\n", v));
                params.append("vv").append(v).append(',');
            }
            else if (parameter.isRecord())
            {
                bHasAllocatedMemory = true;
                preCall.append(String.format("var vv%1$d = store%2$s(allocator, v%1$d).address();\n", v, parameter.getSimpleName()));
                params.append("vv").append(v).append(",");
            }
            else if (parameter.isArray() && parameter.getComponentType().isRecord())
            {
                bHasAllocatedMemory = true;
                Class<?> recordType = parameter.getComponentType();
                preCall.append(String.format("var vv%1$d = store%2$s(allocator, v%1$d);\n", v, recordType.getSimpleName()));
                params.append("vv").append(v).append(".address(),");

                if (isRefArg(paramAnnotations[v-1]))
                {
                    postCall.append(String.format("v%1$d[0] = read%2$s(vv%1d, v%1$d[0]);", v, recordType.getSimpleName()));
                }
            }
            else
                params.append("v").append(v).append(",");
            ++v;
        }

        if (args.length() > 0)
            args.setLength(args.length() - 1);
        if (params.length() > 0 && params.charAt(params.length() - 1) == ',')
            params.setLength(params.length() - 1);
        if (bHasAllocatedMemory)
        {
            tryArgs.append("var scope = ResourceScope.newConfinedScope();");
            preCall.insert(0, "var allocator = SegmentAllocator.ofScope(scope);\n\t\t");
        }

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

    public List<Path> writeModule(Path buildRoot) throws IOException
    {
        m_initSource.append("\t}");
        m_source.append(m_initSource);
        m_source.append("\n}");

        String[] packages = m_fullClassName.split("\\.");
        Path sourceRoot = buildRoot.resolve(packages[0]);

        for (int n = 1; n < packages.length - 1; ++n)
            sourceRoot = sourceRoot.resolve(packages[n]);

        if (Files.exists(sourceRoot))
            Utils.deleteFolder(sourceRoot);
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve(m_className + ".java");
        Files.writeString(sourceFile, m_source);
        Path moduleFile = buildRoot.resolve("module-info.java");
        Files.writeString(moduleFile, m_moduleSource);

        return List.of(moduleFile, sourceFile);
    }

    T build(Map<String, MethodHandle> methods) throws Throwable
    {
        Path buildRoot = Utils.getBuildFolder();
        List<Path> paths = writeModule(buildRoot);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null,
                "--module-path", System.getProperty("jdk.module.path"),
                paths.get(0).toString(), paths.get(1).toString());

        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] {buildRoot.toUri().toURL()});
        Class<? extends T> foreignImpl = (Class<? extends T>)Class.forName(m_fullClassName, true, classLoader);
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

    private boolean isArrayOfPrimitives(Class<?> c)
    {
        return c.isArray() && c.getComponentType().isPrimitive();
    }

    private boolean is2DArrayOfPrimitives(Class<?> c)
    {
        return c.isArray() && c.getComponentType().isArray() && isArrayOfPrimitives(c.getComponentType());
    }

    /**
     * This will search the interface method for return types and arguments that should be imported.
     * These will all be Records.
     *
     * @param interfaceMethods All of the methods in the interfacee
     * @return The list of Record types that should be imported.
     */
    private static Set<Class<?>> findAllExtraImports(List<Method> interfaceMethods) {
        Set<Class<?>> extraImports = new HashSet<>();
        for (Method m : interfaceMethods) {
            Class<?> retType = m.getReturnType();
            Class<?>[] params = m.getParameterTypes();

            if (!isValidArgType(retType))
                throw new PassportException("Types in the interface must by primitive, arrays of primitives, String, or Records. " + retType.getSimpleName() + " not supported.");

            List<Class<?>> invalid = Arrays.stream(params).filter(p -> !isValidArgType(p)).collect(Collectors.toList());
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
        for (Class<?> c : extraImports)
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
    static Set<Class<?>> findSubRecords(Class<?> record)
    {
        Set<Class<?>> subRecords = new HashSet<>();
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
    private static boolean isValidArgType(Class<?> c)
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

}
