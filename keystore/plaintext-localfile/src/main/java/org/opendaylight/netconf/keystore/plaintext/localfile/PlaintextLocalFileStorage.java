/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.keystore.plaintext.api.PlaintextStorage;

/**
 * Local file implementation of {@link PlaintextStorage}.
 */
public class PlaintextLocalFileStorage extends AbstractPlaintextLocalFileStorage {

    private StorageEntry [] entries;

    public PlaintextLocalFileStorage(final File file, final byte [] secret) {
        super(file, secret);
        entries = initFromFile().toArray(StorageEntry[]::new);
    }

    public PlaintextLocalFileStorage(final File file, final byte [] secret,
            final Map<byte[], byte[]> defaults) {
        super(file, secret);
        entries = initWithDefaults(defaults).toArray(StorageEntry[]::new);
    }

    @Override
    public byte @Nullable [] lookup(byte @NonNull [] key) {
        return Arrays.stream(entries).filter(entry -> Arrays.equals(key, entry.key())).findFirst()
            .map(StorageEntry::getValue).orElse(null);
    }

    @Override
    protected StorageEntry[] iteratorEntries() {
        return entries;
    }
}
