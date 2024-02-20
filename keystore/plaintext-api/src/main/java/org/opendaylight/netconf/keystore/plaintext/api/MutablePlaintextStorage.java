/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.api;

import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Mutable Plaintext Storage interface.
 */
public interface MutablePlaintextStorage extends PlaintextStorage {

    /**
     * Removes the mapping for a key from this storage if it is present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was no mapping for key
     * @throws IOException on storage update error
     */
    byte @Nullable [] removeKey(byte @NonNull [] key) throws IOException;

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value.
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return true if entry was removed, false otherwise
     * @throws IOException on storage update error
     */
    boolean removeEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException;

    /**
     * If the specified key is not already associated with a value associates it with the given value and returns null,
     * else returns the current value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or null if there was no mapping for the key
     * @throws IOException on storage update error
     */
    byte @Nullable [] insertEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException;

    /**
     * Associates the specified value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was no mapping for key
     * @throws IOException on storage update error
     */
    byte @Nullable [] putEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException;
}
