package jpassport.parser;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class CPreprocess {

    enum OSType{windows, mac, nix}

    static OSType osType = null;

//    public static Path createTmpHeader(String contents) throws IOException {
//        Path p = Files.createTempFile("", ".h");
//        Files.writeString(p, contents);
//        return p;
//    }

    public static Optional<Path> preprocess(Path header, Path srcDest, String[] args) {

        // Parse args
        List<String> extraArgs = new ArrayList<>();
//        boolean noLineMarkers = true; // default: suppress #line markers
        String std = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
//            if (a.equals("--no-line-markers")) {
//                noLineMarkers = true;
//            } else if (a.equals("--with-line-markers")) {
//                noLineMarkers = false;
//            } else if (a.startsWith("--std=")) {
//                std = a.substring("--std=".length()).trim();
//            } else {
                // Pass-through flags like -I<dir>, -DNAME=VAL, -nostdinc, --sysroot=..., etc.
                extraArgs.add(a);
//            }
        }

        if (!Files.isRegularFile(header)) {
            System.err.println("Error: header file not found: " + header.toAbsolutePath());
            return Optional.empty();
        }

        if (isWindows())
            osType = OSType.windows;
        else if (isMac())
            osType = OSType.mac;
        else
            osType = OSType.nix;

        // Choose preprocessor
        String cc = choosePreprocessor();
        if (cc == null) {
            System.err.println("Error: Could not find a C preprocessor. Tried: clang, gcc, cpp.");
            System.err.println("Hint: Install LLVM/Clang or GCC and ensure it's on your PATH.");
            return Optional.empty();
        }

        // Create a temp .c translation unit that includes the header
        Path tempTU = null;
        try {
            var name = header.getFileName().toString();
            tempTU = Files.createTempFile("cpp_include", ".cpp");
//            tempTU.toFile().deleteOnExit();

            // Use absolute path with forward slashes (portable with clang/gcc on all platforms)
            String absHdr = header.toAbsolutePath().toString().replace('\\', '/');
            String tuContent = "#include \"" + absHdr + "\"\n";
            Files.write(tempTU, tuContent.getBytes(StandardCharsets.UTF_8));

            // Build command
            List<String> cmd = new ArrayList<>();


            cmd.add(cc);
//            cmd.add("-E"); // preprocess only
//            if (noLineMarkers) {
//                cmd.add("-P"); // suppress #line directives
//            }
            if (std != null && !std.isEmpty() && (cc.equals("clang") || cc.equals("gcc"))) {
                cmd.add("-std=" + std);
            }

            // Add user pass-through args (e.g., -I, -D, -nostdinc, --sysroot, -isystem)
            String includes = extraArgs.removeFirst();
            cmd.addAll(extraArgs);

            // Input file last
            cmd.add(tempTU.toString());

            System.out.println("Running command: " + tempTU.getParent().toString());
            cmd.stream().forEach(System.out::println);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("INCLUDE", includes);
            // Set working directory to the header's folder for any relative paths the preprocessor might resolve
//            pb.directory(header.toAbsolutePath().getParent().toFile());
            pb.redirectErrorStream(false);

            Process proc = pb.start();

            // Stream stdout to our stdout; stderr to our stderr
            if (!Files.exists(srcDest))
                Files.createDirectories(srcDest);
            Path processed = Files.createTempFile(srcDest, name + "_", ".h");
            var out = Files.newOutputStream(processed);
            Future<?> outPump = pumpAsync(proc.getInputStream(), out);
            Future<?> errPump = pumpAsync(proc.getErrorStream(), System.err);

            int exit = proc.waitFor();
            outPump.get();
            errPump.get();
            out.close();
            Files.delete(tempTU);
            if (exit != 0) {
                System.err.println("\nPreprocessor exited with code: " + exit);
                return Optional.empty();
            }

            return Optional.of(processed);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while running preprocessor.");
        } catch (ExecutionException e) {
            System.err.println("Stream error: " + e.getMessage());
        }

        return Optional.empty();
    }


    private static void printUsage() {
        System.out.println("Usage: java CPreprocess <header-file> [options]\n" +
                "\n" +
                "Preprocesses a C header by creating a tiny translation unit that includes it\n" +
                "and invoking a system C preprocessor (clang/gcc/cpp). Prints the preprocessed\n" +
                "output to stdout.\n" +
                "\n" +
                "Options:\n" +
                "  --no-line-markers        Suppress #line directives in output (default)\n" +
                "  --with-line-markers      Keep #line directives\n" +
                "  --std=<c11|c17|gnu11...> Pass -std=<...> to clang/gcc\n" +
                "  -I<dir>                  Add an include directory\n" +
                "  -DNAME[=VALUE]           Define a macro\n" +
                "  -nostdinc                Do not search system include paths\n" +
                "  --sysroot=<path>         Set system root for headers\n" +
                "  ...                      Any other flags will be passed to the preprocessor\n" +
                "\n" +
                "Examples:\n" +
                "  java CPreprocess include/foo.h -Iinclude -DMODE=2\n" +
                "  java CPreprocess src/lib/foo.h --cc=clang --std=c17 -I/usr/local/include\n" +
                "  java CPreprocess win/foo.h --cc=gcc -I\"C:/SDK/include\" -DDEBUG\n");
    }

    private static String pathToPreProc = null;

    private static String choosePreprocessor() {
//        if (forced != null && isOnPath(forced)) return forced;
//        if (isOnPath("clang")) return "clang";

        if (pathToPreProc == null)
        {
            if (isWindows()) {
                pathToPreProc = pathToCL(Path.of("C:\\Program Files (x86)\\Microsoft Visual Studio\\")).toAbsolutePath().toString();//only clang on windows
            }

            if (isOnPath("gcc")){
                pathToPreProc = "gcc";
            }
            if (isOnPath("cpp")) {
                pathToPreProc = "cpp";
            }
            return pathToPreProc;
        }

        return pathToPreProc;
    }

    private static boolean isOnPath(String exe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(exe, "--version");

            pb.redirectErrorStream(true);
            Process p = pb.start();
            // We do not need the output; just ensure it can start
            try (InputStream is = p.getInputStream()) {
                byte[] buf = new byte[512];
                // Drain quickly; avoid blocking if tool prints a bit
                while (is.read(buf) != -1) {
                    // ignore
                }
            }
            int exit = p.waitFor();
            // Even if the exit code is non-zero, the command exists
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path pathToCL(Path root)
    {
        var searchRoot = Path.of("C:\\Program Files (x86)\\Microsoft Visual Studio\\");
        Path clPath = null;

        var found = root.toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.equalsIgnoreCase("cl.exe");
            }
        });

        if (found != null && found.length > 0) {return found[0].toPath();};

        var subFolders = root.toFile().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        for (File f : subFolders) {
            var ret = pathToCL(f.toPath());
            if (ret != null) return ret;
        }
        return null;
    }

    private static Future<?> pumpAsync(InputStream in, OutputStream out) {
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stream-pump");
            t.setDaemon(true);
            return t;
        });
        return es.submit(() -> {
            try (BufferedInputStream bis = new BufferedInputStream(in)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = bis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                es.shutdown();
            }
        });
    }

    public static boolean isWindows() {
        var OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("win");
    }

    public static boolean isMac() {
        var OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("mac");
    }

    public static boolean isUnix() {
        return !isWindows() && !isMac();
    }

    static boolean hasWSL()
    {
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "--status");

            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = p.inputReader()) {
                String line;
                String distribution = null;
                String wslVersion = null;

                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith("Default Distribution:"))
                    {
                        String[] parts = line.split(" ");
                        if (parts.length == 2)
                            distribution = parts[1].trim();
                    }
                    if (line.startsWith("Default Version:"))
                    {
                        String[] parts = line.split(" ");
                        if (parts.length == 2)
                            wslVersion = parts[1].trim();
                    }
                }

                if (distribution == null || wslVersion == null)
                {
                    System.err.println("Error: WSL not found. Header will not be preprocessed. #defines and #ifdefs will be ignored.");
                    return false;
                }
                else if (Integer.parseInt(wslVersion) < 2)
                {
                    System.err.println("Error: WSL 1 is not supported. Header will not be preprocessed. #defines and #ifdefs will be ignored.");
                    return false;
                }

                System.out.println("WSL was found. The header file will be preprocessed.");
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
