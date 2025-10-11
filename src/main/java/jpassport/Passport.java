/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
package jpassport;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Optional;

/**
 * An interface needs to extend this interface in order to link to the foreign library.
 */
public interface Passport {
    HashMap<String, MethodHandle> methods = new HashMap<>();
    HashMap<String, MemorySegment> loadedNames = new HashMap<>();
    DebugPassport[] debugHook = new DebugPassport[] {null};

    /**
     * Lets you know if a specific method was found or not. Generally, all methods must be found
     * when loading the library. However, if some methods use the <code>@NotRequired</code> annotation
     * then those methods may not be present at runtime.
     *
     * @param name The exact name of a method in your interface.
     * @return True if the native link was made, false if it was not. If this returns false
     * then a call to the interface method in question is going to throw a java.lang.Error.
     */
    default boolean hasMethod(String name)
    {
        return methods.containsKey(name);
    }

    default boolean hasName(String name)
    {
        return loadedNames.containsKey(name);
    }

    default void setDebugHook(DebugPassport debug)
    {
        debugHook[0] = debug;
    }

    default Optional<DebugPassport> getDebug()
    {
        return debugHook[0] == null ? Optional.empty() : Optional.of(debugHook[0]);
    }
}
