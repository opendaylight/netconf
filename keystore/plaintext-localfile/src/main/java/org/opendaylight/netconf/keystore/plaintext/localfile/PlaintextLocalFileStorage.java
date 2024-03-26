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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
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

    private final File file;
    private final byte[] secret;
    private final AtomicReference<Set<StorageEntry>> entriesRef = new AtomicReference<>();

    @Activate
    public PlaintextLocalFileStorage(final Configuration configuration) throws IOException {
        this(
            secretFrom(new File(configuration.key$_$file())),
            new File(configuration.data$_$file()),
            new File(configuration.import$_$from$_$file())
        );
        LOG.info("Service activated");
    }

    public PlaintextLocalFileStorage(final byte[] secret, final File file) throws IOException {
        this(secret, file, null);
    }

    public PlaintextLocalFileStorage(final byte[] secret, final File file, final  @Nullable File importFile)
            throws IOException {
        this.file = validateFile(file);
        this.secret = validateSecret(secret);
        entriesRef.set(Set.copyOf(readData(secret, file, importFile)));
    }

    @Override
    public final Iterator<Map.Entry<byte[], byte[]>> iterator() {
        final var array = entries().toArray(StorageEntry[]::new);
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

    @Override
    public byte @Nullable [] lookup(final byte @NonNull [] key) {
        return findByKey(key).map(StorageEntry::getValue).orElse(null);
    }

    @Override
    public byte @Nullable [] removeKey(final byte @NonNull [] key) throws IOException {
        final var toRemove = findByKey(key).orElse(null);
        if (toRemove == null) {
            return null;
        }
        final var entries = new HashSet<>(entries());
        entries.remove(toRemove);
        updateWith(Set.copyOf(entries));
        return toRemove.value();
    }

    @Override
    public boolean removeEntry(final byte @NonNull [] key, final byte @NonNull [] value) throws IOException {
        final var entries = new HashSet<>(entries());
        final var removed = entries.remove(new StorageEntry(key, value));
        if (removed) {
            updateWith(Set.copyOf(entries));
        }
        return removed;
    }

    @Override
    public byte @Nullable [] insertEntry(final byte @NonNull [] key, final byte @NonNull [] value) throws IOException {
        final var existing = findByKey(key).orElse(null);
        if (existing != null) {
            return existing.getValue();
        }
        final var entries = new HashSet<>(entries());
        entries.add(new StorageEntry(key, value));
        updateWith(Set.copyOf(entries));
        return null;
    }

    @Override
    public byte @Nullable [] putEntry(final byte @NonNull [] key, final byte @NonNull [] value) throws IOException {
        final var previous = findByKey(key).orElse(null);
        final var entries = new HashSet<>(entries());
        if (previous != null) {
            if (Arrays.equals(previous.value(), value)) {
                // same value, no reason to update
                return value;
            }
            entries.remove(previous);
        }
        entries.add(new StorageEntry(key, value));
        updateWith(Set.copyOf(entries));
        return previous == null ? null : previous.getValue();
    }

    private Set<StorageEntry> entries() {
        final var entries = entriesRef.get();
        return entries == null ? Set.of() : entries;
    }

    private Optional<StorageEntry> findByKey(final byte @NonNull [] key) {
        return entries().stream().filter(entry -> Arrays.equals(key, entry.key())).findFirst();
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
        // write data to temp file
        final var tmpFile = File.createTempFile("localfile-", ".temp");
        try (var fos = new FileOutputStream(tmpFile)) {
            CipherUtils.toBase64Stream(list, secret, fos);
        }
        // replace file with newly written
        Files.move(tmpFile.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING);
    }

    private static byte[] secretFrom(final File file) throws IOException {
        validateFile(file);
        if (file.exists()) {
            try (var in = Base64.getDecoder().wrap(new FileInputStream(file))) {
                return in.readAllBytes();
            }
        }
        LOG.warn("Key file {} does not exist. Using generated key.", file);
        final var secret = CipherUtils.generateSecret();
        try (var out = Base64.getEncoder().wrap(new FileOutputStream(file))) {
            out.write(secret);
        }
        return secret;
    }

    private static byte[] validateSecret(final byte[] secret) {
        requireNonNull(secret);
        if (secret.length != 16 && secret.length != 32) {
            throw new IllegalArgumentException("Invalid secret length " + secret.length
                + ". Expected 16 or 32.");
        }
        return secret;
    }

    private static File validateFile(final File file) {
        requireNonNull(file);
        if (file.exists() && Files.isSymbolicLink(file.toPath())) {
            throw new IllegalArgumentException("Symbolic link detected in file path " + file.getPath());
        }
        return file;
    }

    private static Collection<StorageEntry> readData(final byte[] secret, final File file,
            final @Nullable File importFile) throws IOException {
        if (file.exists()) {
            try (var fis = new FileInputStream(file)) {
                return CipherUtils.fromBase64Stream(secret, fis);
            }
        }
        LOG.warn("Data file {} does not exist.", file);
        final var props = new Properties();
        if (importFile != null && importFile.exists()) {
            LOG.info("Importing initial data from unencrypted properties file {}", importFile);
            try (var fis = new FileInputStream(importFile)) {
                props.load(fis);
            }
        } else {
            LOG.info("Importing initial data from default properties");
            try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream("default.properties")) {
                props.load(in);
            }
        }
        final var data = new LinkedList<StorageEntry>();
        for (var name : props.stringPropertyNames()) {
            final var value = props.getProperty(name, "");
            if (!value.isEmpty()) {
                data.add(
                    new StorageEntry(name.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8)));
            }
        }
        try (var fos = new FileOutputStream(file)) {
            CipherUtils.toBase64Stream(data, secret, fos);
        }
        if (importFile != null && importFile.delete()) {
            LOG.info("Data moved to encrypted storage, import data file {} removed as insecure.", importFile);
        }
        return data;
    }
}
