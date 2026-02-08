package jpassport.parser;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * TODO:
  *  -Handle typedefs
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
    static List<Path> includeFolders = new ArrayList<>();
    static List<Path> excludeFolders = new ArrayList<>();
    static List<String> warnings = new ArrayList<>();

    enum OUTPUT_OBJECTS{methods, records, enums}
    static int[] counts = new int[OUTPUT_OBJECTS.values().length];

    //find arguments defined in C as arg[row][col]
    static Pattern pattArray = Pattern.compile(
            "\\[.*?\\]",
            Pattern.DOTALL
    );


    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: [path to header] [path to destination] [package] [extra include folders " + File.pathSeparator +" delimited]");
            System.exit(1);
        }

        Path headerPath = Path.of(args[0]);
        String headerName = headerPath.getFileName().toString();
        destinationPath = Path.of(args[1]);
        packageName = args[2];
        interfaceName = headerName.replace(".", "_");


        var folderNames = args[3].split(File.pathSeparator);
        includeFolders.add(headerPath.getParent());
        for (String folderName : folderNames) {
            includeFolders.add(Path.of(folderName));
        }

        validateArguments(headerPath);

        Path baseDir = headerPath.getParent();
        String header = loadHeaderRecursive(baseDir, headerName, new HashSet<>());

        Path tmpHeader = CPreprocess.createTmpHeader(header);
        System.out.println("Tmp file created: " + tmpHeader);
        String[] extraArgs = new String[args.length - 4];
        for (int n = 4; n < args.length; n++)
            extraArgs[n-4] = args[n];
        var processedFile = CPreprocess.preprocess(tmpHeader, extraArgs);
        System.out.println("Preprocessed file: " + processedFile);
        Files.delete(tmpHeader);

        if (processedFile.isPresent()) {
            header = Files.readString(processedFile.get());
            Files.delete(processedFile.get());
        }
        else
        {
            System.err.println("Preprocessor step failed - no further generation is possible.");
            return;
        }

        parseTypedefs(header);
        List<CFunction> functions = parseFunctions(header);
        List<CRecord> records = parseRecords(header);
        List<CEnum> enums = parseEnums(header);

        generateJava(records, enums, functions);
        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[OUTPUT_OBJECTS.methods.ordinal()]);
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
    // INCLUDE HANDLING
    // ============================================================

    static class Include {
        String file;
        boolean isUserHeader;
        Include(String f, boolean u) { file = f; isUserHeader = u; }
    }

    static List<Include> parseIncludes(String text) {
        List<Include> includes = new ArrayList<>();

        Pattern p = Pattern.compile("#include\\s+([\"<])([^\">]+)([\">])");
        Matcher m = p.matcher(text);

        while (m.find()) {
            String open = m.group(1);
            String file = m.group(2).trim();
            boolean isUser = open.equals("\"");
            includes.add(new Include(file, isUser));
        }

        return includes;
    }

    static String loadHeaderRecursive(Path baseDir, String fileName, Set<String> visited) throws Exception {
        if (visited.contains(fileName)) {
            return "";
        }
        visited.add(fileName);

        Path file = baseDir.resolve(fileName);
        System.out.println("Including: " + file);
        String text = Files.readString(file);

        //find multi line comments and remove them
        var multiline = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        Matcher m = multiline.matcher(text);
        while (m.find()) {
            var section = m.group(0);
            text = text.replace(section, "");
        }

        //find single line comments or trailing comments and remove them
        var lines = text.lines().toList();
        StringBuilder sb = new StringBuilder();
        for (String s : lines)
        {
            int idx=  s.indexOf("//");
            //In blis.h I found #line 1 ".//file.h" - If the // is in a preprocessor then ignore it
            if (idx > 0 && !s.trim().startsWith("#"))  //the comment starts somewhere on the line, so clip off the end of the line
                s = s.substring(0, idx);
            else if (idx == 0) //if the line starts with a comment then just throw it away
                continue;

            sb.append(s).append("\n");
        }

        //there should be no comments left in this text
        text = sb.toString();
        return text;
    }

    // ============================================================
    // FUNCTION PARSING
    // ============================================================

    static List<CFunction> parseFunctions(String text) {
        text = text.replaceAll("\\bextern\\b", ""); // ignore extern

        List<CFunction> list = new ArrayList<>();

        Pattern p = Pattern.compile(
                "([A-Za-z_][A-Za-z0-9_\\*\\s]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*;",
                Pattern.MULTILINE
        );

        Matcher m = p.matcher(text);
        while (m.find()) {
            String returnType = m.group(1).trim();
            String name = m.group(2).trim();
            String params = m.group(3).trim();

            List<ArgDef> parameters = new ArrayList<>();
            if (!params.isEmpty() && !params.equals("void")) {
                for (String param : params.split(",")) {
                    parameters.add(splitArg(param));
                }
            }

            if (returnType.contains("__cdecl"))
                continue;
            list.add(new CFunction(splitArg(returnType + " dummy"), name, parameters));

            if (!warnings.isEmpty())
            {
                System.out.println("Warnings parsing function: " + name);
                warnings.stream().forEach(s -> System.out.println("\t" + s));
                warnings.clear();
            }
        }

        return list;
    }

    static boolean isAllPtr(String def)
    {
        if ( def.chars().filter(ch -> ch == '*').count() == def.length() )
            return true;

        if (def.startsWith("[") && def.endsWith("]"))
            return true;
        return false;
    }

    record CAlias(String alias, String replace){

        String clean(String orig) {
            String regEx = "\\s*" + alias.replace(" ", "\\s+") + "(\\*+\\s+|\\s+)";
            Pattern p = Pattern.compile(regEx, Pattern.DOTALL);

            Matcher m = p.matcher(orig);
            while (m.find()) {
                String found = m.group(0);
                int ptrCount = found.length() - found.replace("*", "").length();

                if (ptrCount == 0)
                    orig = orig.replace(found, " " + replace + " ");
                else{
                    String ptrs = "";
                    for (int i = 0; i < ptrCount; i++)
                        ptrs = ptrs + "*";

                    orig = orig.replace(found, " " + replace + ptrs + " ");

                }

            }

            return orig;
        }
    }

    private static final List<CAlias> commonAliases = List.of(
            new CAlias("unsigned char", "byte"),
            new CAlias("unsigned short", "short"),
            new CAlias("unsigned int", "int"),
            new CAlias("unsigned long", "long"),
            new CAlias("unsigned long long", "long"),
            new CAlias("unsigned long long int", "long"),
            new CAlias("long long unsigned int", "long"),
            new CAlias("long long int", "long"),
            new CAlias("long long", "long"),
            new CAlias("long double", "double"),
            new CAlias("size_t", "long")
    );

    private static String cleanCommonAliases(String orig) {
        for (var c : commonAliases) {
            orig = c.clean(orig);
        }
        return orig;
    }

    static ArgDef splitArg(String argDef)
    {
        argDef = argDef.trim();
        argDef = argDef.replace("const ", "");
        argDef = argDef.replace("__stdcall ", "");
        argDef = cleanCommonAliases(argDef);


        if (argDef.isEmpty()){
            warnings.add("WARNING: there seems to be a problem with an argument definition. Is there an extra comma?");
            return new ArgDef("error", "error", "", true, argDef);
        }

        String[] parts = argDef.split("\\s+");

        String prtDetails = "";
        if (parts.length > 2) {
            prtDetails = Arrays.stream(parts).filter(HeaderToPassport::isAllPtr).findFirst().orElse("");
            String cdetails = prtDetails;
            parts = Arrays.stream(parts).filter(s -> !s.equals(cdetails)).toList().toArray(new String[0]);
        }

        if (parts.length != 2)
        {
            warnings.add("ERROR: I don't know how to parse the argument: " + argDef);
            return new ArgDef("error", "error", "", true,  argDef);
        }

        String type = parts[0].replace("[]", "*");
        String name = parts[1].replace("[]", "*");
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

        if (type.contains("["))
        {
            int start = type.indexOf("[");
            int end = type.lastIndexOf("]");
            ptr = type.substring(start, end+1);
            type = type.replace(ptr, "");
        }

        if (name.contains("["))
        {
            int start = name.indexOf("[");
            int end = name.lastIndexOf("]");
            ptr = name.substring(start, end+1);
            name = name.replace(ptr, "");
        }
        return new ArgDef(type, name,  ptr, false, argDef);
    }

    // ============================================================
    // STRUCT / UNION PARSING
    // ============================================================

    static void parseTypedefs(String text) {
        //"typedef\\s*(long|int|char|short)\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?";

//        "typedef\\s*(long|int|char|short|float|double|long long|long double|unsigned long long|long int|unsigned)\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?;",

//            Pattern p = Pattern.compile(
//                "typedef\\s*(unsigned\\s+short|short|" +
//                        "unsigned\\s+int|long\\s+int|int|" +
//                        "unsigned\\s+long\\s+long|long\\s+long|unsigned\\s+long|long|" +
//                        "unsgined\\s+char|char|" +
//                        "float|" +
//                        "long\\s+double|double|" +
//                        "bool)\\s+(?:[A-Za-z_][A-Za-z0-9_]*)?;",
//                Pattern.DOTALL);

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

            if (!typeDefToNative.containsKey(name))
            {
                Type t;
                if (typeDefToNative.containsKey(type))
                {
                    t = typeDefToNative.get(type);
                    typeDefToNative.put(name, t);
                }
                else
                    warnings.add("WARNING: typdef " + name + " cannot be found.");
            }
        }
    }

    record Type(String name, boolean isNative, String ptr){}

    private static HashMap<String, Type> typeDefToNative = new HashMap<>();

    static {
        typeDefToNative.put("char", new Type("char", true, ""));
        typeDefToNative.put("short", new Type("short", true, ""));
        typeDefToNative.put("int", new Type("int", true, ""));
        typeDefToNative.put("long", new Type("long", true, ""));
        typeDefToNative.put("float", new Type("float", true, ""));
        typeDefToNative.put("double", new Type("double", true, ""));
        typeDefToNative.put("size_t", new Type("long", true, ""));
        typeDefToNative.put("HANDLE", new Type("long", true, ""));

        typeDefToNative.put("PVOID", new Type("byte", true, "*"));
        typeDefToNative.put("LPVOID", new Type("byte", true, "*"));
        typeDefToNative.put("LPCSTR", new Type("char", true, "*"));
        typeDefToNative.put("LPCWSTR", new Type("char", true, "*"));
        typeDefToNative.put("PDWORD", new Type("long", true, "*"));
        typeDefToNative.put("ULONG64", new Type("long", true, ""));
        typeDefToNative.put("BOOLEAN", new Type("boolean", true, ""));
        typeDefToNative.put("PBOOL", new Type("boolean", true, "*"));
    }

    static List<CRecord> parseRecords(String text) {
        List<CRecord> list = new ArrayList<>();

        Pattern p = Pattern.compile(
                "(struct|union)\\s*(?:[A-Za-z_][A-Za-z0-9_]*)?\\s*\\{([^}]*)}\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*;",
                Pattern.DOTALL
        );

        Matcher m = p.matcher(text);
        while (m.find()) {
            String kind = m.group(1);
            String body = m.group(2).trim();
            String name = m.group(3).trim();

            List<ArgDef> fields = new ArrayList<>();
            for (String line : body.split(";")) {
                fields.add(splitArg(line));
            }

            list.add(new CRecord(name, kind, fields));
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
                        if (number.toLowerCase().endsWith("l"))
                        {
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

            list.add(new CEnum(name, values));
        }

        return list;
    }

    // ============================================================
    // JAVA GENERATION
    // ============================================================

    static void generateJava(List<CRecord> records, List<CEnum> enums, List<CFunction> funcs) throws FileNotFoundException {

        // ---- Structs / Unions → Records ----
        generateJavaRecords(records);

        // ---- Enums ----
        generateJavaEnums(enums);

        generateInterface(funcs);
    }

    private static void generateInterface(List<CFunction> funcs) throws FileNotFoundException {
        Path outFile = destinationPath.resolve(interfaceName + ".java");
        try (PrintWriter out = new PrintWriter(outFile.toFile())) {
            out.println("package " + packageName + ";\n\n");
            out.println("import java.lang.foreign.MemorySegment;");
            out.println("import jpassport.pointers.*;");
            out.println("import jpassport.annotations.*;\n\n");

            // ---- Functions → Interface ----
            out.println("public interface " + interfaceName  + "{");
            for (CFunction f : funcs) {
                warnings.clear();
                out.print("    " + mapType(f.returnType, true) + " " + f.name + "(");
                String params = String.join(", ",
                        f.parameters.stream()
                                .map(HeaderToPassport::formatParam)
                                .toList()
                );
                out.print(params);
                out.println(");");
                counts[OUTPUT_OBJECTS.methods.ordinal()]++;
                if (!warnings.isEmpty()) {
                    System.err.println("WARNINGS found for method: " + f.name);
                    warnings.forEach(warning -> System.err.println("\t" + warning));
                }
            }
            out.println("}");
        }
    }

    private static void generateJavaRecords(List<CRecord> records) throws FileNotFoundException {
        for (CRecord r : records) {
            warnings.clear();
            Path outFile = destinationPath.resolve(r.name + ".java");
            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.*;");
                out.println("import jpassport.pointers.*;");
                out.println("import jpassport.annotations.*;\n\n");
                out.println("public record " + r.name + " (");
                for (int i = 0; i < r.fields.size(); i++) {
                    var f = r.fields.get(i);

                    out.print("    " + mapRecordType(f) + " " + f.name);
                    if (i < r.fields.size() - 1) out.println(",");
                }

                if (r.kind.equalsIgnoreCase("union"))
                    out.println("    ,UnionFieldIO unionIO\n");
                out.println("\n) {}\n");

                counts[OUTPUT_OBJECTS.records.ordinal()]++;
                if (!warnings.isEmpty()) {
                    System.err.println("WARNINGS found for record: " + r.name);
                    warnings.forEach(warning -> System.err.println("\t" + warning));
                }
            }
        }
    }

    static boolean allSequential(List<CEnumValue> enums)
    {
        long curVal = 0;
        for (var e : enums){
            if (e.value!= curVal)
                return false;
            curVal++;
        }
        return true;
    }

    static void generateJavaEnums(List<CEnum> enums) throws FileNotFoundException {
        for (CEnum e : enums) {
            boolean useLongs = e.values.stream().anyMatch(v -> v.forceLong);
            boolean isSequential = allSequential(e.values);

            Path outFile = destinationPath.resolve(e.name + ".java");

            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.enums.EnumInt;\n");
                out.println("import jpassport.enums.EnumLong;\n");

                boolean useValues = !isSequential ||  useLongs;

                if (isSequential && !useLongs)
                    out.println("public enum " + e.name + " {");
                else if (useLongs)
                    out.println("public enum " + e.name + " implements EnumLong {");
                else
                    out.println("public enum " + e.name + " implements EnumInt {");

                for (int i = 0; i < e.values.size(); i++) {
                    CEnumValue v = e.values.get(i);
                    if (useValues){
                        if (useLongs)
                            out.print("    " + v.name + "(" + v.value + "L)");
                        else
                            out.print("    " + v.name + "(" + v.value + ")");
                    }
                    else
                        out.print("    " + v.name);

                    if (i < e.values.size() - 1) out.println(",");
                }
                out.println(";");

                if (useValues) {

                    if (useLongs)
                    {
                        out.println("\n    public final long value;");
                        out.println("\n    " + e.name + "(long v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public long getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    }
                    else {
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

            }
        }
    }

    // ============================================================
    // PARAMETER FORMATTER (adds @RefArg)
    // ============================================================

    static String formatParam(ArgDef p) {
        if (p.error)
            return p.origText;

        long ptrCount = p.ptr.chars().filter(ch -> ch == '*' || ch == '[').count();
        String javaType = mapType(p, false);

        if (ptrCount > 2)
            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        if (ptrCount == 2)
            return "@PtrPtrArg " + javaType + " " + p.name;
        else if (ptrCount ==1) {
            return "@RefArg " + javaType + " " + p.name;
        } else {
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
        if (typeDefToNative.containsKey(t))
        {
            ptr = typeDefToNative.get(t).ptr;
            t = typeDefToNative.get(t).name;
        }

        long ptrCount =  ptr.chars().filter(ch -> ch == '*').count();

        if (isReturn && ptrCount > 0)
            return "MemorySegment";

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
            case "long" -> "long";
            case "long long" -> "long";
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
        long ptrCount =  cType.ptr.chars().filter(ch -> ch == '*').count();

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
        else if (cType.ptr.contains("["))
        {
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

    static class CFunction {
        ArgDef returnType;
        String name;
        List<ArgDef> parameters;
        CFunction(ArgDef r, String n, List<ArgDef> p) {
            this.returnType = r;
            this.name = n;
            this.parameters = p;
        }
    }

//    record CParameter {
//        String type, name;
//        CParameter(String t, String n) {
//            this.type = t;
//            this.name = n;
//        }
//    }

    static class CRecord {
        String name, kind;
        List<ArgDef> fields;
        CRecord(String n, String k, List<ArgDef> f) {
            this.name = n;
            this.kind = k;
            this.fields = f;
        }
    }

//    static class CField {
//        String type, name;
//        CField(String t, String n) {
//            this.type = t;
//            this.name = n;
//        }
//    }

    static class CEnum {
        String name;
        List<CEnumValue> values;
        CEnum(String n, List<CEnumValue> v) {
            this.name = n;
            this.values = v;
        }
    }

    record CEnumValue (
        String name,
        Long value,
        boolean forceLong){}
}