package jpassport.parser;
import com.sun.jna.platform.win32.WinNT;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * TODO:
 * - Handle unions defined within structs
 *  -Auto generate the implementation (requires pre compiling all generated code and passing in the native library name)
 *
 *  DONE
 *   -Handle #defines, #ifdef, #ifndef - i.e. preprocessor
 *   -Add simple completion stats (number of structs, enums, and methods)
 *  -find 2D pointers and annotate with PtrPtrArg (methods, records)
 *  -Error out on deeper nesting of pointers (***, or [][][] in records or methods)
 */
public class HeaderToPassport {
    static Path destinationPath;
    static String packageName;
    static String interfaceName;

    static List<String> warnings = new ArrayList<>();

    enum OUTPUT_OBJECTS {methods, records, enums}

    static HashSet<String> typesWritten = new HashSet();

    static int[] counts = new int[OUTPUT_OBJECTS.values().length];

    static List<CFunction> functions = new ArrayList<>();
    static List<CRecord> records = new ArrayList<>();
    static List<CEnum> enums = new ArrayList<>();

    //find arguments defined in C as arg[row][col]
    static Pattern pattArray = Pattern.compile(
            "\\[.*?\\]",
            Pattern.DOTALL
    );

    private static final List<CAlias> commonAliases = List.of(
            new CAlias("unsigned char", "byte"),
            new CAlias("unsigned short", "short"),
            new CAlias("unsigned int", "int"),
            new CAlias("unsigned long", "long"),
            new CAlias("unsigned long long", "long"),
            new CAlias("unsigned long long int", "long"),
            new CAlias("long long unsigned int", "long"),
            new CAlias("short int", "short"),
            new CAlias("long long int", "long"),
            new CAlias("long long", "long"),
            new CAlias("long double", "double"),
            new CAlias("size_t", "long"),
            new CAlias("__int32", "int"),
            new CAlias("__uint32", "int"),
            new CAlias("__int16", "short"),
            new CAlias("__uint16", "short")
    );

    private static final HashMap<String, Type> typeDefToNative = new HashMap<>();

    static {
        typeDefToNative.put("char", new Type("char", true, ""));
        typeDefToNative.put("uint8_t", new Type("byte", true, ""));
        typeDefToNative.put("uint_least8_t", new Type("byte", true, ""));
        typeDefToNative.put("uint_fast8_t", new Type("byte", true, ""));
        typeDefToNative.put("short", new Type("short", true, ""));
        typeDefToNative.put("int", new Type("int", true, ""));
        typeDefToNative.put("long", new Type("long", true, ""));
        typeDefToNative.put("time_t", new Type("long", true, ""));
        typeDefToNative.put("float", new Type("float", true, ""));
        typeDefToNative.put("double", new Type("double", true, ""));
        typeDefToNative.put("size_t", new Type("long", true, ""));
        typeDefToNative.put("HANDLE", new Type("long", true, ""));

        typeDefToNative.put("PVOID", new Type("byte", true, "*", true));
        typeDefToNative.put("LPVOID", new Type("byte", true, "*", true));
        typeDefToNative.put("LPCSTR", new Type("char", true, "*", true));
        typeDefToNative.put("LPCWSTR", new Type("char", true, "*", true));
        typeDefToNative.put("PWSTR", new Type("char", true, "*", true));
        typeDefToNative.put("LPWSTR", new Type("char", true, "*", true));
        typeDefToNative.put("DWORD", new Type("long", true, "", false));
        typeDefToNative.put("PDWORD", new Type("long", true, "*", true));
        typeDefToNative.put("LPDWORD", new Type("long", true, "*", true));
        typeDefToNative.put("LONG", new Type("long", true, ""));
        typeDefToNative.put("LONG64", new Type("long", true, ""));
        typeDefToNative.put("ULONG64", new Type("long", true, ""));
        typeDefToNative.put("LONGLONG", new Type("long", true, ""));
        typeDefToNative.put("ULONGLONG", new Type("long", true, ""));
        typeDefToNative.put("BOOLEAN", new Type("boolean", true, ""));
        typeDefToNative.put("BOOL", new Type("boolean", true, ""));
        typeDefToNative.put("PBOOL", new Type("boolean", true, "*"));
        typeDefToNative.put("BYTE", new Type("byte", true, ""));
        typeDefToNative.put("UINT8", new Type("byte", true, ""));
        typeDefToNative.put("UINT64", new Type("long", true, ""));
        typeDefToNative.put("SHORT", new Type("short", true, ""));
        typeDefToNative.put("BSTR", new Type("String", true, ""));
        typeDefToNative.put("WCHAR", new Type("String", true, ""));
    }

