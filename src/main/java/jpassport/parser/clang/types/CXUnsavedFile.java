package jpassport.parser.clang.types;

public record CXUnsavedFile(  /**
   * The file whose contents have not yet been saved.
   *
   * This file must already exist in the file system.
   */
    String Filename,

      /**
       * A buffer containing the unsaved contents of this file.
       */
      String contents,

      /**
       * The length of the unsaved contents of this buffer.
       */
      long Length
) {
}
