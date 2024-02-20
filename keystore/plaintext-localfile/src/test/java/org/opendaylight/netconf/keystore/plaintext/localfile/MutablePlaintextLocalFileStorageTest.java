/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.SECRET;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.TEST_ENTRIES;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertFileContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertStorageContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.prepareDataFile;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.storageEntry;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendaylight.netconf.keystore.plaintext.api.MutablePlaintextStorage;

public class MutablePlaintextLocalFileStorageTest {

    private static final StorageEntry ENTRY_1 = storageEntry("key-1", "value-1");
    private static final StorageEntry ENTRY_1_MODIFIED = storageEntry("key-1", "value-1-modified");
    private static final StorageEntry ENTRY_2 = storageEntry("key-2", "value-2");
    private static final StorageEntry ENTRY_2_MODIFIED = storageEntry("key-2", "value-3-modified");
    private static final StorageEntry ENTRY_3 = storageEntry("key-3", "value-3");
    private static final StorageEntry ENTRY_3_MODIFIED = storageEntry("key-3", "value-3-modified");
    private static final StorageEntry ENTRY_4 = storageEntry("key-4", "value-4");
    private static final Collection<StorageEntry> INITIAL_DATA = List.of(ENTRY_1, ENTRY_2, ENTRY_3);

    @TempDir
    private static File tempDir;

    private static File storageFile;

    @BeforeAll
    static void beforeAll() {
        // init file paths after temp dir is initialized
        storageFile = new File(tempDir, "data-file");
    }

    @Test
    void initFromFile() throws Exception {
        // init from existing file
        prepareDataFile(storageFile, SECRET, TEST_ENTRIES);
        assertStorageContains(new MutablePlaintextLocalFileStorage(storageFile, SECRET), TEST_ENTRIES);
    }

    @Test
    void initWithDefaults() throws Exception {
        // init from non-existent file with defaults
        final File file = new File(tempDir, "defaults");
        final var defaults = Map.ofEntries(TEST_ENTRIES.toArray(StorageEntry[]::new));
        assertStorageContains(new MutablePlaintextLocalFileStorage(file, SECRET, defaults), TEST_ENTRIES);

        // ensure newly created file having expected content
        assertFileContains(file, SECRET, TEST_ENTRIES);
    }

    @Test
    void removeKey() throws IOException {
        final var storage = initStorage();
        assertStorageContains(storage, INITIAL_DATA);
        // delete non existent
        assertNull(storage.removeKey(ENTRY_4.key()));
        // delete existent
        assertArrayEquals(ENTRY_1.value(), storage.removeKey(ENTRY_1.key()));
        assertArrayEquals(ENTRY_3.value(), storage.removeKey(ENTRY_3.key()));
        // validate data
        final var expected = List.of(ENTRY_2);
        assertStorageContains(storage, expected);
        assertFileContains(storageFile, SECRET, expected);
    }

    @Test
    void removeEntry() throws IOException {
        final var storage = initStorage();
        assertStorageContains(storage, INITIAL_DATA);
        // delete non existent
        assertFalse(storage.removeEntry(ENTRY_4.key(), ENTRY_4.value()));
        // value mismatches
        assertFalse(storage.removeEntry(ENTRY_1.key(), ENTRY_1_MODIFIED.value()));
        // delete existent
        assertTrue(storage.removeEntry(ENTRY_1.key(), ENTRY_1.value()));
        // validate data
        final var expected = List.of(ENTRY_2, ENTRY_3);
        assertStorageContains(storage, expected);
        assertFileContains(storageFile, SECRET, expected);
    }

    @Test
    void insertEntry() throws IOException {
        final var storage = initStorage();
        assertStorageContains(storage, INITIAL_DATA);
        // key exists
        assertArrayEquals(ENTRY_1.value(), storage.insertEntry(ENTRY_1.key(), ENTRY_1_MODIFIED.value()));
        assertArrayEquals(ENTRY_2.value(), storage.insertEntry(ENTRY_2.key(), ENTRY_2_MODIFIED.value()));
        assertArrayEquals(ENTRY_3.value(), storage.insertEntry(ENTRY_3.key(), ENTRY_3_MODIFIED.value()));
        // ensure no data modified
        assertStorageContains(storage, INITIAL_DATA);
        // new entry
        assertNull(storage.insertEntry(ENTRY_4.key(), ENTRY_4.value()));
        // validate data
        final var expected = List.of(ENTRY_1, ENTRY_2, ENTRY_3, ENTRY_4);
        assertStorageContains(storage, expected);
        assertFileContains(storageFile, SECRET, expected);
    }

    @Test
    void putEntry() throws IOException {
        final var storage = initStorage();
        assertStorageContains(storage, INITIAL_DATA);
        // key exists
        assertArrayEquals(ENTRY_1.value(), storage.putEntry(ENTRY_1.key(), ENTRY_1_MODIFIED.value()));
        assertArrayEquals(ENTRY_2.value(), storage.putEntry(ENTRY_2.key(), ENTRY_2_MODIFIED.value()));
        assertArrayEquals(ENTRY_3.value(), storage.putEntry(ENTRY_3.key(), ENTRY_3_MODIFIED.value()));
        assertNull(storage.putEntry(ENTRY_4.key(), ENTRY_4.value()));
        // validate data
        final var expected = List.of(ENTRY_1_MODIFIED, ENTRY_2_MODIFIED, ENTRY_3_MODIFIED, ENTRY_4);
        assertStorageContains(storage, expected);
        assertFileContains(storageFile, SECRET, expected);
    }

    private MutablePlaintextStorage initStorage() throws IOException {
        prepareDataFile(storageFile, SECRET, INITIAL_DATA);
        return new MutablePlaintextLocalFileStorage(storageFile, SECRET);
    }

}
