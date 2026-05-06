package jpassport;

import jpassport.annotations.*;
import jpassport.codebuilder.ArenaTypeNeeded;
import jpassport.codebuilder.ArgClassification;
import jpassport.codebuilder.CBConstants;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


import static jpassport.PassportFactory.classToMemory;
import static jpassport.PassportFactory.isSpecialClass;
import static jpassport.codebuilder.ArenaTypeNeeded.*;
import static jpassport.codebuilder.ArgClassification.enum_long;
import static jpassport.codebuilder.ArgClassification.struct_return_memory;
import static jpassport.codebuilder.CBConstants.*;

/***
 * Given an interface class that extends Passport this class will generate a class that implements the interface
 * and allows calls through to a native library.
 * This class can dynamically create, compile, and hand back the class, or just create the source code so you
 * can compile later. If you use the PassportFactory then you do not need to use this class at all.
 * In order to write out a class you will:
 * new PassportWriter(MyInterface.class).writeModule(Path.of("out/testing"));
 * At this point your class is written, so you can compile it yourself. The created class still requires JPassport to run.
 * MyInterface mi = new MyInterface_impl(PassportFactory.loadMethodHandles(libName, MyInterface.class));
 *
 * @param <T> A class that extends Passport
 */
public class PassportWriter<T extends Passport> implements CBConstants
{
    private final StringBuilder m_source = new StringBuilder();
    private final StringBuilder m_moduleSource = new StringBuilder();
    private final String m_className;
    private final String m_fullClassName;
    private final String m_libName;

