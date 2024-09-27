/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import java.util.Arrays;

record StorageEntry(byte[] key, byte[] value) {
    StorageEntry {
        if (key.length == 0) {
            throw new IllegalArgumentException("key is empty");
        }
        if (value.length == 0) {
            throw new IllegalArgumentException("value is empty");
        }
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof StorageEntry other && Arrays.equals(key, other.key)
            && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(key) + Arrays.hashCode(value);
    }
}
