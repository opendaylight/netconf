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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.keystore.plaintext.api.MutablePlaintextStorage;
import org.opendaylight.netconf.keystore.plaintext.api.PlaintextStorage;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local file implementation of{@link PlaintextStorage} and {@link MutablePlaintextStorage}.
 */
@Component(service = {PlaintextStorage.class, MutablePlaintextStorage.class}, immediate = true,
        configurationPid = "org.opendaylight.netconf.keystore.plaintext.localfile")
@Designate(ocd = PlaintextLocalFileStorage.Configuration.class)
@SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
public class PlaintextLocalFileStorage implements MutablePlaintextStorage {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String key$_$file() default "etc/plaintext.keystore.key";

        @AttributeDefinition
        String data$_$file() default "etc/plaintext.keystore.data";

        @AttributeDefinition
        String import$_$from$_$file() default "etc/plaintext.keystore.data.unencrypted";
    }

    private static final Logger LOG = LoggerFactory.getLogger(PlaintextLocalFileStorage.class);

    private final Path file;
    private final byte[] secret;
    private final AtomicReference<Set<StorageEntry>> entriesRef = new AtomicReference<>();

    @Activate
    public PlaintextLocalFileStorage(final Configuration configuration) throws IOException {
        this(
            secretFrom(Path.of(configuration.key$_$file())),
            Path.of(configuration.data$_$file()),
            Path.of(configuration.import$_$from$_$file())
        );
        LOG.info("Service activated");
    }

    public PlaintextLocalFileStorage(final byte[] secret, final Path file) throws IOException {
        this(secret, file, null);
    }

    public PlaintextLocalFileStorage(final byte[] secret, final Path file, final @Nullable Path importFile)
            throws IOException {
        this.file = validateFile(file);
        this.secret = validateSecret(secret);
        entriesRef.set(Set.copyOf(readData(secret, file, importFile)));
    }

    @Override
    public final Iterator<Map.Entry<byte[], byte[]>> iterator() {
        final var it = entries().iterator();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<byte[], byte[]> next() {
                return it.next().toEntry();
            }
        };
    }

    @Override
    public byte[] lookup(final byte[] key) {
        final var entry = lookupEntry(key);
        return entry != null ? entry.publicValue() : null;
    }

    @Override
    public byte[] removeKey(final byte[] key) throws IOException {
        final var toRemove = lookupEntry(key);
        if (toRemove == null) {
            return null;
        }
        final var entries = new HashSet<>(entries());
        entries.remove(toRemove);
        updateWith(Set.copyOf(entries));
        return toRemove.value();
    }

    @Override
    public boolean removeEntry(final byte[] key, final byte[] value) throws IOException {
        final var entries = new HashSet<>(entries());
        final var removed = entries.remove(new StorageEntry(key, value));
        if (removed) {
            updateWith(Set.copyOf(entries));
        }
        return removed;
    }

    @Override
    public byte[] insertEntry(final byte[] key, final byte[] value) throws IOException {
        final var existing = lookupEntry(key);
        if (existing != null) {
            return existing.publicValue();
        }
        final var entries = new HashSet<>(entries());
        entries.add(new StorageEntry(key.clone(), value.clone()));
        updateWith(Set.copyOf(entries));
        return null;
    }

    @Override
    public byte[] putEntry(final byte[] key, final byte[] value) throws IOException {
        final var previous = lookupEntry(key);
        final var entries = new HashSet<>(entries());
        if (previous != null) {
            if (Arrays.equals(previous.value(), value)) {
                // same value, no reason to update
                return value;
            }
            entries.remove(previous);
        }
        entries.add(new StorageEntry(key.clone(), value.clone()));
        updateWith(Set.copyOf(entries));
        return previous == null ? null : previous.publicValue();
    }

    private Set<StorageEntry> entries() {
        final var entries = entriesRef.get();
        return entries == null ? Set.of() : entries;
    }

    private @Nullable StorageEntry lookupEntry(final byte[] key) {
        requireNonNull(key);
        for (var entry : entries()) {
            if (Arrays.equals(key, entry.key())) {
                return entry;
            }
        }
        return null;
    }

    private void updateWith(final Set<StorageEntry> mutable) throws IOException {
        final var immutable = Set.copyOf(mutable);
        writeToFile(immutable);
        entriesRef.set(immutable);
    }

    private void writeToFile(final Collection<StorageEntry> data) throws IOException {
        // randomize order before persistence
        final var list = new ArrayList<>(data);
        Collections.shuffle(list, new Random());
        // create temp file with new data-file value
        final var tmpFile = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".temp");
        try (var fos = Files.newOutputStream(tmpFile)) {
            CipherUtils.toBase64Stream(list, secret, fos);
        }
        // replace file with newly written
        Files.move(tmpFile, file, ATOMIC_MOVE, REPLACE_EXISTING);
    }

    private static byte[] secretFrom(final Path file) throws IOException {
        validateFile(file);
        if (Files.exists(file)) {
            try (var in = Base64.getDecoder().wrap(Files.newInputStream(file))) {
                return in.readAllBytes();
            }
        }
        LOG.warn("Key file {} does not exist. Using generated key.", file);
        final var secret = CipherUtils.generateSecret();
        try (var out = Base64.getEncoder().wrap(Files.newOutputStream(file))) {
            out.write(secret);
        }
        return secret;
    }

    private static byte[] validateSecret(final byte[] secret) {
        if (secret.length != 16 && secret.length != 32) {
            throw new IllegalArgumentException("Invalid secret length " + secret.length
                + ". Expected 16 or 32.");
        }
        return secret;
    }

    private static Path validateFile(final Path file) {
        if (Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("Symbolic link detected in file path " + file);
        }
        return file;
    }

    private static Collection<StorageEntry> readData(final byte[] secret, final Path file,
            final @Nullable Path importFile) throws IOException {
        if (Files.exists(file)) {
            try (var fis = Files.newInputStream(file)) {
                return CipherUtils.fromBase64Stream(secret, fis);
            }
        }
        LOG.warn("Data file {} does not exist.", file);
        final var props = new Properties();
        if (importFile != null && Files.exists(importFile)) {
            LOG.info("Importing initial data from unencrypted properties file {}", importFile);
            try (var fis = Files.newInputStream(importFile)) {
                props.load(fis);
            }
        } else {
            LOG.info("Importing initial data from default properties");
            try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream("default.properties")) {
                props.load(in);
            }
        }
        final var data = new ArrayList<StorageEntry>();
        for (var name : props.stringPropertyNames()) {
            final var value = props.getProperty(name);
            if (value != null) {
                data.add(new StorageEntry(name, value));
            }
        }
        try (var fos = Files.newOutputStream(file)) {
            CipherUtils.toBase64Stream(data, secret, fos);
        }
        if (importFile != null && Files.deleteIfExists(importFile)) {
            LOG.info("Data moved to encrypted storage, import data file {} removed as insecure.", importFile);
        }
        return data;
    }
}