    private static final Set<String> emptyStructs = new HashSet<>();

    public static Optional<Path> preprocess(Path headerPath, Path dest, List<String> preProcArgs)
    {
        return CPreprocess.preprocess(headerPath, dest, preProcArgs.toArray(new String[0]));
    }

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
        List<String> preProcArgs = new ArrayList<>(Arrays.asList(args).subList(3, args.length));
        var processedFile = CPreprocess.preprocess(headerPath, destinationPath, preProcArgs.toArray(new String[0]));

        if (processedFile.isEmpty()) {
            System.err.println("Preprocessor step failed - no further generation is possible.");
            return;
        }

        var preProcHeader = processedFile.get();
        System.out.println("Preprocessed file: " + preProcHeader);
        String header = readC(preProcHeader);


//        String header = Files.readString(preProcHeader);
//        Files.delete(preProcHeader);

        Arrays.fill(counts, 0);

        parseTypedefs(header);
        functions = parseFunctions(header);
        records.addAll(parseRecords(header));
        enums.addAll(parseEnums(header));

        generateInterface(functions);
//        generateJava();
        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[OUTPUT_OBJECTS.methods.ordinal()]);
    }

    private static String readC(Path source) throws IOException
    {
        var lines = Files.readAllLines(source);
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
        {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#line") || line.startsWith("#pragma"))
                continue;
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public static void readCPP(Path cpp, Path dest, String pack) throws IOException
    {
        packageName = pack;
        destinationPath = dest;
        interfaceName = cpp.getFileName().toString().split("\\.")[0];

        String header = readC(cpp);
//        String header = Files.readString(cpp);
        parseTypedefs(header);
        functions = parseFunctions(header);
        records.addAll(parseRecords(header));
        enums.addAll(parseEnums(header));

        generateInterface(functions);

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

    // ============================================================
    // FUNCTION PARSING
    // ============================================================

    static List<CFunction> parseFunctions(String text) {
        text = text.replaceAll("\\bextern\\b", ""); // ignore extern
        text = text.replaceAll("\\volatile\\b", ""); // ignore extern
        text = text.replaceAll("\\const\\b", ""); // ignore extern

        List<CFunction> list = new ArrayList<>();

        Pattern p = Pattern.compile(
                "([A-Za-z_][A-Za-z0-9_\\*\\s]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*;",
                Pattern.MULTILINE
        );

        Matcher m = p.matcher(text);
        int argID = 1;
        while (m.find()) {
            String returnType = m.group(1).trim();
            String name = m.group(2).trim();
            String params = m.group(3).trim();

            //ignore functions declared in the header
            if (returnType.trim().equals("return"))
                continue;

            if (name.isEmpty())
                name = "arg" + argID;
            argID++;

            List<ArgDef> parameters = new ArrayList<>();
            if (!params.isEmpty() && !params.equalsIgnoreCase("void")) {
                int count = 1;
                for (String param : params.split(",")) {
                    var arg = splitArg(param, count++);
                    if (arg.isPresent())
                        parameters.add(arg.get());

                }
            }

            //The windows API is batshit for this directive, and we never need these methods
            if (returnType.contains("__cdecl"))
                continue;

            String CDeclaration = returnType + " " + name + "(" + params + ");";
            var retArg = splitArg(returnType, 0);
            if (retArg.isPresent())
                list.add(new CFunction(retArg.get(), name, parameters, CDeclaration));

            if (!warnings.isEmpty()) {
                System.out.println("Warnings parsing function: " + name);
                warnings.stream().forEach(s -> System.out.println("\t" + s));
                warnings.clear();
            }
        }

        return list;
    }

    static boolean isAllPtr(String def) {
        if (def.chars().filter(ch -> ch == '*').count() == def.length())
            return true;

        if (def.startsWith("[") && def.endsWith("]"))
            return true;
        return false;
    }

    record CAlias(String alias, String replace) {

        String clean(String orig) {
            String regEx = "\\s*" + alias.replace(" ", "\\s+") + "(\\*+\\s+|\\s+)";
            Pattern p = Pattern.compile(regEx, Pattern.DOTALL);

            Matcher m = p.matcher(orig);
            while (m.find()) {
                String found = m.group(0);
                int ptrCount = found.length() - found.replace("*", "").length();

                if (ptrCount == 0)
                    orig = orig.replace(found, " " + replace + " ");
                else {
                    String ptrs = "";
                    for (int i = 0; i < ptrCount; i++)
                        ptrs = ptrs + "*";

                    orig = orig.replace(found, " " + replace + ptrs + " ");

                }

            }

            return orig;
        }
    }


    private static String cleanCommonAliases(String orig) {
        for (var c : commonAliases) {
            orig = c.clean(orig);
        }
        return orig;
    }

    static String[] ignoreKeywords = new String[] {
            "const",
            "__stdcall ",
            "typedef ",
            "volatile ",
            "unsigned ",
            "signed ",
            "__unaligned ",
            "enum ",
            "IN ",
            "OUT ",
            "_In_ ",
            "_Out_ ",
            "_Outptr_ ",
            "_Inout_ ",
            "_In_opt_",
            "_Inout_opt_",
            "static ",
            "_Reserved_"
    };

    static Optional<ArgDef> splitArg(String argDef, int countArg) {
        argDef = argDef.trim();
        for (String remove : ignoreKeywords)
            argDef = argDef.replace(remove, "");

        argDef = cleanCommonAliases(argDef);


        if (argDef.isEmpty()) {
            warnings.add("WARNING: there seems to be a problem with an argument definition. Is there an extra comma?");
            return Optional.empty();//new ArgDef("error", "error", "", true, argDef);
        }

        if (argDef.contains(" * "))
            argDef = argDef.replace(" * ", "* ");
        argDef = argDef.replace("struct ", " ");
        if (argDef.equals("void"))
            argDef = "";
        String[] parts = argDef.split("\\s+");

        String prtDetails = "";
        if (parts.length > 2) {
            prtDetails = Arrays.stream(parts).filter(HeaderToPassport::isAllPtr).findFirst().orElse("");
            String cdetails = prtDetails;
            parts = Arrays.stream(parts).filter(s -> !s.equals(cdetails)).toList().toArray(new String[0]);
        }

        //meant to catch arguments that are just defined by type with no name - common in win32 api
        if (parts.length == 1)
            parts = new String[] {parts[0], "arg" + countArg, };

        if (parts.length == 1 || parts.length > 3) {
            warnings.add("ERROR: I don't know how to parse the argument: " + argDef);
            return Optional.empty();//new ArgDef("error", "error", "", true, argDef);
        }

        String type = parts[0].replace("[]", "*");
        String name = parts[1].replace("[]", "*");
        type = type.replace("&", "*");
        name = name.replace("&", "*");
        String ptr = prtDetails;
        while (type.endsWith("*")) {
            ptr = ptr + "*";
            type = type.substring(0, type.length() - 1);
        }

        while (name.endsWith("*")) {
            ptr = ptr + "*";
            name = name.substring(0, name.length() - 1);
        }

        while (name.startsWith("*")) {
            ptr = ptr + "*";
            name = name.substring(1);
        }

        if (type.contains("[")) {
            int start = type.indexOf("[");
            int end = type.lastIndexOf("]");
            ptr = type.substring(start, end + 1);
            type = type.replace(ptr, "");
        }

        if (name.contains("[")) {
            int start = name.indexOf("[");
            int end = name.lastIndexOf("]");
            ptr = name.substring(start);
            name = name.replace(ptr, "");
        }
        return Optional.of(new ArgDef(type, name, ptr, false, argDef));
    }

    // ============================================================
    // STRUCT / UNION PARSING
    // ============================================================

    static void parseTypedefs(String text) {

        Pattern p = Pattern.compile(
                "typedef\\s*(unsigned\\s+short|short|" +
                        "unsigned\\s+int|long\\s+int|long\\s+long\\s+int|long\\s+long\\s+unsigned\\s+int|int|" +
                        "unsigned\\s+long\\s+long|long\\s+long|unsigned\\s+long|long|" +
                        "unsigned\\s+char|char|" +
                        "float|" +
                        "long\\s+double|double|" +
                        "bool|(?:[A-Za-z_][A-Za-z0-9_]*)?)\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?;",
                Pattern.DOTALL);

        Matcher m = p.matcher(text);
        while (m.find()) {

            String typeDefOrig = m.group();
            String cleanTypeDef = cleanCommonAliases(typeDefOrig);
            String[] parts = cleanTypeDef.split("\\s+");

            String type = parts[1];
            String name = parts[2];

            if (name.endsWith(";"))
                name = name.substring(0, name.length() - 1);

            if (!typeDefToNative.containsKey(name)) {
                Type t;
                if (typeDefToNative.containsKey(type)) {
                    t = typeDefToNative.get(type);
                    typeDefToNative.put(name, t);
                } else
                    warnings.add("WARNING: typdef " + name + " cannot be found.");
            }
        }
    }


    static List<CRecord> parseRecords(String text) {
        List<CRecord> list = new ArrayList<>();
        Set<String> foundRecords = new HashSet<>();

        Pattern p = Pattern.compile(
                "(struct|union)\\s*(?:[A-Za-z_][A-Za-z0-9_]*)?\\s*\\{([^}]*)}\\s*([A-Za-z_][A-Za-z0-9_\\*\\,]*)\\s*;",
                Pattern.DOTALL
        );

        Pattern subP = Pattern.compile(
                "(_Field_size_|_Field_size_bytes_|_Field_size_opt_|_Field_size_bytes_)\\((?:[A-Za-z_][A-Za-z0-9_]*)?\\)"
        );

        Matcher m = p.matcher(text);
        while (m.find()) {
            List<ArgDef> fields = new ArrayList<>();
            String kind = m.group(1);
            String body = m.group(2).trim();
            String name = m.group(3).trim();

            int startRec = text.indexOf(body);
            int endRecAt = 0;
            if (body.contains("union") || body.contains("struct")) {
                int openCount = 1;
                for (int n = startRec; n < text.length(); ++n)
                {
                    if (text.charAt(n) == '{')
                        openCount++;
                    else if (text.charAt(n) == '}')
                    {
                        openCount--;
                        if (openCount == 0)
                        {
                            endRecAt = text.indexOf(';', n);
                            break;
                        }
                    }
                }
                String fullRec = text.substring(startRec, endRecAt + 1);
                var recs = parseRecords(fullRec);
                list.addAll(recs);

                for (var rec : recs)
                    body = body.replace(rec.CCode, rec.name + " " + rec.name + ";");
            }

            var m2 = subP.matcher(body);
            while (m2.find())
            {
                String matched = m2.group();
                body = body.replace(matched, "");
            }

            int argCount = 1;
            for (String line : body.split(";")) {
                var arg = splitArg(line, argCount++);
                if (arg.isPresent())
                    fields.add(arg.get());
            }

            //many structs in win32 have a point based name at the end, this helps with the conversion
            if (name.contains(","))
            {
                var parts = name.split(",");
                String baseName = parts[0];
                list.add(new CRecord(baseName, kind, fields, m.group()));

                for (int n = 1; n < parts.length; ++n)
                {
                    var structName = parts[n];
                    structName = structName.trim();
                    if  (structName.contains("NEAR"))
                        structName = structName.replace("NEAR", "").trim();
                    if  (structName.contains("FAR"))
                        structName = structName.replace("FAR", "").trim();

                    if (structName.startsWith("*"))
                        structName = structName.replace("*", "");

                    typeDefToNative.put(structName, new Type(baseName, true, "*", true));
                }

            }
            else
                list.add(new CRecord(name, kind, fields, m.group()));

            foundRecords.add(name);
        }

        //this code is to handle empty records, which are usually just names for pointers
        Pattern p2 = Pattern.compile(
                "typedef\\s+struct\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?;",
                Pattern.DOTALL
        );

        Matcher m2 = p2.matcher(text);
        while (m2.find()) {
            String kind = m2.group();
            String[] parts = kind.split(" ");
            String name = parts[parts.length - 1];
            name = name.replace(";", "");

            if (name.contains(":"))
                name = name.substring(0, kind.indexOf(":"));

            list.add(new CRecord(name, "struct", new ArrayList<>(), kind));
            if (!foundRecords.contains(name))
                emptyStructs.add(name);
        }

        return list;
    }

    // ============================================================
    // ENUM PARSING
    // ============================================================

    static List<CEnum> parseEnums(String text) {
        List<CEnum> list = new ArrayList<>();

        Pattern p = Pattern.compile(
                "(?:typedef\\s+)?enum\\s*(?:[A-Za-z_][A-Za-z0-9_]*)?\\s*\\{([^}]*)}\\s*([A-Za-z_][A-Za-z0-9_]*)?\\s*;",
                Pattern.DOTALL
        );

        Matcher m = p.matcher(text);
        while (m.find()) {
            String body = m.group(1).trim();
            String name = m.group(2);
            if (name == null || name.isEmpty()) {
                name = "UnnamedEnum" + list.size();
            }

            List<CEnumValue> values = new ArrayList<>();
            long currentValue = 0;

            for (String raw : body.split(",")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("=");
                String enumName = parts[0].trim();

                Long value;
                boolean forceLong = false;
                if (parts.length == 2) {
                    try {
                        String number = parts[1].trim();
                        if (number.toLowerCase().endsWith("l")) {
                            number = number.substring(0, number.length() - 1);
                            forceLong = true;
                        }

                        if (number.startsWith("0x"))
                            value = Long.decode(number);
                        else
                            value = Long.parseLong(parts[1].trim());
                    } catch (NumberFormatException e) {
                        value = currentValue;
                    }
                    currentValue = value + 1;
                } else {
                    value = currentValue++;
                }

                if (!forceLong)
                    forceLong = value < Integer.MIN_VALUE || value > Integer.MAX_VALUE;
                values.add(new CEnumValue(enumName, value, forceLong));
            }

            list.add(new CEnum(name, values, m.group()));
        }

        return list;
    }

    // ============================================================
    // JAVA GENERATION
    // ============================================================

    public static void generateJava() throws FileNotFoundException {

        // ---- Structs / Unions → Records ----
        generateJavaRecords(records);

        // ---- Enums ----
        generateJavaEnums(enums);

//        generateInterface(functions);
    }

    private static void generateInterface(List<CFunction> funcs) throws FileNotFoundException {
        if (funcs.isEmpty())
            return;

        Path outFile = destinationPath.resolve(interfaceName + ".java");
        try (PrintWriter out = new PrintWriter(outFile.toFile())) {
            out.println("package " + packageName + ";\n\n");
            out.println("import java.lang.foreign.MemorySegment;");
            out.println("import jpassport.pointers.*;");
            out.println("import jpassport.annotations.*;\n\n");

            // ---- Functions → Interface ----
            out.println("public interface " + interfaceName + "{");
            for (CFunction f : funcs) {

                if (typesWritten.contains(f.name) || f.returnType.origText.trim().equals("return"))
                    continue;

                warnings.clear();
                out.println("/*");
                out.println(f.CCode);
                out.println("*/");
                out.print("    " + mapType(f.returnType, true) + " " + f.name + "(");
                StringBuilder params = new StringBuilder();
                int i = 1;
                for (int n = 0; n < f.parameters.size(); ++n)
                {
                    var p = f.parameters.get(n);
                    if (p.name.isEmpty())
                        p = new ArgDef(p.type, "arg" + i, p.ptr, p.error, p.origText);
                    if (p.type.equals("..."))
                    {
                        var pp  = f.parameters.get(n-1);
                        p = new ArgDef(pp.type, "arg" + i, pp.ptr, p.error, p.origText);
                    }
                    params.append(formatParam(p)).append(",");
                    i++;
                }
                if (f.parameters.size() > 0)
                    params.setLength(params.length()-1);
//                String params = String.join(", ",
//                        f.parameters.stream()
//                                .map(HeaderToPassport::formatParam)
//                                .toList()
//                );
                out.print(params);
                out.println(");");
                out.println();
                counts[OUTPUT_OBJECTS.methods.ordinal()]++;
                if (!warnings.isEmpty()) {
                    System.err.println("WARNINGS found for method: " + f.name);
                    warnings.forEach(warning -> System.err.println("\t" + warning));
                }
                typesWritten.add(f.name);
            }
            out.println("}");
        }
    }

    private static void generateJavaRecords(List<CRecord> records) throws FileNotFoundException {
        for (CRecord r : records) {

            if (typesWritten.contains(r.name))
                continue;

            warnings.clear();
            Path outFile = destinationPath.resolve(r.name.replace("*", "") + ".java");
            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.*;");
                out.println("import jpassport.pointers.*;");
                out.println("import jpassport.annotations.*;\n\n");

                out.println("/*");
                out.println(r.CCode);
                out.println("*/");
                if (r.isEmptyStruct()) {
                    out.println("import java.lang.foreign.MemorySegment;\n\n");
                    out.println("public class " + r.name + " extends GenericPointer {");
                    out.println("public " + r.name + "(MemorySegment addr) {\n" +
                            "        super(addr);\n" +
                            "    }");
                    out.println("}");
                } else {

//                    if (r.name.equals("WHV_DOORBELL_MATCH_DATA"))
//                        System.out.println();
                    out.println("public record " + r.name + " (");
                    for (int i = 0; i < r.fields.size(); i++) {
                        var f = r.fields.get(i);
                        String name = f.name;
                        if (name.contains(":"))
                            name = name.substring(0, name.indexOf(":"));
                        out.print("    " + mapRecordType(f) + " " + name);
                        if (i < r.fields.size() - 1) out.println(",");
                    }

                    if (r.kind.equalsIgnoreCase("union"))
                        out.println("    ,UnionFieldIO unionIO\n");
                    out.println("\n) {}\n");
                }

                counts[OUTPUT_OBJECTS.records.ordinal()]++;
                if (!warnings.isEmpty()) {
                    System.err.println("WARNINGS found for record: " + r.name);
                    warnings.forEach(warning -> System.err.println("\t" + warning));
                }

                typesWritten.add(r.name);
            }
        }
    }

    static boolean allSequential(List<CEnumValue> enums) {
        long curVal = 0;
        for (var e : enums) {
            if (e.value != curVal)
                return false;
            curVal++;
        }
        return true;
    }

    static void generateJavaEnums(List<CEnum> enums) throws FileNotFoundException {
        for (CEnum e : enums) {
            if (typesWritten.contains(e.name))
                continue;

            boolean useLongs = e.values.stream().anyMatch(v -> v.forceLong);
            boolean isSequential = allSequential(e.values);

            Path outFile = destinationPath.resolve(e.name.replace("*", "") + ".java");

            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.enums.EnumInt;\n");
                out.println("import jpassport.enums.EnumLong;\n");

                boolean useValues = !isSequential || useLongs;

                out.println("/*");
                out.println(e.CCode);
                out.println("*/");

                if (isSequential && !useLongs)
                    out.println("public enum " + e.name + " {");
                else if (useLongs)
                    out.println("public enum " + e.name + " implements EnumLong {");
                else
                    out.println("public enum " + e.name + " implements EnumInt {");

                for (int i = 0; i < e.values.size(); i++) {
                    CEnumValue v = e.values.get(i);
                    if (useValues) {
                        if (useLongs)
                            out.printf("    %s(%dL), //0x%x\n", v.name, v.value, v.value);
                        else
                            out.printf("    %s(%d), //0x%x\n", v.name, v.value, v.value);
                    } else
                        out.print("    " + v.name + ",\n");

//                    if (i < e.values.size() - 1) out.println(",");
                }
                out.println(";");

                if (useValues) {

                    if (useLongs) {
                        out.println("\n    public final long value;");
                        out.println("\n    " + e.name + "(long v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public long getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    } else {
                        out.println("\n    public final int value;");
                        out.println("\n    " + e.name + "(int v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public int getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    }
                }

                out.println("}\n");
                counts[OUTPUT_OBJECTS.enums.ordinal()]++;
                typesWritten.add(e.name);
            }
        }
    }

    // ============================================================
    // PARAMETER FORMATTER
    // ============================================================

    static String formatParam(ArgDef p) {
        if (p.error)
            return p.origText;

        long ptrCount = p.ptr.chars().filter(ch -> ch == '*' || ch == '[').count();
        String javaType = mapType(p, false);

        long ptrCount2 = javaType.chars().filter(ch -> ch == '*' || ch == '[').count();
        if (ptrCount2 > ptrCount)
            ptrCount = ptrCount2;

        if (ptrCount > 2)
            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        if (ptrCount == 2)
            return "@PtrPtrArg " + javaType + " " + p.name;
//        else if (ptrCount == 1 && !emptyStructs.contains(javaType)) {
//            return "@RefArg " + javaType + " " + p.name;
//        }
        else {
            return javaType + " " + p.name;
        }
    }

    // ============================================================
    // TYPE MAPPING
    // ============================================================

    static String mapType(ArgDef def, boolean isReturn) {
        if (def.error)
            return def.origText;

        String t = def.type.trim();
        String ptr = def.ptr;
        if (typeDefToNative.containsKey(t)) {
            //Empty structs are just GenericPointers. So if we are not an empty struct then do not change the pointer
            //count. This preserves things like "uint16 *myarg" which should be treated as a java array
            if (emptyStructs.contains(t) || typeDefToNative.get(t).ptrRequired)
                ptr = typeDefToNative.get(t).ptr;
            t = typeDefToNative.get(t).name;
        }

        long ptrCount = ptr.chars().filter(ch -> ch == '*').count();

        if (isReturn && ptrCount > 0)
            return "MemorySegment";
        if (isReturn && def.type.isEmpty())
            return "void";

        if (ptrCount > 2)
            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        String base = t.replaceAll("\\s+", " ");

        if (typeDefToNative.containsKey(base))
            base = typeDefToNative.get(base).name;

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

    static String mapRecordType(ArgDef cType) {
        if (cType.error)
            return cType.origText;

        String t = cType.type.trim();
        long ptrCount = cType.ptr.chars().filter(ch -> ch == '*').count();

        if (ptrCount > 2)
            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        String base = t.replaceAll("\\s+", " ");

        if (typeDefToNative.containsKey(base))
            base = typeDefToNative.get(base).name;

        if (base.startsWith("unsigned"))
            base = base.substring("unsigned".length());
        if (base.startsWith("long long"))
            base = "long" + base.substring("long long".length());
        if (base.startsWith("long double"))
            base = "double" + base.substring("long double".length());

        String scalar = switch (base) {
            case "int" -> "int";
            case "short" -> "short";
            case "long" -> "long";
            case "long long" -> "long";
            case "float" -> "float";
            case "double" -> "double";
            case "long double" -> "double";
            case "bool" -> "boolean";
            case "char" -> "byte";
            case "void" -> "void";
            default -> base;
        };

        String annotation = "";
        if (ptrCount == 2)
            annotation = "@PtrPtrArg ";
        else if (ptrCount == 1)
            annotation = "@Ptr ";
        else if (cType.ptr.contains("[")) {
            var matcher = pattArray.matcher(cType.ptr);
            String number = "";
            if (matcher.find()) {
                String b = matcher.group(0).trim();
                number = b.substring(1, b.length() - 1);
                scalar = scalar.replace(b, "[]");
            }
            annotation = "@Array(length = " + number + ") ";
        }

        if (ptrCount > 0) {
            if (base.equals("char")) {
                return "String";
            } else if (base.equals("void")) {
                return "byte[]";
            } else {
                return annotation + scalar + "[]";
            }
        }


        return annotation + scalar;
    }

    // ============================================================
    // MODEL CLASSES
    // ============================================================

    record ArgDef(String type, String name, String ptr, boolean error, String origText){}

    record CFunction(ArgDef returnType,
            String name,
            List<ArgDef> parameters,
             String CCode) {
    }


    record CRecord(String name, String kind, List<ArgDef> fields, String CCode) {

        //in many C APIs you will find code like:
        // typedef struct MyStruct MyStruct;
        // This allows the library maker to hide the fields of the struct
        //These typedefs are always used as pointers only and can therefore
        //be converted to GenericPointer types.
        public boolean isEmptyStruct() {
            return this.fields.isEmpty();
        }
    }

    record CEnum(String name, List<CEnumValue> values, String CCode) {
    }

    record CEnumValue (
        String name,
        Long value,
        boolean forceLong){}

    record Type(String name, boolean isNative, String ptr, boolean ptrRequired){
        public Type(String name, boolean isNative, String ptr)
        {
            this(name, isNative, ptr, false);
        }
    }
}