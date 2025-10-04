package jpassport;


/**
 * A record that extends this interface is considered a C Union.
 * The record must contain 2 integer fields that are annotated with
 * @UnionToNativeIdx (which zero based field index to send to native memory)
 * and @UnionFromNativeIdx (which zero based field index to read back
 * from native memory).
 */
public interface Union {

}
