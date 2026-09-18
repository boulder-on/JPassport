package jpassport.apibuilder;

import jpassport.Utils;
import jpassport.parser.clang.types.CXCursor;

import java.io.File;
import java.nio.file.*;
import java.util.*;

public class win32APIBuilder {

//    public static void main1(String[] args) throws Exception {
//
//        Path includeRoot = Path.of("C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0");
//        Map<String, Path> includeFoldersMap = new HashMap<>();
//        List<Path> allHeaders = new ArrayList<>();
//
//        Files.walkFileTree(includeRoot, new FileVisitor<Path>() {
//            @Override
//            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//                var ret = dir.toFile().listFiles((dir1, name) -> name.endsWith(".h") || name.endsWith(".hpp") || name.endsWith(".h++"));
//                if (ret.length > 0)
//                    includeFoldersMap.put(dir.toString(), dir);
//
//                allHeaders.addAll(Arrays.stream(ret).map(File::toPath).toList());
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//                return FileVisitResult.CONTINUE;
//            }
//        });
//
//        includeFoldersMap.put("C:/Program Files (x86)/Windows Kits/10/Include/10.0.22621.0", Path.of("C:/Program Files (x86)/Windows Kits/10/Include/10.0.22621.0/cppwinrt"));
//        includeFoldersMap.put("C:/Program Files (x86)/Windows Kits/10/Include/10.0.22621.0/cppwinrt", Path.of("C:/Program Files (x86)/Windows Kits/10/Include/10.0.22621.0/cppwinrt"));
//        System.out.println("Includes found: " +  includeFoldersMap.size());
//        String[] includeFolders = {
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0",
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\cppwinrt",
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\cppwinrt\\winrt",
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um",
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\shared",
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\ucrt",
//                "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\include"
//
//        };
//
//        String[] genArgs = {
//                "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um\\winbase.h",
//                "JWin32/src/main/java/jwin32c/winbase",
//                "jwin32b.winbase",
//        };
//
//        var headers = new ArrayList<Path>();
////        headers.add(Path.of("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\include", "stdlib.h"));
////        headers.add(Path.of("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\include", "stdio.h"));
//        headers.addAll(allHeaders);
//        int generated = 0;
//
//        Path srcDest = Path.of("D:\\code\\github\\ffm5_win32\\src\\c");
//        List<Path> generatedFiles = new ArrayList();
//
//        for (Path pHeader : headers) {
//            System.out.println("=============================================");
//            System.out.println(generated + " of " + headers.size());
//            System.out.println("=============================================");
//            generated++;
//            genArgs[0] = pHeader.toAbsolutePath().toString();
//            if (genArgs[0].contains("coroutine"))
//                continue;
//            genArgs[1] = Path.of("D:\\code\\github\\ffm5_win32\\src\\win32").toAbsolutePath().toString();
//            genArgs[2] = "win32";
//            List<String> allArgs = new ArrayList<>();
//            StringBuilder env = new StringBuilder();
//            for (int n = 0; n < includeFolders.length; n++)
//               env.append(includeFolders[n]).append(";");
////            for (String f : includeFolders) {
////                allArgs.add("-I" + f);
////            }
//            allArgs.add(env.toString());
//            allArgs.add("-D_AMD64_");
//            allArgs.add("-Wignored-pragma-intrinsic");
//            allArgs.add("-Wignored-attributes");
////            allArgs.add("-D__cplusplus");
////            allArgs.add("-D_STL_DISABLE_CLANG_WARNINGS");
////            allArgs.add("-Wc++23-extensions");
////            allArgs.add("-Wno-nonportable-include-path");
//            var progArgs = allArgs.toArray(new String[0]);
//
//            var processed = HeaderToPassport.preprocess(pHeader, srcDest, allArgs);
//            if (processed.isPresent())
//                generatedFiles.add(processed.get());
//        }
////        HeaderToPassport.generateJava();
//    }