    private final boolean withDebug;

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
            put(boolean.class, "Boolean");
            put(char.class, "Character");
        }
    };

    private static final Map<Class<?>, String> typeToClass = new HashMap<>()
    {

        {
            put(byte.class, "Byte");
            put(short.class, "Short");
            put(int.class, "Integer");
            put(long.class, "Long");
            put(float.class, "Float");
            put(double.class, "Double");
            put(boolean.class, "Boolean");
            put(char.class, "Character");
        }
    };

    private static final Map<Class<?>, String> typeToCName = new HashMap<>()
    {
        {
            put(char.class, "JAVA_CHAR");
            put(byte.class, "JAVA_BYTE");
            put(short.class, "JAVA_SHORT");
            put(int.class, "JAVA_INT");
            put(long.class, "JAVA_LONG");
            put(float.class, "JAVA_FLOAT");
            put(double.class, "JAVA_DOUBLE");
            put(boolean.class, "JAVA_BOOLEAN");
        }
    };

    public PassportWriter(PassportFactory.WritingDetails details)
    {
        this(details.interfaceClass(), details.libraryName(),
                details.outputClassPackage(),
                details.outputClassName(),
                details.withDebug());
    }

    public PassportWriter(Class<T> interfaceClass, String libName, boolean withDebug)
    {
        this(interfaceClass, libName, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl", withDebug);
    }

    /**
     * Create a class based on the interface given.
     * @param interfaceClass The Passport interface
     * @param libName The name of the native library to load
     * @param packageName The package to make the class for
     * @param className The class name to build
     */
    public PassportWriter(Class<T> interfaceClass, String libName, String packageName, String className, boolean withDebug)
    {
        m_libName = libName;

        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);
        Set<Class<?>> extraImports = findAllExtraImports(interfaceMethods);
        m_className = className;
        m_fullClassName = packageName + "." + m_className;
        this.withDebug = withDebug;
        String structLayouts = buildStructLayouts(extraImports);
        String enumLookups = buildEnumLookups(extraImports);

        var verParts = Version.getVersionParts();

        String importInterface = "import " + interfaceClass.getName();
        if (interfaceClass.getEnclosingClass() != null)
        {
            importInterface = "import static " + interfaceClass.getEnclosingClass().getName() + "." + interfaceClass.getSimpleName();
        }

        m_source.append(String.format("""
                    package %1$s;

                    %2$s
                    %3$s;
                    import jpassport.DebugPassport;
                    import jpassport.Utils;
                    import jpassport.PassportFactory;
                    import jpassport.ErrorCapture;
                    import jpassport.pointers.Pointer;
                    import jpassport.pointers.GenericPointer;
                    import jpassport.pointers.MemoryBlock;
                    import jpassport.annotations.NativeLibrary;
                    import java.lang.invoke.MethodHandle;
                    import java.lang.foreign.*;
                    import java.util.HashMap;
                    import static java.lang.foreign.ValueLayout.*;
                    import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
                    
                    /**
                    This is code generated by the JPassport library
                    https://github.com/boulder-on/JPassport
                    **/
                    @NativeLibrary(name = "%12$s")
                    public class %4$s implements %5$s {
                        %6$s
                    
                        public static final int[] GENERATED_BY = {%7$d, %8$d, %9$d};
                        public static final int JAVA_VERSION = %10$d;
                    
                        public %11$s(HashMap<String, MethodHandle> methods)
                        {
                            this.methods.putAll(methods);
                        }

                        public %11$s()
                        {
                        }

                    """,
                packageName,
                buildExtraImports(extraImports),
                importInterface,
                m_className, interfaceClass.getSimpleName(),
                structLayouts + enumLookups,
                verParts[0], verParts[1], verParts[2],
                Runtime.version().version().getFirst(),
                m_className, m_libName));

        m_source.append(buildStoreStructFunction(extraImports));
        m_source.append(buildReadStructFunction(extraImports));

        if (interfaceClass.getModule() == null || interfaceClass.getModule().getName() == null ||
                interfaceClass.getModule().getName().equals("jpassport"))
        {
            m_moduleSource.append("""
                    module foreign.caller {
                        requires jpassport;
                    }
                    """);
        }
        else {
            m_moduleSource.append(String.format("""
                    module foreign.caller {
                        requires jpassport;
                        requires %s;
                    }
                    """, interfaceClass.getModule().getName()));
        }

        for (Method method : interfaceMethods) {
            addMethod(method, method.getReturnType(), interfaceClass);
        }
    }

    /**
     * Any extra Records that we want to work with must be imported into the class.
     * @param imports the record types we need to import.
     * @return The import statements for the records.
     */
    private String buildExtraImports(Set<Class<?>> imports)
    {
        StringBuilder strImports = new StringBuilder();
        for (Class<?> c : imports)
        {
            if (c.getEnclosingClass() == null)
                strImports.append("import ").append(c.getName()).append(";\n");
            else
            {
                String innerClassName = c.getEnclosingClass().getName() + "." + c.getSimpleName();
                strImports.append("import static ").append(innerClassName).append(";\n");
            }
        }
        return strImports.toString();
    }

    /**
     * This code will build all of the code required to convert a Record object into a MemoryLayout.
     * A MemoryLayout is the Java wrapper around a C struct.
     *
     * @param records the record types that we need to handle.
     * @return The code to create the required MemoryLayouts for all structs
     */
    private String buildStructLayouts(Set<Class<?>> records)
    {
        //We need to build the MemoryLayouts separately and bundle them together later.
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
            boolean isUnion = isUnion(c);

            if (isUnion)
                sbLayout.append(String.format("private static final UnionLayout %sLayout = Utils.makeUnion(\n", c.getSimpleName()));
            else
                sbLayout.append(String.format("private static final GroupLayout %sLayout = Utils.makeStruct(\n", c.getSimpleName()));

            //Build an array cached with the required byte offsets. This reduces the overhead on calls using structs ~60%-75%
            sbOffsets.append(String.format("\tprivate static final long[] %sLayoutOffsets = new long[] {\n", c.getSimpleName()));

            //Makes sure the union has the required annotations
            verifyUnion(c);

            for (Field f : c.getDeclaredFields())
            {
                if (skipUnionField(c, f))
                        continue;

                var varHandling = ArgClassification.classify(f);
                int paddingBits = getPaddingBytes(f);

                if(varHandling != struct_return_memory)
                    sbOffsets.append(String.format("\t\t%1$sLayout.byteOffset(groupElement(\"%2$s\")),\n", c.getSimpleName(), f.getName()));

                // negative indicates pre-padding
                if (paddingBits < 0)
                    sbLayout.append(String.format("\t\tMemoryLayout.paddingLayout(%d),\n", -paddingBits));

                var type = f.getType();
                switch (varHandling)
                {
                    case primitive ->
                        sbLayout.append(String.format("\t\t%s.withName(\"%s\"),\n",typeToCName.get(type), f.getName()));
                    case enum_ordinal, enum_int ->
                        sbLayout.append(String.format("\t\t%s.withName(\"%s\"),\n",typeToCName.get(int.class), f.getName()));
                    case enum_long ->
                        sbLayout.append(String.format("\t\t%s.withName(\"%s\"),\n",typeToCName.get(long.class), f.getName()));

                    case record_ -> {
                        requiresMap.get(c).add(type);
                        sbLayout.append(String.format("\t\t%sLayout.withName(\"%s\"),\n",type.getSimpleName(), f.getName()));
                    }
                    case record_ptr -> {
                        requiresMap.get(c).add(type);
                        sbLayout.append(String.format("\t\tADDRESS.withName(\"%s\"),\n", f.getName()));
                    }
                    case record_array -> {
                        Annotation[] arrays = f.getAnnotationsByType(Array.class);
                        requiresMap.get(c).add(type);
                        int length = ((Array) arrays[0]).length();
                        sbLayout.append(String.format("\t\tMemoryLayout.sequenceLayout(%d, %sLayout).withName(\"%s\"),\n", length, type.getComponentType().getSimpleName(), f.getName()));
                    }
                    case string_, mem_segment, generic_ptr, memory_block, primitive_array_ptr, record_array_ptr, enum_array_ptr ->
                        sbLayout.append(String.format("\t\tADDRESS.withName(\"%s\"),\n", f.getName()));

                    case primitive_array, enum_array -> {
                        Annotation[] arrays = f.getAnnotationsByType(Array.class);
                        if (arrays.length == 0)
                            throw new PassportException("Arrays in structs must be annotated with Array or Ptr: " + c.getSimpleName() + "." + f.getName());
                        int length = ((Array) arrays[0]).length();
                        Class<?> arrType = type.getComponentType();
                        //an array of enums is treated like an array of ints or longs
                        if (arrType.isEnum())
                        {
                            var enumType = ArgClassification.classify(type.getComponentType());
                            arrType = enumType == enum_long ? long.class : int.class;
                        }

                        sbLayout.append(String.format("\t\tMemoryLayout.sequenceLayout(%d, %s).withName(\"%s\"),\n", length, typeToCName.get(arrType), f.getName()));
                    }
                    case struct_return_memory -> {
                        //skip
                    }

                    default -> throw new PassportException("Cannot create a struct with field: " + c.getSimpleName() + "." + f.getName());
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

    private String buildEnumLookups(Set<Class<?>> records)
    {
        StringBuilder sbLookups = new StringBuilder();

        for (Class<?> c : records) {
            if (!c.isEnum())
                continue;

            var type = ArgClassification.classify(c);
            String autoBoxType = type == enum_long ? "Long" : "Integer";
            sbLookups.append(String.format("\tprivate static final HashMap<%1$s, Object> %2$sLookup = Utils.buildEnumMap%1$s(%2$s.class);\n", autoBoxType, c.getSimpleName()));
        }

        return sbLookups.toString();
    }


    /**
     * This code is used to write the code that converts Record classes into MemorySegments that can be passed
     * into methods.
     *
     * @param records the Record types we need to support.
     * @return The code that converts Records into MemorySegments
     */
    private String buildStoreStructFunction(Set<Class<?>> records)
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

                        private MemorySegment storePtrs%1$s(SegmentAllocator scope, %1$s[] recs) {
                            if (recs == null)
                                return MemorySegment.NULL;
                    
                            long addressBytes = ValueLayout.ADDRESS.byteSize();
                            MemorySegment ptr = scope.allocate(addressBytes * recs.length);
                            for (int n = 0; n < recs.length; ++n)
                            {
                                MemorySegment struct = store%1$s(scope, recs[n]);
                                ptr.set(ValueLayout.ADDRESS, addressBytes * n, struct);
                            }
                            return ptr;
                        };

                        private MemorySegment storeArr%1$s(SegmentAllocator scope, %1$s[] recs) {
                            if (recs == null)
                                return MemorySegment.NULL;
 
                            long size = %1$sLayout.byteSize();
                            MemorySegment ptr = scope.allocate(size * recs.length);
                            %1$s[] recsPass = new %1$s[1];
                            for (int n = 0; n < recs.length; ++n)
                            {
                                recsPass[0] = recs[n];
                                store%1$s(scope, recsPass, ptr.asSlice(size*n));
                            }
                            return ptr;
                        };

                        private MemorySegment store%1$s(SegmentAllocator scope, %1$s[] recs) {
                            return store%1$s(scope, recs, null);
                        }
                    
                        private MemorySegment store%1$s(SegmentAllocator scope, %1$s[] recs, MemorySegment memStruct) {
                            if (recs == null || (recs.length == 1 && recs[0] == null))
                                return MemorySegment.NULL;

                            long size = %1$sLayout.byteSize();
                            if (memStruct == null)
                            {
                                memStruct = scope.allocate(size * recs.length);
                            }
                    
                            long offset = 0;
                            for (%1$s rec : recs) {
                                if (rec == null) continue;
                    """,
                    c.getSimpleName()));

            boolean isUnion = isUnion(c);
            String toNativeFieldName = "";
            if (isUnion) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().equals(UnionFieldIO.class))
                    {
                        toNativeFieldName = f.getName();
                        break;
                    }
                }
            }

            int Element = 0;
            for (Field f : c.getDeclaredFields())
            {
                if (skipUnionField(c, f))
                    continue;

                var varHandling = ArgClassification.classify(f);
                Class<?> type = f.getType();
                String offset = "";
                if (varHandling != struct_return_memory)
                    offset = String.format("%sLayoutOffsets[%d] + offset", c.getSimpleName(), Element++);

                //if the union UnionToNativeIdx annotated field has the index of this field then write to memory.
                if (isUnion)
                    sb.append(String.format("\t\tif (rec.%1$s().toNative(%2$d, \"%3$s\"))\n", toNativeFieldName, Element-1, f.getName()));

                switch (varHandling)
                {
                    case primitive ->
                        sb.append(String.format("\t\tmemStruct.set(JAVA_%2$s, %3$s, rec.%1$s());\n", f.getName(), typeToName.get(type).toUpperCase(), offset));

                    case primitive_array ->
                            sb.append(String.format("\t\tmemStruct.asSlice(%2$s).copyFrom(MemorySegment.ofArray(rec.%1$s()));\n", f.getName(), offset));

                    case primitive_array_ptr ->
                            sb.append(String.format("\t\tmemStruct.set(ADDRESS, %2$s, Utils.toMS(scope, rec.%1$s(), false));\n", f.getName(), offset));

                    case record_ ->
                        sb.append(String.format("\t\tmemStruct.asSlice(%3$s).copyFrom(store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName(), offset));

                    case record_ptr ->
                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %3$s, store%2$s(scope, rec.%1$s()));\n", f.getName(), type.getSimpleName(), offset));

                    case record_array_ptr ->
                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %3$s, storePtrs%2$s(scope, rec.%1$s()));\n", f.getName(), type.getComponentType().getSimpleName(), offset));

                    case record_array ->
                            sb.append(String.format("\t\tmemStruct.asSlice(%3$s).copyFrom(storeArr%2$s(scope, rec.%1$s()));\n", f.getName(), type.getComponentType().getSimpleName(), offset));

                    case mem_segment ->
                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %2$s, rec.%1$s());\n", f.getName(), offset));

                    case string_ ->
                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %2$s, Utils.toCString(rec.%1$s(), scope));\n", f.getName(), offset));

                    case memory_block ->
                            sb.append(String.format("\t\tmemStruct.set(ADDRESS, %2$s, rec.%1$s().toPtr(scope));\n", f.getName(), offset));
                    case struct_return_memory -> {//skip
                         }
                    case enum_ordinal ->
                            sb.append(String.format("\t\tmemStruct.set(JAVA_%2$s, %3$s, rec.%1$s().ordinal());\n", f.getName(), typeToName.get(int.class).toUpperCase(), offset));
                    case enum_int ->
                            sb.append(String.format("\t\tmemStruct.set(JAVA_%2$s, %3$s, rec.%1$s().getCValue());\n", f.getName(), typeToName.get(int.class).toUpperCase(), offset));
                    case enum_long ->
                            sb.append(String.format("\t\tmemStruct.set(JAVA_%2$s, %3$s, rec.%1$s().getCValue());\n", f.getName(), typeToName.get(long.class).toUpperCase(), offset));
                    case enum_array -> {
                         var etype = ArgClassification.classify(f.getType().getComponentType());
                         if (etype == enum_long)
                             sb.append(String.format("\t\tvar vv%1d = Utils.enumToPrimitiveLong(rec.%2$s());\n", Element, f.getName()));
                         else
                            sb.append(String.format("\t\tvar vv%1$d = Utils.enumToPrimitiveInteger(rec.%2$s());\n", Element, f.getName()));
                        sb.append(String.format("\t\tmemStruct.asSlice(%1$s).copyFrom(MemorySegment.ofArray(vv%2$d));\n", offset, Element));
                    }
                    case enum_array_ptr -> {
                        var etype = ArgClassification.classify(f.getType().getComponentType());
                        if (etype == enum_long)
                            sb.append(String.format("\t\tvar vv%1d = Utils.enumToPrimitiveLong(rec.%2$s());\n", Element, f.getName()));
                        else
                            sb.append(String.format("\t\tvar vv%1$d = Utils.enumToPrimitiveInteger(rec.%2$s());\n", Element, f.getName()));

                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %1$s, Utils.toMS(scope, vv%2$d, false));\n", offset, Element));
                    }
                    case generic_ptr -> {
                        sb.append(String.format("\t\tmemStruct.set(ADDRESS, %2$s, rec.%1$s().getPtr());\n", f.getName(), offset));

                    }

                    default -> throw new PassportException("Type not supported in a struct: " + f.getType() + ", " + c.getSimpleName() +"." + f.getName());
                }
            }
            sb.append("\t\toffset += size;\n\t}\n");
            if (withDebug)
                sb.append(String.format("\t\tUtils.structBuilt(this, memStruct, %1$sLayout, \"%1$s\", recs);", c.getSimpleName()));
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
                        private void readPtrs%1$s(MemorySegment mem, %1$s[] rec) {
                            if (MemorySegment.NULL.equals(mem) || rec == null)
                                return;
 
                            GroupLayout layout = %1$sLayout;
                            long addressBytes = ValueLayout.ADDRESS.byteSize();
                            mem = mem.reinterpret(addressBytes * rec.length);
            
                            for (int n = 0; n < rec.length; ++n)
                            {
                                MemorySegment ptrToStruct = mem.get( ValueLayout.ADDRESS, n * addressBytes);
                                ptrToStruct = ptrToStruct.reinterpret(layout.byteSize());
                                rec[n] = read%1$s(ptrToStruct, rec[0]);
                            }
                        }

                        private void readArr%1$s(MemorySegment mem, %1$s[] rec) {
                            if (MemorySegment.NULL.equals(mem) || rec == null)
                                return;
 
                            GroupLayout layout = %1$sLayout;
                            long byteSize = layout.byteSize();
            
                            for (int n = 0; n < rec.length; ++n)
                            {
                                MemorySegment ptrToStruct = mem.asSlice(n * byteSize, byteSize);
                                rec[n] = read%1$s(ptrToStruct, rec[0]);
                            }
                        }
                        
                        private %1$s read%1$s(MemorySegment memStruct, %1$s rec) {
                            if (MemorySegment.NULL.equals(memStruct))
                                return null;
 
                            GroupLayout layout = %1$sLayout;
                            memStruct = Utils.resize(memStruct, layout.byteSize());
                    """,
                    c.getSimpleName()));

            int Element = 0;

            boolean isUnion = isUnion(c);
            String toFromNativeFieldName = "";
            if (isUnion) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().equals(UnionFieldIO.class))
                    {
                        toFromNativeFieldName = f.getName();
                        break;
                    }
                }
            }


            for (Field f : c.getDeclaredFields())
            {
                Class<?> type = f.getType();

                if (skipUnionField(c, f))
                {
                    sb.append(String.format("\t\tvar %1$s = rec.%1$s();\n", f.getName()));
                    continue;
                }

                var varHandling = ArgClassification.classify(f);
                String offset = "";
                if (varHandling != struct_return_memory)
                    offset = String.format("%sLayoutOffsets[%d]", c.getSimpleName(), Element++);

                String ifUnionRead = "";
                // creates a ternary operator to help read back the correct field for unions.
                if (isUnion)
                    ifUnionRead = String.format(" rec.%1$s().fromOrigRec(%2$d, \"%3$s\") ? rec.%3$s() : ", toFromNativeFieldName, Element-1, f.getName());

                switch (varHandling)
                {
                    case primitive ->
                        sb.append(String.format("\t\tvar %1$s = %4$s memStruct.get(JAVA_%2$s, %3$s);\n", f.getName(), typeToName.get(type).toUpperCase(), offset, ifUnionRead));

                    case record_ ->
                        sb.append(String.format("\t\tvar %1$s = %4$s read%2$s(memStruct.asSlice(%3$s), rec.%1$s());\n", f.getName(), type.getSimpleName(), offset, ifUnionRead));

                    case record_ptr ->
                        sb.append(String.format("\t\tvar %1$s = %4$s read%2$s(Utils.slice(memStruct, memStruct.get(ADDRESS, %3$s), %2$sLayout.byteSize()), rec.%1$s());\n", f.getName(), type.getSimpleName(), offset, ifUnionRead));

                    case record_array_ptr -> {
                            sb.append(String.format("\t\tvar %1$s = new %2$s[rec.%1$s().length];\n", f.getName(), type.getComponentType().getSimpleName()));
                            if (isUnion)
                                sb.append(String.format("\t\tif(rec.%1$s().fromNative(%2$d, \"%3$s\"))",toFromNativeFieldName, Element-1, f.getName()));
                            sb.append(String.format("\t\treadPtrs%2$s(memStruct.get(ADDRESS, %3$s), %1$s);\n", f.getName(), type.getComponentType().getSimpleName(), offset));
                    }

                    case record_array ->{
                            sb.append(String.format("\t\tvar %1$s = new %2$s[rec.%1$s().length];\n", f.getName(), type.getComponentType().getSimpleName()));
                            if (isUnion)
                                sb.append(String.format("\t\tif(rec.%1$s().fromNative(%2$d, \"%3$s\"))",toFromNativeFieldName, Element-1, f.getName()));
                            sb.append(String.format("\t\treadArr%2$s(memStruct.asSlice(%3$s, rec.%1$s().length * %2$sLayout.byteSize()), %1$s);\n", f.getName(), type.getComponentType().getSimpleName(), offset));
                    }

                    case mem_segment ->
                        sb.append(String.format("\t\tvar %1$s = %3$s  memStruct.get(ADDRESS, %2$s);\n", f.getName(), offset, ifUnionRead));

                    case generic_ptr -> {
                        sb.append(String.format("\t\tvar mem%1$s = memStruct.get(ADDRESS, %2$s);\n", f.getName(), offset));
                        sb.append(String.format("\t\tvar %1$s = %3$s new %2$s(mem%1$s);\n", f.getName(), type.getSimpleName(), ifUnionRead));
                    }
                    case string_ ->
                        sb.append(String.format("\t\tvar %1$s = %3$s Utils.readString(memStruct.get(ADDRESS, %2$s));\n", f.getName(), offset, ifUnionRead));

                    case primitive_array -> {
                        Annotation[] arrays = f.getAnnotationsByType(Array.class);
                        Class<?> arrType = type.getComponentType();
                        int length = ((Array) arrays[0]).length();
                        sb.append(String.format("\t\tvar %1$s = %6$s memStruct.asSlice(%4$s, %2$d * %5$s.BYTES).toArray(JAVA_%3$s);\n", f.getName(), length, typeToName.get(arrType).toUpperCase(), offset, typeToClass.get(arrType), ifUnionRead));
                    }
                    case primitive_array_ptr ->
                        sb.append(String.format("\t\tvar %1$s = %3$s Utils.toArr(memStruct, memStruct.get(ADDRESS, %2$s), rec.%1$s());\n", f.getName(), offset, ifUnionRead));

                    case memory_block -> {
                        sb.append(String.format("\t\tvar %1$sMS = memStruct.get(ADDRESS, %2$s);\n", f.getName(), offset));
                        sb.append(String.format("\t\tvar %1$s = %2$s MemoryBlock.recreate(%1$sMS, rec.%1$s());\n", f.getName(), ifUnionRead));
                    }
                    case struct_return_memory -> {
                        sb.append(String.format("\t\tvar %1$s = memStruct;\n", f.getName()));
                    }
                    case enum_ordinal, enum_int, enum_long -> {
                        Class<?> etype = varHandling == enum_long ? long.class : int.class;
                        sb.append(String.format("\t\tvar %1$stmp = %4$s memStruct.get(JAVA_%2$s, %3$s);\n", f.getName(), typeToName.get(etype).toUpperCase(), offset, ifUnionRead));
                        sb.append(String.format("\t\tvar %1$s = (%2$s)%2$sLookup.get(%1$stmp);",f.getName(), type.getSimpleName()));
                    }
                    case enum_array -> {
                        Annotation[] arrays = f.getAnnotationsByType(Array.class);
                        Class<?> arrType = type.getComponentType();
                        int length = ((Array) arrays[0]).length();
                        var etype = ArgClassification.classify(arrType);
                        Class<?> eClass = etype == enum_long ? long.class : int.class;
                        String boxedName = etype == enum_long ? "Long" : "Integer";
                        sb.append(String.format("\t\tvar %1$stmp = %6$s memStruct.asSlice(%4$s, %2$d * %5$s.BYTES).toArray(JAVA_%3$s);\n",
                                f.getName(), length, typeToName.get(eClass).toUpperCase(), offset, boxedName, ifUnionRead));
                        sb.append(String.format("\t\tvar %1$s = new %2$s[%3$d];\n", f.getName(), arrType.getSimpleName(), length));
                        if (etype == enum_long)
                            sb.append(String.format("\t\tUtils.primitiveToEnumLong(%1$stmp, %1$s, %2$sLookup);\n", f.getName(), arrType.getSimpleName()));
                        else
                            sb.append(String.format("\t\tUtils.primitiveToEnumInteger(%1$stmp, %1$s, %2$sLookup);\n", f.getName(), arrType.getSimpleName()));
                    }
                    case enum_array_ptr -> {
                        Class<?> arrType = type.getComponentType();
                        var etype = ArgClassification.classify(arrType);
                        String ptype = etype == enum_long ? "long" : "int";

                        sb.append(String.format("\t\tvar %1$stmp = %3$s Utils.toArr(memStruct, memStruct.get(ADDRESS, %2$s), new %4$s [rec.%1$s().length]);\n", f.getName(), offset, ifUnionRead, ptype));
                        sb.append(String.format("\t\tvar %1$s = %1$stmp == null ? null : new %2$s[rec.%1$s().length];\n", f.getName(), arrType.getSimpleName()));
                        if (etype == enum_long)
                            sb.append(String.format("\t\tUtils.primitiveToEnumLong(%1$stmp, %1$s, %2$sLookup);\n", f.getName(), arrType.getSimpleName()));
                        else
                            sb.append(String.format("\t\tUtils.primitiveToEnumInteger(%1$stmp, %1$s, %2$sLookup);\n", f.getName(), arrType.getSimpleName()));
                    }
                    default -> throw new PassportException("Type not supported in a struct: " + f.getType() + ", " + c.getSimpleName() +"." + f.getName());
                }
            }

            // Building the Record class to return
            sb.append(String.format("\t\tvar ret = new %s(", c.getSimpleName()));
            for (Field f : c.getDeclaredFields()) {
                sb.append(f.getName()).append(",");
            }
            sb.setLength(sb.length() - 1);
            sb.append(");\n");
            if (withDebug)
                sb.append(String.format("\t\tUtils.structReadBack(this, memStruct, %1$sLayout, \"%1$s\", ret);\n", c.getSimpleName()));

            sb.append("\n\treturn ret;\n}\n\n");
        }

        return sb.toString();
    }

    private void buildMethodMeta(Method method)
    {
        Class<?> retType = method.getReturnType();
        Class<?>[] parameters = method.getParameterTypes();
        for (int n = 0; n < parameters.length; ++n) {
            if (!(parameters[n].isPrimitive() || parameters[n].isEnum()) && !isSpecialClass(parameters[n]))
                parameters[n] = MemorySegment.class;
        }

        String[] memoryLayout = Arrays.stream(parameters)
                .filter(p -> !isSpecialClass(p)).
                map(this::classToMemory).toArray(String[]::new);

        if (void.class.equals(retType))
            m_source.append(String.format("\tstatic FunctionDescriptor fd_%s =  FunctionDescriptor.ofVoid(", method.getName()));
        else if (retType.isRecord())
            m_source.append(String.format("\tstatic FunctionDescriptor fd_%s =  FunctionDescriptor.of(%sLayout,", method.getName(), retType.getSimpleName()));
        else
        {
            m_source.append(String.format("\tstatic FunctionDescriptor fd_%s =  FunctionDescriptor.of(%s,", method.getName(), classToMemory(retType)));
        }
        for (String s:memoryLayout)
            m_source.append(s).append(",");
        m_source.setLength(m_source.length()-1);
        m_source.append(");\n");
    }

    String classToMemory(Class<?> type)
    {
        if (double.class.equals(type))
            return "ValueLayout.JAVA_DOUBLE";
        if (int.class.equals(type))
            return "ValueLayout.JAVA_INT";
        if (float.class.equals(type))
            return "ValueLayout.JAVA_FLOAT";
        if (short.class.equals(type))
            return "ValueLayout.JAVA_SHORT";
        if (byte.class.equals(type))
            return "ValueLayout.JAVA_BYTE";
        if (long.class.equals(type))
            return "ValueLayout.JAVA_LONG";
        if (boolean.class.equals(type))
            return "ValueLayout.JAVA_BOOLEAN";
        if (char.class.equals(type))
            return "ValueLayout.JAVA_CHAR";

        var ctype = ArgClassification.classify(type);
        if (ctype == ArgClassification.enum_long)
            return "ValueLayout.JAVA_LONG";
        else if (ctype == ArgClassification.enum_int || ctype == ArgClassification.enum_ordinal)
            return "ValueLayout.JAVA_INT";

        return "ValueLayout.ADDRESS";
    }

    /**
     * This method is used to create the code to support a single interface method.
     * @param method The interface method to implement
     * @param retType The return type of the method.
     */
    private void addMethod(Method method, Class<?> retType, Class<T> interfaceClass)
    {
        buildMethodMeta(method);
        StringBuilder args = new StringBuilder();
        StringBuilder params = new StringBuilder();
        StringBuilder preTryArgs = new StringBuilder();
        StringBuilder tryArgs = new StringBuilder();
        StringBuilder postCall = new StringBuilder();
        StringBuilder preCall = new StringBuilder();

        String strCallReturn = "";
        String strReturn = "";

        if (!void.class.equals(retType))
        {
            var varHandling = ArgClassification.classify(retType);

            switch (varHandling)
            {
                case string_ -> {
                    strCallReturn = "var ret = (MemorySegment)";
                    strReturn = "return Utils.readString(ret);";
                }
                case mem_segment -> {
                    strCallReturn = "var ret = (MemorySegment)";
                    strReturn = "return ret;";
                }
                case generic_ptr -> {
                    strCallReturn = "var ret = (MemorySegment)";
                    strReturn = "return new " + retType.getSimpleName() + "(ret);";
                }
                case enum_long -> {
                    strCallReturn = "var ret = (long)";
                    strReturn = String.format("return (%1$s)%1$sLookup.get(ret);", retType.getSimpleName());
                }
                case enum_int, enum_ordinal -> {
                    strCallReturn = "var ret = (int)";
                    strReturn = String.format("return (%1$s)%1$sLookup.get(ret);", retType.getSimpleName());
                }
                case record_ -> {
                    strCallReturn = "var ret = (MemorySegment)";
                    strReturn = String.format("return read%1$s(ret, null);", retType.getSimpleName());
                    params.append("(SegmentAllocator)scope,");
                }
                default -> {
                    strCallReturn = String.format("var ret = (%s)", retType.getSimpleName());
                    strReturn = "return ret;";
                }
            }
         }

        boolean isCriticalMethod = method.getAnnotation(Critical.class) != null;
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        var methodArgs = method.getParameters();
        int v = 1;
        ArenaTypeNeeded arenaNeeded = none;
        boolean bHasArenaArg = false;

        if (method.getReturnType().isRecord())
            arenaNeeded = global;

        for (Class<?> parameter : method.getParameterTypes())
        {
            var varHandling = ArgClassification.classify(parameter, paramAnnotations[v-1]);
            boolean isRefArg = isRefArg(paramAnnotations[v-1]) || isRefArg(interfaceClass.getAnnotations());

            switch (varHandling)
            {
                case arena ->
                    bHasArenaArg = true;

                case primitive_array -> {
                    if (isCriticalMethod)
                    {
                        //critical methods can access heap memory, which saves a data copy.
                        //load the array as heap memory
                        if (!isRefArg)
                            throw new PassportException("Critical methods with primitive array arguments must mark the arrays as RefArgs, the arrays will be changed.");

                        preCall.append(String.format("var vv%1$d = Utils.toMS(v%1$d);\n", v));
                        params.append("vv").append(v).append(',');
                    }
                    else {
                        arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                        preCall.append(String.format("var vv%1$d = Utils.toMS(scope, v%1$d, %2$s);\n", v,
                                isRefArgReadBackOnly(methodArgs[v - 1])));
                        params.append("vv").append(v).append(',');
                        if (isRefArg)
                            postCall.append(String.format("Utils.toArr(v%1$d, vv%1$d);\n", v));
                    }
                }

                case primitive_array2D -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("var vv%1$d = Utils.toMS(scope, v%1$d, %2$s);\n", v,
                            isRefArgReadBackOnly(methodArgs[v - 1])));
                    params.append("vv").append(v).append(',');
                }

                case primitive_array2D_ptr2ptrs -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("var vv%1$d = Utils.toPtrPTrMS(scope, v%1$d);", v));
                    params.append("vv").append(v).append(',');

                    if (isRefArg)
                        postCall.append(String.format("Utils.toArr(v%1$d, vv%1$d);\n", v));

                }
                case string_ -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("MemorySegment vv%1$d = v%1$d == null ? MemorySegment.NULL : Utils.toCString(v%1$d, scope);\n", v));
                    params.append("vv").append(v).append(',');

                }
                case string_array -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("MemorySegment vv%1$d = v%1$d == null ? MemorySegment.NULL : Utils.toCString(v%1$d, scope);\n", v));
                    params.append("vv").append(v).append(',');

                    if (isRefArg)
                        postCall.append(String.format("Utils.fromCString(vv%1$d, v%1$d);\n", v));

                }
                case memory_block -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("MemorySegment vv%1$d = v%1$d == null ? MemorySegment.NULL :v%1$d.toPtr(scope);\n", v));
                    params.append("vv").append(v).append(',');
                    postCall.append(String.format("v%1$d.readBack();\n", v));
                }
                case record_ -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("var vv%1$d =  store%2$s(scope, v%1$d);\n", v, parameter.getSimpleName()));
                    params.append("(MemorySegment)vv").append(v).append(",");

                }
                case record_array -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    Class<?> recordType = parameter.getComponentType();
                    preCall.append(String.format("var vv%1$d =  storeArr%2$s(scope, v%1$d);\n", v, recordType.getSimpleName()));
                    params.append("(MemorySegment)vv").append(v).append(",");

                    if (isRefArg)
                        postCall.append(String.format("readArr%2$s(vv%1d, v%1$d);", v, recordType.getSimpleName()));
                }
                case record_array_ptr -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    Class<?> recordType = parameter.getComponentType();
                    preCall.append(String.format("var vv%1$d =  storePtrs%2$s(scope, v%1$d);\n", v, recordType.getSimpleName()));
                    params.append("(MemorySegment)vv").append(v).append(",");

                    if (isRefArg)
                        postCall.append(String.format("readPtrs%2$s(vv%1d, v%1$d);", v, recordType.getSimpleName()));

                }
                case generic_ptr ->
                    params.append("v").append(v).append(".getPtr(),");

                case generic_ptr_array -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("var vv%1$d = Utils.toMS(scope, v%1$d, %2$s);\n", v,
                            isRefArgReadBackOnly(methodArgs[v - 1])));
                    params.append("vv").append(v).append(',');
                    if (isRefArg)
                        postCall.append(String.format("Utils.toArr(v%1$d, vv%1$d);\n", v));

                }
                case error_capture -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;
                    preCall.append(String.format("var vv%1$d = v%1$d.alloc(scope);\n", v));
                    postCall.append(String.format("v%1$d.readAfter(vv%1$d);\n", v));
                    params.append(String.format("vv%1$d,", v));
                }
                case enum_long, enum_int -> {
                    preCall.append(String.format("var vv%1$d = v%1$d.getCValue();\n", v));
                    params.append("vv").append(v).append(',');
                }
                case enum_ordinal -> {
                    preCall.append(String.format("var vv%1$d = v%1$d.ordinal();\n", v));
                    params.append("vv").append(v).append(',');
                }
                case enum_array -> {
                    arenaNeeded = arenaNeeded == none ? confined : arenaNeeded;

                    var enumType = ArgClassification.classify(parameter.getComponentType());
                    if (enumType == enum_long)
                        preCall.append(String.format("var tmp%1$d = Utils.enumToPrimitiveLong(v%1$d);\n", v));
                    else
                        preCall.append(String.format("var tmp%1$d = Utils.enumToPrimitiveInteger(v%1$d);\n", v));

                    preCall.append(String.format("var vv%1$d = Utils.toMS(scope, tmp%1$d, %2$s);\n", v,
                            isRefArgReadBackOnly(methodArgs[v - 1])));
                    params.append("vv").append(v).append(',');

                    if (isRefArg)
                    {
                        postCall.append(String.format("Utils.toArr(tmp%1$d, vv%1$d);\n", v));
                        if (enumType == enum_long)
                            postCall.append(String.format("Utils.primitiveToEnumLong(tmp%1$d, v%1$d, %2$sLookup);\n", v, parameter.getComponentType().getSimpleName()));
                        else
                            postCall.append(String.format("Utils.primitiveToEnumInteger(tmp%1$d, v%1$d, %2$sLookup);\n", v, parameter.getComponentType().getSimpleName()));
                    }
                }

                default ->
                    params.append("v").append(v).append(",");

            }

            if (varHandling == ArgClassification.arena)
                args.append(String.format("%s scope,", parameter.getSimpleName()));
            else
                args.append(String.format("%s v%d,", parameter.getSimpleName(), v));
            ++v;
        }

        if (!args.isEmpty())
            args.setLength(args.length() - 1);
        if (!params.isEmpty() && params.charAt(params.length() - 1) == ',')
            params.setLength(params.length() - 1);

        if (!bHasArenaArg) {
            switch (arenaNeeded) {
                case confined -> tryArgs.append("var scope = Arena.ofConfined();");
                case global -> preTryArgs.append("var scope = Arena.global();");
            }
        }

