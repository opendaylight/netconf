/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.api;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Immutable Plaintext Storage interface.
 */
public interface PlaintextStorage extends Iterable<Map.Entry<byte[], byte[]>> {

    /**
     * Returns the value to which the specified key is mapped, or null if this storage contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if this storage contains no mapping for the key
     */
    byte @Nullable [] lookup(byte @NonNull [] key);
}
