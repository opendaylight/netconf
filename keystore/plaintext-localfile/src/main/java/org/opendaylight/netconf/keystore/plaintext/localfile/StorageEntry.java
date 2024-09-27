/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
record StorageEntry(byte[] key, byte[] value) {
    StorageEntry {
        if (key.length == 0) {
            throw new IllegalArgumentException("key is empty");
        }
        if (value.length == 0) {
            throw new IllegalArgumentException("value is empty");
        }
    }

    StorageEntry(final String key, final String value) {
        this(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    byte[] publicValue() {
        return value.clone();
    }

    Map.Entry<byte[], byte[]> toEntry() {
        return Map.entry(key.clone(), publicValue());
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return this == obj || obj instanceof StorageEntry other && Arrays.equals(key, other.key)
            && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(key) + Arrays.hashCode(value);
    }
}
