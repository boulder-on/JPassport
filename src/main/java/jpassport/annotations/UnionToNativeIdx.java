package jpassport.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Added to an integer field of a record that extends Union.
 * That integer field will indicate which member of the union will
 * be sent to native memory. If the index is Union.NO_TO_NATIVE
 * then nothing is written.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface UnionToNativeIdx {
}
