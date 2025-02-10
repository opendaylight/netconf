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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.TEST_ENTRIES;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertFileContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.assertStorageContains;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.prepareDataFile;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.preparePropertiesFile;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.prepareSecretFile;
import static org.opendaylight.netconf.keystore.plaintext.localfile.TestUtils.secretFromFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.keystore.plaintext.api.MutablePlaintextStorage;

@ExtendWith(MockitoExtension.class)
class PlaintextLocalFileStorageTest {
    private static final StorageEntry ENTRY_1 = new StorageEntry("key-1", "value-1");
    private static final StorageEntry ENTRY_1_MODIFIED = new StorageEntry("key-1", "value-1-modified");
    private static final StorageEntry ENTRY_2 = new StorageEntry("key-2", "value-2");
    private static final StorageEntry ENTRY_2_MODIFIED = new StorageEntry("key-2", "value-3-modified");
    private static final StorageEntry ENTRY_3 = new StorageEntry("key-3", "value-3");
    private static final StorageEntry ENTRY_3_MODIFIED = new StorageEntry("key-3", "value-3-modified");
    private static final StorageEntry ENTRY_4 = new StorageEntry("key-4", "value-4");
    private static final Collection<StorageEntry> INITIAL_DATA = List.of(ENTRY_1, ENTRY_2, ENTRY_3);
    private static final Collection<StorageEntry> DEFAULT_DATA = List.of(new StorageEntry("admin", "admin"));

    @Mock
    private PlaintextLocalFileStorage.Configuration config;
    @TempDir
    private Path tmpFolder;

    private byte[] secret;
    private Path storageFile;
    private Path keyFile;
    private Path importFile;

    @BeforeEach
    void beforeEach() throws IOException {
        secret = CipherUtils.generateSecret();
        storageFile = tmpFolder.resolve("data-file");
        keyFile = tmpFolder.resolve("key-file");
        importFile = tmpFolder.resolve("import-file");
    }

    @Test
    void initFromFile() throws IOException {
        prepareDataFile(storageFile, secret, TEST_ENTRIES);
        assertStorageContains(new PlaintextLocalFileStorage(secret, storageFile), TEST_ENTRIES);
    }

    @Test
    void initFromConfig() throws IOException {
        // key and encrypted data loaded from existing files
        prepareSecretFile(keyFile, secret);
        prepareDataFile(storageFile, secret, TEST_ENTRIES);
        mockConfig();
        assertStorageContains(new PlaintextLocalFileStorage(config), TEST_ENTRIES);
    }

    @Test
    void initWithImportedData() throws IOException {
        // key data loaded from existing file,
        prepareSecretFile(keyFile, secret);
        // data to be imported from unencrypted properties file
        preparePropertiesFile(importFile, TEST_ENTRIES);
        // no encrypted storage
        assertFalse(Files.exists(storageFile));
        mockConfig();

        assertStorageContains(new PlaintextLocalFileStorage(config), TEST_ENTRIES);
        // data should be persisted into secure storage, original data file should be removed
        assertTrue(Files.exists(storageFile));
        assertFileContains(storageFile, secret, TEST_ENTRIES);
        assertFalse(Files.exists(importFile));
    }

    @Test
    void initWithDefaults() throws IOException {
        // no files existing
        assertFalse(Files.exists(keyFile));
        assertFalse(Files.exists(storageFile));
        mockConfig();

        final var storage = new PlaintextLocalFileStorage(config);
        // 32 byte secret generated and persisted
        final var newSecret = secretFromFile(keyFile);
        assertNotNull(newSecret);
        assertEquals(32, newSecret.length);
        // default data populated and persisted
        assertStorageContains(storage, DEFAULT_DATA);
        assertFileContains(storageFile, newSecret, DEFAULT_DATA);
    }

    @Test
    void illegalKeyFileSymLink() throws IOException {
        prepareSecretFile(keyFile, secret);
        doReturn(symLink(keyFile).toAbsolutePath().toString()).when(config).key$_$file();
        // symlink should not be used for secret key file
        assertThrows(IllegalArgumentException.class, () -> new PlaintextLocalFileStorage(config));
    }

    @Test
    void illegalStorageFileSymLink() throws IOException {
        prepareDataFile(storageFile, secret, TEST_ENTRIES);
        // symlink should not be used for storage file
        assertThrows(IllegalArgumentException.class, () -> new PlaintextLocalFileStorage(secret, symLink(storageFile)));
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
        assertFileContains(storageFile, secret, expected);
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
        assertFileContains(storageFile, secret, expected);
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
        assertFileContains(storageFile, secret, expected);
    }

    @Test
    void putEntry() throws IOException {
        final var storage = initStorage();
        assertStorageContains(storage, INITIAL_DATA);
        // same entry
        assertArrayEquals(ENTRY_1.value(), storage.putEntry(ENTRY_1.key(), ENTRY_1.value()));
        // update entries
        assertArrayEquals(ENTRY_1.value(), storage.putEntry(ENTRY_1.key(), ENTRY_1_MODIFIED.value()));
        assertArrayEquals(ENTRY_2.value(), storage.putEntry(ENTRY_2.key(), ENTRY_2_MODIFIED.value()));
        assertArrayEquals(ENTRY_3.value(), storage.putEntry(ENTRY_3.key(), ENTRY_3_MODIFIED.value()));
        // insert new
        assertNull(storage.putEntry(ENTRY_4.key(), ENTRY_4.value()));
        // validate data
        final var expected = List.of(ENTRY_1_MODIFIED, ENTRY_2_MODIFIED, ENTRY_3_MODIFIED, ENTRY_4);
        assertStorageContains(storage, expected);
        assertFileContains(storageFile, secret, expected);
    }

    private void mockConfig() {
        doReturn(keyFile.toAbsolutePath().toString()).when(config).key$_$file();
        doReturn(storageFile.toAbsolutePath().toString()).when(config).data$_$file();
        doReturn(importFile.toAbsolutePath().toString()).when(config).import$_$from$_$file();
    }

    private MutablePlaintextStorage initStorage() throws IOException {
        prepareDataFile(storageFile, secret, INITIAL_DATA);
        return new PlaintextLocalFileStorage(secret, storageFile);
    }

    private static Path symLink(final Path file) throws IOException {
        final var absPath = file.toAbsolutePath();
        final var link = Path.of(absPath.toString() + ".lnk");
        Files.createSymbolicLink(link, absPath);
        return link;
    }
}
