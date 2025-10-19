package jpassport.enums;

/**
 * Required for any C enum you are modeling as a java enum
 * where the C enum does NOT use sequential values starting at 0.
 */
public interface EnumInt {
    int getCValue();
}
