/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.RANDOM;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.SECRET;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.TEST_ENTRIES;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertFileContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertStorageContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.prepareDataFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlaintextLocalFileStorageTest {

    @TempDir
    private static File tempDir;

    private static File storageFile;
    private static File nonExistentFile;

    @BeforeAll
    static void beforeAll() throws IOException {
        // init file paths after temp dir is initialized
        storageFile = new File(tempDir, "data-file");
        nonExistentFile = new File(tempDir, "non-existent-file");
        // init data content
        prepareDataFile(storageFile, SECRET, TEST_ENTRIES);
    }

    @Test
    void initFromFile() {
        // init from existing file
        assertStorageContains(new PlaintextLocalFileStorage(storageFile, SECRET), TEST_ENTRIES);
    }

    @Test
    void initWithDefaults() throws Exception {
        // init from non-existent file with defaults
        final File file = new File(tempDir, "defaults");
        final var defaults = Map.ofEntries(TEST_ENTRIES.toArray(StorageEntry[]::new));
        assertStorageContains(new PlaintextLocalFileStorage(file, SECRET, defaults), TEST_ENTRIES);

        // ensure newly created file having expected content
        assertFileContains(file, SECRET, TEST_ENTRIES);
    }

    @ParameterizedTest (name = "Init failure: {0}")
    @MethodSource("initFailureArgs")
    void initFailure(final String ignored, final File file, final byte[] secret,
            final Class<? extends Throwable> expected) {
        assertThrows(expected, () -> new PlaintextLocalFileStorage(file, secret));
    }

    private static Stream<Arguments> initFailureArgs() {
        return Stream.of(
          Arguments.of("Null file", null, SECRET, NullPointerException.class),
          Arguments.of("Non-existent file", nonExistentFile, SECRET, IllegalArgumentException.class),
          Arguments.of("Null secret", storageFile, null, NullPointerException.class),
          Arguments.of("Invalid secret length", storageFile, RANDOM.generateSeed(9), IllegalArgumentException.class)
        );
    }
}
