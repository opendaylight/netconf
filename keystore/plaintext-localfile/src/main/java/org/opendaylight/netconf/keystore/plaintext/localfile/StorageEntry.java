/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Map;

record StorageEntry(byte[] key, byte[] value) implements Map.Entry<byte[], byte[]> {

    StorageEntry {
        requireNonNull(key);
        requireNonNull(value);
        if (key.length == 0) {
            throw new IllegalArgumentException("key is empty");
        }
        if (value.length == 0) {
            throw new IllegalArgumentException("value is empty");
        }
    }

    @Override
    public byte[] getKey() {
        return Arrays.copyOf(key, key.length);
    }

    @Override
    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public byte[] setValue(byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        StorageEntry that = (StorageEntry) object;
        return Arrays.equals(key, that.key) && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(key);
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }
}