    public static void main(String[] args) throws Throwable {

        List<File> hpaths = List.of(new File("C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um"),
                new File("C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\ucrt"),
                new File("C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\shared"));
        Path destLoc = Path.of("D:\\code\\github\\ffm5_win32\\src\\jwin32_b");
        if (!Files.exists(destLoc))
            Files.createDirectories(destLoc);

        List<String> win32Headers = new ArrayList<>();
        win32Headers.add("winnt.h");
//        win32Headers.add("wctype.h");
//        win32Headers.add("winnt.h");
//        win32Headers.add("ctype.h");

//        win32Headers.add("winbase.h");

//        win32Headers.add("advpub.h");
//        win32Headers.add("apiquery2.h");
//        win32Headers.add("appcompatapi.h");
        win32Headers.add("guiddef.h");
        win32Headers.add("aux_ulib.h");
//        win32Headers.add("avrfsdk.h");
//        win32Headers.add("camerauicontrol.h");
//        win32Headers.add("capi.h");
//        win32Headers.add("comppkgsup.h");  //not compiling
//        win32Headers.add("dciman.h");
//        win32Headers.add("ddrawgdi.h");   //not compiling
//        win32Headers.add("editionupgradehelper.h");
//        win32Headers.add("exdisp.h");
//        win32Headers.add("exposeenums2managed.h");   //not compiling
//        win32Headers.add("fci.h");
//        win32Headers.add("fdi.h");
//        win32Headers.add("fdi_fci_types.h");
//        win32Headers.add("featurestagingapi.h");   //not compiling
//        win32Headers.add("fhcfg.h"); //13k
//        win32Headers.add("fhsvcctl.h");
//        win32Headers.add("filehc.h");
//        win32Headers.add("icwcfg.h");
//        win32Headers.add("ime.h");
//        win32Headers.add("isolatedapplauncher.h");
//        win32Headers.add("iwscapi.h");
//        win32Headers.add("lmaccess.h");
//        win32Headers.add("loadperf.h");
//        win32Headers.add("msxml.h");
//        win32Headers.add("ntsecpkg.h");
//        win32Headers.add("rpcndr.h"); //not compiling
//        win32Headers.add("rtlsupportapi.h");  //not compiling
//        win32Headers.add("stralign.h");
//        win32Headers.add("tcpioctl.h");//not compiling
//        win32Headers.add("tdiinfo.h");
//        win32Headers.add("vdmdbg.h"); //not compiling
//        win32Headers.add("wininet.h");
//        win32Headers.add("winnls32.h");
//        win32Headers.add("winreg.h");
//        win32Headers.add("wscapi.h");
//        var allFiles = srcLoc.listFiles((dir, name) -> name.toLowerCase().startsWith("win") && name.endsWith(".h"));
        List<File> allFiles = new ArrayList<>();
        for (String s: win32Headers)
        {
            boolean found = false;
            for (var root : hpaths)
            {
                if (new File(root, s).exists())
                {
                    allFiles.add(new File(root, s));
                    found = true;
                }
            }
            if (!found)
                System.err.println("NOT FOUND: " + s);
        }
//        win32Headers.stream().map(h -> new File(srcLoc1, h)).toList();
//        var allFiles = srcLoc.listFiles((dir, name) -> win32Headers.contains(name.toLowerCase()));
        HashSet<String> allNames = new HashSet<>();

        int generated = 0;
        {
            System.out.println("=============================================");
            System.out.println(generated + " of " + allFiles.size() + ", ");
            System.out.println("=============================================");

//            List<Path> pathList = Arrays.stream(allFiles).map(f -> f.toPath()).toList();
//            var header2Pass = new ClangHtoP();
//            ClangHtoP clang = null;
//            List<ClangHtoP.CFunction> allFunctions = new ArrayList<>();

            for (File f : allFiles) {
//                var headerList = List.of(f.toPath());
//                List<String> ignoreFunctionNames = new ArrayList<>();
//                if (clang != null)
//                    ignoreFunctionNames = clang.allFunctions.stream().map(ClangHtoP.CFunction::name).toList();
                String[] a = new String[]
                        {
//                                f.getAbsolutePath(), destLoc.toString(), "jwin32",
//                                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um",
//                                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\shared",
//                                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\ucrt",
//                                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\winrt",
                                "-IC:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Tools\\MSVC\\14.29.30133\\include",
//                                "-D_AMD64_", //"/DWIN32", "/D_WINDOWS",
//                                "-DWINAPI_FAMILY=100",
//                                "-D_WIN32_WINNT=0x1000",
                                //value from https://www.google.com/search?q=msdn+WINAPI_FAMILY_PARTITION+values&rlz=1C1CHBF_enCA920CA920&oq=msdn+WINAPI_FAMILY_PARTITION+values&gs_lcrp=EgZjaHJvbWUyBggAEEUYOdIBCTQ0NTZqMGoxNagCCLACAQ&sourceid=chrome&ie=UTF-8
//                                "-DWINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)", //WINAPI_PARTITION_PC_APP
//                                "-Wignored-attributes",
//                                "-Wignored-pragma-intrinsic",
//                                "-Wimplicit-int",
                                "-x", "c-header", "-v"

//                                "/D_WINDOWS", "/Ob0", "/Od", "/RTC1"
//                        "-D_MSC_VER=1300"

                        };

                try(HeaderDefs header2Pass = new HeaderDefs(f.toPath(), a))
                {
                    header2Pass.parseClang();
                    header2Pass.generateJava("jwin32_b", destLoc, new HashSet<>());
//                    HashSet<String> allNames2 = new HashSet<>();
//                    HeaderToPassportClang.generateJava(header2Pass,allNames2);
//                    var foundFunctions = header2Pass.build(a, allNames);
                    var namelst = header2Pass.allFunctions.stream().map(HeaderDefs.CFunction::name).toList();
                    allNames.addAll(namelst);
                }
                catch(Throwable th)
                {
                    th.printStackTrace();
                }

            }
        }
    }

}
