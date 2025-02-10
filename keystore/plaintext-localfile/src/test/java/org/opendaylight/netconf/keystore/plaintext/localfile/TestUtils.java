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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.opendaylight.netconf.keystore.plaintext.api.PlaintextStorage;

final class TestUtils {
    static final List<StorageEntry> TEST_ENTRIES = IntStream.range(0, 10)
        .mapToObj(index -> new StorageEntry("key-" + index, "value-" + index))
        .toList();

    private TestUtils() {
        // utility class
    }

    static void prepareDataFile(final Path file, final byte[] secret, final Collection<StorageEntry> entries)
            throws IOException {
        try (var fos = Files.newOutputStream(file)) {
            CipherUtils.toBase64Stream(entries, secret, fos);
        }
    }

    static void preparePropertiesFile(final Path file, final Collection<StorageEntry> entries) throws IOException {
        try (var fos = Files.newOutputStream(file)) {
            fos.write("# comment\n".getBytes(StandardCharsets.UTF_8));
            for (var entry : entries) {
                fos.write(entry.key());
                fos.write('=');
                fos.write(entry.value());
                fos.write('\n');
            }
        }
    }

    static void prepareSecretFile(final Path file, final byte[] secret) throws IOException {
        try (var out = Base64.getEncoder().wrap(Files.newOutputStream(file))) {
            out.write(secret);
        }
    }

    static byte[] secretFromFile(final Path file) throws IOException {
        assertTrue(Files.exists(file));
        try (var in = Base64.getDecoder().wrap(Files.newInputStream(file))) {
            return in.readAllBytes();
        }
    }

    static void assertFileContains(final Path file, final byte[] secret, final Collection<StorageEntry> entries)
            throws IOException {
        assertTrue(Files.exists(file));
        try (var fis = Files.newInputStream(file)) {
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
        final var fromIterator = new ArrayList<StorageEntry>(entries.size());
        final var iterator = storage.iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            fromIterator.add(new StorageEntry(entry.getKey(), entry.getValue()));
        }
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals(entries.size(), fromIterator.size());
        assertTrue(fromIterator.containsAll(entries));
    }
}