//        if (bHasAllocatedMemory && !bHasArenaArg)
//        {
//            tryArgs.append("var scope = Arena.ofConfined();");
//        }

        if (withDebug)
        {
            preCall.append(String.format("Utils.preNativeCall(this, \"%1$s\", %2$s);\n", method.getName(), params));
            String returnName = strCallReturn.isEmpty() ? "null" : "ret";
            postCall.insert(0, String.format("\nUtils.postNativeCall(this, \"%1$s\", %2$s, %3$s);\n", method.getName(), returnName, params));

        }

        if (!tryArgs.isEmpty())
            tryArgs.insert(0, "(").append(")");

        String nativeName = method.getName();
        if (method.isAnnotationPresent(NativeName.class))
        {
            var nn = method.getAnnotation(NativeName.class);
            nativeName = nn.name();
        }

        Class<?>[] parameters = method.getParameterTypes();
        boolean hasErrorCapture = parameters.length > 0 && parameters[0].equals(ErrorCapture.class);


        m_source.append(String.format("""
                                private static final MethodHandle %1$s;
                                 static {
                                    %1$s = PassportFactory.loadMethodHandle("%12$s", "%13$s", fd_%1$s, %14$b,  %15$b);
                                    if (%1$s != null)
                                        methods.put("%1$s", %1$s);
                                 }
                                public %2$s %1$s(%3$s)
                                {
                                    %16$s
                                    try %4$s {
                                        %5$s
                                        %6$s %1$s.invokeExact(%7$s);
                                        %8$s
                                        %9$s
                                    }
                                    catch(Throwable th)
                                    {
                                        throw new Error(th);
                                    }
                                }
                            
                            """,
                method.getName(),
                retType.getSimpleName(),args,
                tryArgs,
                preCall.toString().replace("\n", "\n\t\t\t"),
                strCallReturn, params,
                postCall.toString().replace("\n", "\n\t\t\t"),
                strReturn, interfaceClass.getSimpleName(), m_className,
                m_libName, nativeName, isCriticalMethod ,hasErrorCapture, preTryArgs));
    }

    public List<Path> writeModule(Path buildRoot) throws IOException
    {
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

        var fmanager = compiler.getStandardFileManager(null, null, null);
        var compileThis = fmanager.getJavaFileObjectsFromPaths(paths);

        var dothis = compiler.getTask(null, null, null,
                List.of( "--module-path", System.getProperty("jdk.module.path")),
                null, compileThis);
//        compiler.run(null, null, null,
//                 "--module-path", System.getProperty("jdk.module.path"),
//                paths.get(0).toString(), paths.get(1).toString());
        dothis.call();

        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] {buildRoot.toUri().toURL()});
        Class<? extends T> foreignImpl = (Class<? extends T>)Class.forName(m_fullClassName, true, classLoader);
        return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
    }


    void write(Path sourceRoot) throws Throwable
    {
        m_source.append("\n}");
        Path sourceFile = sourceRoot.resolve(m_className + ".java");
        Files.writeString(sourceFile, m_source);
    }




}
