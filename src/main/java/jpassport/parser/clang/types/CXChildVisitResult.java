package jpassport.parser.clang.types;


public enum CXChildVisitResult {
    /**
     * Terminates the cursor traversal.
     */
    CXChildVisit_Break,
    /**
     * Continues the cursor traversal with the next sibling of
     * the cursor just visited, without visiting its children.
     */
    CXChildVisit_Continue,
    /**
     * Recursively traverse the children of this cursor, using
     * the same visitor and client data.
     */
    CXChildVisit_Recurse

}
