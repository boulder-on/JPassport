package jpassport.parser;

import jpassport.apibuilder.HeaderDefs;

import java.nio.file.Path;
import java.util.HashSet;

public class GeneratePassport {

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Usage: [path to header] [destination folder for generated code] [package name to use] [clang options] ");
            return;
        }



        String[] clangOptions = new String[Math.max(0, args.length - 3)];
        for (int i = 0; i < clangOptions.length; i++) {
            clangOptions[i] = args[i + 3];
        }
        var headerPath = Path.of(args[0]);
        String destinationFolder = args[1];
        String packageName = args[2];
        var destLoc = Path.of(destinationFolder);

        try(HeaderDefs header2Pass = new HeaderDefs(headerPath, clangOptions))
        {
            header2Pass.parseClang();
            header2Pass.generateJava(packageName, destLoc, new HashSet<>());

            var namelst = header2Pass.allFunctions.stream().map(HeaderDefs.CFunction::name).toList();
        }
        catch(Throwable th)
        {
            th.printStackTrace();
        }

    }
}
