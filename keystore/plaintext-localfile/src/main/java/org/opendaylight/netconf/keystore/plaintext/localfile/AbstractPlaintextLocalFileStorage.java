/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.opendaylight.netconf.keystore.plaintext.api.PlaintextStorage;

abstract class AbstractPlaintextLocalFileStorage implements PlaintextStorage {

    private final File file;
    private final byte[] secret;

    protected AbstractPlaintextLocalFileStorage(final File file, final byte[] secret) {
        this.file = requireNonNull(file);
        this.secret = validateSecret(secret);
    }

    protected final Collection<StorageEntry> initFromFile() {
        if (!file.isFile() || !file.exists()) {
            throw new IllegalArgumentException("File " + file.getAbsoluteFile()
                + " is not a file or does not exist.");
        }
        try {
            return readFromFile();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected final Collection<StorageEntry> initWithDefaults(final Map<byte[], byte[]> defaults) {
        if (file.exists()) {
            return initFromFile();
        }
        final var entries = requireNonNull(defaults).entrySet().stream()
            .map(entry -> new StorageEntry(entry.getKey(), entry.getValue())).toList();
        try {
            writeToFile(entries);
            return entries;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected final Collection<StorageEntry> readFromFile() throws IOException {
        try (var fis = new FileInputStream(file)) {
            return CipherUtils.fromBase64Stream(secret, fis);
        }
    }

    protected final void writeToFile(final Collection<StorageEntry> data) throws IOException {
        // randomize order before persistence
        final var list = new ArrayList<>(data);
        Collections.shuffle(list, new Random());
        // write data to temp file
        final var tmpFile = File.createTempFile("plaintext-localfile-", ".temp");
        try (var fos = new FileOutputStream(tmpFile)) {
            CipherUtils.toBase64Stream(list, secret, fos);
        }
        // replace file with newly written
        Files.move(tmpFile.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING);
    }

    @Override
    public final Iterator<Map.Entry<byte[], byte[]>> iterator() {
        final var array = iteratorEntries();
        return new Iterator<>() {
            private final int maxIndex = array.length - 1;
            private int index = -1;

            @Override
            public boolean hasNext() {
                return index < maxIndex;
            }

            @Override
            public Map.Entry<byte[], byte[]> next() {
                if (index >= maxIndex) {
                    throw new NoSuchElementException();
                }
                return array[++index];
            }
        };
    }

    protected abstract StorageEntry[] iteratorEntries();

    private static byte[] validateSecret(final byte[] secret) {
        requireNonNull(secret);
        if (secret.length != 16 && secret.length != 32) {
            throw new IllegalArgumentException("Invalid secret length " + secret.length
                + ". Expected 16 or 32.");
        }
        return secret;
    }
}
