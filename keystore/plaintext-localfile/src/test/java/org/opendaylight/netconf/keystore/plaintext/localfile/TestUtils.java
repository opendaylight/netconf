/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.opendaylight.netconf.keystore.plaintext.api.PlaintextStorage;

final class TestUtils {
    static final Collection<StorageEntry> TEST_ENTRIES = IntStream.range(0, 10)
        .mapToObj(index -> storageEntry("key-" + index, "value-" + index)).toList();

    private TestUtils() {
        // utility class
    }

    static void prepareDataFile(final File file, final byte[] secret, final Collection<StorageEntry> entries)
            throws IOException {
        try (var fos = new FileOutputStream(file)) {
            CipherUtils.toBase64Stream(entries, secret, fos);
        }
    }

    static void preparePropertiesFile(final File file, final Collection<StorageEntry> entries) throws IOException {
        try (var fos = new FileOutputStream(file)) {
            fos.write("# comment\n".getBytes(StandardCharsets.UTF_8));
            for (var entry : entries) {
                fos.write(entry.key());
                fos.write('=');
                fos.write(entry.value());
                fos.write('\n');
            }
        }
    }

    static void prepareSecretFile(final File file, final byte[] secret) throws IOException {
        try (var out = Base64.getEncoder().wrap(new FileOutputStream(file))) {
            out.write(secret);
        }
    }

    static byte[] secretFromFile(final File file) throws IOException {
        assertTrue(file.exists());
        try (var in = Base64.getDecoder().wrap(new FileInputStream(file))) {
            return in.readAllBytes();
        }
    }

    static StorageEntry storageEntry(final String key, final String value) {
        return new StorageEntry(bytesOf(key), bytesOf(value));
    }

    static byte[] bytesOf(final String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    static void assertFileContains(final File file, final byte[] secret, final Collection<StorageEntry> entries)
            throws IOException {
        assertTrue(file.exists());
        try (var fis = new FileInputStream(file)) {
            final var fileEntries = CipherUtils.fromBase64Stream(secret, fis);
            assertNotNull(fileEntries);
            assertEquals(entries.size(), fileEntries.size());
            assertTrue(fileEntries.containsAll(entries));
        }
    }

    static void assertStorageContains(final PlaintextStorage storage, final Collection<StorageEntry> entries) {
        // ensure all expected entries can be extracted
        assertNotNull(storage);
        for (var entry : entries) {
            assertArrayEquals(entry.value(), storage.lookup(entry.key()));
        }
        // validate iterator contain all the expected entries
        final var fromIterator = new ArrayList<Map.Entry<byte[], byte[]>>(entries.size());
        final var iterator = storage.iterator();
        while (iterator.hasNext()) {
            fromIterator.add(iterator.next());
        }
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals(entries.size(), fromIterator.size());
        assertTrue(fromIterator.containsAll(entries));
    }
}
