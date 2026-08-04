package jpassport.parser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class HeaderToPassportClang {
    static Path destinationPath;
    static String packageName;
    static String interfaceName;
    static List<String> ignoreFunctionNames = new ArrayList<>();

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    // crash recovery is not an issue on Windows, so enable it there by default to work around a libclang issue with reparseTranslationUnit
    private static final boolean CRASH_RECOVERY = IS_WINDOWS || Boolean.getBoolean("libclang.crash_recovery");
    private static final SegmentAllocator IMPLICIT_ALLOCATOR = (size, align) -> Arena.ofAuto().allocate(size, align);
    private final static MemorySegment disableCrashRecovery =
            IMPLICIT_ALLOCATOR.allocateFrom("LIBCLANG_DISABLE_CRASH_RECOVERY=" + CRASH_RECOVERY);

    static {
        if (!CRASH_RECOVERY) {
            //this is a hack - needed because clang_toggleCrashRecovery only takes effect _after_ the
            //first call to createIndex.
            try {
                Linker linker = Linker.nativeLinker();
                String putenv = IS_WINDOWS ? "_putenv" : "putenv";
                MethodHandle PUT_ENV = linker.downcallHandle(linker.defaultLookup().find(putenv).get(),
                        FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS));
                int res = (int) PUT_ENV.invokeExact(disableCrashRecovery);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }
    }


    static int[] counts = new int[HeaderToPassport.OUTPUT_OBJECTS.values().length];

    //find arguments defined in C as arg[row][col]
    static Pattern pattArray = Pattern.compile(
            "\\[.*?\\]",
            Pattern.DOTALL
    );

    private final List<HeaderToPassport.CAlias> commonAliases = new ArrayList(List.of(
            new HeaderToPassport.CAlias("__stdcall", "FunctionPtr"),
            new HeaderToPassport.CAlias("__RPC_USER", "FunctionPtr"),

            new HeaderToPassport.CAlias("unsigned char", "byte"),
            new HeaderToPassport.CAlias("unsigned short", "short"),
            new HeaderToPassport.CAlias("unsigned int", "int"),
            new HeaderToPassport.CAlias("unsigned long", "long"),
            new HeaderToPassport.CAlias("unsigned long long", "long"),
            new HeaderToPassport.CAlias("unsigned long long int", "long"),
            new HeaderToPassport.CAlias("long long unsigned int", "long"),
            new HeaderToPassport.CAlias("short int", "short"),
            new HeaderToPassport.CAlias("long long int", "long"),
            new HeaderToPassport.CAlias("long long", "long"),
            new HeaderToPassport.CAlias("long double", "double"),
            new HeaderToPassport.CAlias("size_t", "long"),
            new HeaderToPassport.CAlias("__int32", "int"),
            new HeaderToPassport.CAlias("__uint32", "int"),
            new HeaderToPassport.CAlias("__int16", "short"),
            new HeaderToPassport.CAlias("__uint16", "short"),
            new HeaderToPassport.CAlias("__uint64", "long"),
            new HeaderToPassport.CAlias("__int64", "long")
    ));

    static HashMap<String, ClangHtoP.Type> typeDefToNative = new HashMap<>();


    private static final Set<String> emptyStructs = new HashSet<>();

//    public static Optional<Path> preprocess(Path headerPath, Path dest, List<String> preProcArgs)
//    {
//        return CPreprocess.preprocess(headerPath, dest, preProcArgs.toArray(new String[0]));
//    }

    static List<String> clangArgs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: [path to header] [path to destination] [package] [preprocessor options]");
            System.exit(1);
        }
        Path headerPath = Path.of(args[0]);
        String headerName = headerPath.getFileName().toString();
        destinationPath = Path.of(args[1]);
        packageName = args[2];
        interfaceName = headerName.replace(".", "_");

        validateArguments(headerPath);

        System.out.println("Loading: " + headerPath);
        Arrays.fill(counts, 0);

        var allHeaders = List.of(headerPath);
        var allheadernames = allHeaders.stream().map(Path::toString).toList();

        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                clangArgs.add(args[i]);
            }
        }
        clangArgs.add("-I" + headerPath.getParent().toAbsolutePath().toString());

        try (var clang = new ClangHtoP(allheadernames, clangArgs)){

            clang.parseClang();
            generateJava(clang, new HashSet<>());
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.methods.ordinal()]);
    }

    public static List<ClangHtoP.CFunction> build(String[] args, Set<String> ignoreFunctions) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: [path to header] [path to destination] [package] [preprocessor options]");
            System.exit(1);
        }
        Path headerPath = Path.of(args[0]);
        String headerName = headerPath.getFileName().toString();
        destinationPath = Path.of(args[1]);
        packageName = args[2];
        interfaceName = headerName.replace(".", "_");

        validateArguments(headerPath);

        System.out.println("Loading: " + headerPath);
        Arrays.fill(counts, 0);

        var allHeaders = List.of(headerPath);
        var allheadernames = allHeaders.stream().map(Path::toString).toList();

        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                clangArgs.add(args[i]);
            }
        }
        clangArgs.add("-I" + headerPath.getParent().toAbsolutePath().toString());
        List<ClangHtoP.CFunction> ret = new ArrayList<>();

        try (var clang = new ClangHtoP(allheadernames, clangArgs)){

            clang.parseClang();
            generateJava(clang, ignoreFunctions);
            ret = clang.allFunctions;
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.methods.ordinal()]);
        return ret;
    }

    public HeaderToPassportClang()
    {

    }


    public ClangHtoP processHeader(List<Path> headerPath, Path destinationPath, String packageName, List<String> ignoreFunctions) throws Throwable {

        String headerName = headerPath.get(0).getFileName().toString();
        this.destinationPath = destinationPath;
        this.packageName = packageName;
        interfaceName = headerName.replace(".", "_");
        ignoreFunctionNames = ignoreFunctions;

        System.out.println("Loading: " + headerName);
        System.out.println("Preprocessed file: " + headerName);

        Arrays.fill(counts, 0);
        var h = new ArrayList<String>();
        headerPath.forEach(p -> h.add(p.getFileName().toString()));
        var clang = new ClangHtoP(h, clangArgs);
        try {

            clang.parseClang();
            generateJava(clang, new HashSet<>());
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[HeaderToPassport.OUTPUT_OBJECTS.methods.ordinal()]);
        return clang;
    }

    public void finishGeneration(ClangHtoP clang) throws IOException {
        generateJava(clang, new HashSet<>());
    }

    private static void validateArguments(Path headerPath) {
        if (Files.notExists(headerPath)) {
            System.err.println("The given header file does not exist: " + headerPath);
        }
        if (Files.notExists(destinationPath)) {
            try
            {
                Files.createDirectories(destinationPath);
            }
            catch (IOException e) {
                System.err.println(e.getMessage());
                System.err.println("Unable to create the destination directory: " + destinationPath);
            }
        }
    }

    public static void generateJava(ClangHtoP clang, Set<String> ignoreFunctions) throws IOException {


        typeDefToNative = clang.typeDefToNative;

        // ---- Structs / Unions → Records ----
        generateJavaRecords(clang.allRecords);

        // ---- Enums ----
        generateJavaEnums(clang.allEnums);
        generateInterface(clang.allFunctions, ignoreFunctions);
    }

    private static void generateInterface(List<ClangHtoP.CFunction> funcs, Set<String> ignoreFunctions) throws FileNotFoundException {
        if (funcs.isEmpty())
            return;

        Path outFile = destinationPath.resolve(interfaceName + ".java");

        try {
            if (!Files.exists(outFile.getParent()))
                Files.createDirectories(outFile.getParent());
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }

        HashSet<String> ignorable = new HashSet<>();
        ignoreFunctionNames.stream().forEach(n -> ignorable.add(n));

        try (PrintWriter out = new PrintWriter(outFile.toFile())) {
            out.println("package " + packageName + ";\n\n");
            out.println("import jpassport.Passport;");
            out.println("import java.lang.foreign.MemorySegment;");
            out.println("import jpassport.pointers.*;");
            out.println("import jpassport.annotations.*;\n\n");

            out.println("public interface " + interfaceName + " extends Passport{");
            Set<String> functionsMade = new HashSet<>();

            for (ClangHtoP.CFunction f : funcs) {

                if (ignoreFunctions.contains(f.name()))
                {
                    System.out.println("Skipping function: " + f.name());
                    continue;
                }

//                out.println("/*");
//                out.println(f.CCode());
//                out.println("*/");
                if (functionsMade.contains(f.name()))
                    continue;
                functionsMade.add(f.name());

                out.print("    " + mapType(f.returnType(), true) + " " + f.name() + "(");
                StringBuilder params = new StringBuilder();
                int i = 1;
                for (int n = 0; n < f.parameters().size(); ++n)
                {
                    var p = f.parameters().get(n);
                    if (p.name().isEmpty())
                        p = new ClangHtoP.ArgDef(p.type(), "arg" + i, p.ptr(), p.error(), p.origText());
                    if (p.type().equals("..."))
                    {
                        var pp  = f.parameters().get(n-1);
                        p = new ClangHtoP.ArgDef(pp.type(), "arg" + i, pp.ptr(), p.error(), p.origText());
                    }
                    params.append(formatParam(p)).append(",");
                    i++;
                }
                if (!f.parameters().isEmpty())
                    params.setLength(params.length()-1);
                out.print(params);
                out.println(");");
                out.println();
                counts[HeaderToPassport.OUTPUT_OBJECTS.methods.ordinal()]++;
            }
            out.println("}");
        }
    }

    private static void generateJavaRecords(List<ClangHtoP.CRecord> records) throws IOException {
        for (ClangHtoP.CRecord r : records) {

            Path outFile = destinationPath.resolve(r.name().trim().replace("*", "") + ".java");

            if (Files.exists(outFile))
                Files.delete(outFile);

            try {
                if (!Files.exists(outFile.getParent()))
                    Files.createDirectories(outFile.getParent());
            }
            catch(IOException ex)
            {
                ex.printStackTrace();
            }
            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.*;");
                out.println("import jpassport.pointers.*;");
                out.println("import jpassport.annotations.*;\n\n");
                out.println("import java.lang.foreign.*;\n\n");


                out.println("/*");
                out.println(r.CCode());
                out.println("*/");
                if (r.isEmptyStruct()) {
                    out.println("import java.lang.foreign.MemorySegment;\n\n");
                    out.println("public class " + r.name() + " extends GenericPointer {");
                    out.println("public " + r.name() + "(MemorySegment addr) {\n" +
                            "        super(addr);\n" +
                            "    }");
                    out.println("}");
                } else {

                    out.println("public record " + r.name() + " (");
                    Set<String> usedNames = new HashSet<>();

                    for (int i = 0; i < r.fields().size(); i++) {
                        var f = r.fields().get(i);
                        String name = f.name();
                        if (name.isEmpty() || name.equals(f.type()))
                            name = "arg" + i;
                        if (usedNames.contains(f.name()))
                            continue;
                        if (i > 0)
                            out.println(",");
                        out.print("    " + mapRecordType(f) + " " + name);
                        usedNames.add(f.name());
                    }

                    if (r.kind().equalsIgnoreCase("union"))
                        out.println("    ,UnionFieldIO unionIO\n");
                    out.println("\n) {}\n");
                }

                counts[HeaderToPassport.OUTPUT_OBJECTS.records.ordinal()]++;
            }
        }
    }

    static boolean allSequential(List<ClangHtoP.CEnumValue> enums) {
        long curVal = 0;
        for (var e : enums) {
            if (e.value() != curVal)
                return false;
            curVal++;
        }
        return true;
    }

    static void generateJavaEnums(List<ClangHtoP.CEnum> enums) throws FileNotFoundException {
        for (ClangHtoP.CEnum e : enums) {

            boolean useLongs = e.values().stream().anyMatch(ClangHtoP.CEnumValue::forceLong);
            boolean isSequential = allSequential(e.values());

            Path outFile = destinationPath.resolve(e.name().replace("*", "").trim() + ".java");
            try {
                if (!Files.exists(outFile.getParent()))
                    Files.createDirectories(outFile.getParent());
            }
            catch(IOException ex)
            {
                ex.printStackTrace();
            }

            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.enums.EnumInt;\n");
                out.println("import jpassport.enums.EnumLong;\n");

                boolean useValues = !isSequential || useLongs;

                out.println("/*");
                out.println(e.CCode());
                out.println("*/");

                if (isSequential && !useLongs)
                    out.println("public enum " + e.name() + " {");
                else if (useLongs)
                    out.println("public enum " + e.name() + " implements EnumLong {");
                else
                    out.println("public enum " + e.name() + " implements EnumInt {");

                for (int i = 0; i < e.values().size(); i++) {
                    ClangHtoP.CEnumValue v = e.values().get(i);
                    if (useValues) {
                        if (useLongs)
                            out.printf("    %s(%dL), //0x%x\n", v.name(), v.value(), v.value());
                        else
                            out.printf("    %s(%d), //0x%x\n", v.name(), v.value(), v.value());
                    } else
                        out.print("    " + v.name() + ",\n");

                }
                out.println(";");

                if (useValues) {

                    if (useLongs) {
                        out.println("\n    public final long value;");
                        out.println("\n    " + e.name() + "(long v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public long getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    } else {
                        out.println("\n    public final int value;");
                        out.println("\n    " + e.name() + "(int v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public int getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    }
                }

                out.println("}\n");
                counts[HeaderToPassport.OUTPUT_OBJECTS.enums.ordinal()]++;
            }
        }
    }

    static String formatParam(ClangHtoP.ArgDef p) {
        if (p.error())
            return p.origText();

        long ptrCount = p.ptr().chars().filter(ch -> ch == '*' || ch == '[').count();
        String javaType = mapType(p, false);

        long ptrCount2 = javaType.chars().filter(ch -> ch == '*' || ch == '[').count();
        if (ptrCount2 > ptrCount)
            ptrCount = ptrCount2;

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        if (ptrCount == 2)
            return "@PtrPtrArg " + javaType + " " + p.name();
        else {
            return javaType + " " + p.name();
        }
    }

    static String mapType(ClangHtoP.ArgDef def, boolean isReturn) {
        if (def.error())
            return def.origText();

        String t = def.type().trim();
        String ptr = def.ptr();
        if (typeDefToNative.containsKey(t)) {
            //Empty structs are just GenericPointers. So if we are not an empty struct then do not change the pointer
            //count. This preserves things like "uint16 *myarg" which should be treated as a java array
            if (emptyStructs.contains(t) || typeDefToNative.get(t).ptrRequired())
                ptr = typeDefToNative.get(t).ptr();
            var tt = typeDefToNative.get(t);
            if (!tt.name().isEmpty())
                t = tt.name();
        }

        long ptrCount = ptr.chars().filter(ch -> ch == '*').count();

        if (isReturn && ptrCount > 0)
            return "MemorySegment";
        if (isReturn && def.type().isEmpty())
            return "void";

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        String base = t.replaceAll("\\s+", " ");

        if (typeDefToNative.containsKey(base))
            base = typeDefToNative.get(base).name();

        base = base.toLowerCase(Locale.ROOT);

        if (base.startsWith("long long") ||
                base.startsWith("unsigned long long")) {
            base = "long long";
        }

        String javaType = switch (base) {
            case "int" -> "int";
            case "short" -> "short";
            case "long", "long long" -> "long";
            case "float" -> "float";
            case "double" -> "double";
            case "bool" -> "boolean";
            case "char" -> "byte";
            case "void" -> "void";
            default -> t;
        };

        if (ptrCount > 0) {
            if (base.equals("char")) {
                return "String";
            } else if (base.equals("void")) {
                return "byte[]";
            } else if (emptyStructs.contains(javaType)) {
                //this is the case where an empty struct has been defined. Empty structs get treated
                //as new classes that extend GenericPointer
                return javaType;
            } else {

                for (int n = 0; n < ptrCount; n++)
                    javaType = javaType + "[]";
                return javaType;
            }
        }

        return javaType;
    }

    static String mapRecordType(ClangHtoP.ArgDef cType) {
        if (cType.error())
            return cType.origText();

        String t = cType.type().trim();
        long ptrCount = cType.ptr().chars().filter(ch -> ch == '*').count();

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

//        String base = t.replaceAll("\\s+", " ");
        String base = t;

        if (typeDefToNative.containsKey(base))
        {
            var tt = typeDefToNative.get(base);
            if (!tt.name().isEmpty())
                base = tt.name();
            ptrCount = tt.ptr().chars().filter(ch -> ch == '*').count();
        }

        String annotation = "";
        if (ptrCount == 2)
            annotation = "@PtrPtrArg ";
        else if (ptrCount == 1)
            annotation = "@Ptr ";
        else if (cType.ptr().contains("[")) {
            var matcher = pattArray.matcher(cType.ptr());
            String number = "";
            if (matcher.find()) {
                String b = matcher.group(0).trim();
                number = b.substring(1, b.length() - 1);
//                base = base.replace(b, "[]");
                base = base + "[]";
            }
            if (!number.isEmpty())
                annotation = "@Array(length = " + number + ") ";
        }

        if (ptrCount > 0) {
            if (base.equals("char")) {
                return "String";
            } else if (base.equals("void")) {
                return "byte[]";
            } else {
                return annotation + base + "[]";
            }
        }


        return annotation + base;
    }
}