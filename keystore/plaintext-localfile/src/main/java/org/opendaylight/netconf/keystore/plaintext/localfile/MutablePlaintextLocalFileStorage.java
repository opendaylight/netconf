/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.keystore.plaintext.api.MutablePlaintextStorage;

/**
 * Local file implementation of {@link MutablePlaintextStorage}.
 */
@SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
public class MutablePlaintextLocalFileStorage extends AbstractPlaintextLocalFileStorage
        implements MutablePlaintextStorage {
    private final AtomicReference<Set<StorageEntry>> entriesRef = new AtomicReference<>();

    public MutablePlaintextLocalFileStorage(final File file, final byte[] secret) {
        super(file, secret);
        entriesRef.set(Set.copyOf(initFromFile()));
    }

    public MutablePlaintextLocalFileStorage(final File file, final byte[] secret,
        final Map<byte[], byte[]> defaults) {
        super(file, secret);
        entriesRef.set(Set.copyOf(initWithDefaults(defaults)));
    }

    @Override
    public byte @Nullable [] lookup(byte @NonNull [] key) {
        return findByKey(key).map(StorageEntry::getValue).orElse(null);
    }

    @Override
    protected StorageEntry[] iteratorEntries() {
        return entries().toArray(StorageEntry[]::new);
    }

    @Override
    public byte @Nullable [] removeKey(byte @NonNull [] key) throws IOException {
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
    public boolean removeEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException {
        final var entries = new HashSet<>(entries());
        final var removed = entries.remove(new StorageEntry(key, value));
        if (removed) {
            updateWith(Set.copyOf(entries));
        }
        return removed;
    }

    @Override
    public byte @Nullable [] insertEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException {
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
    public byte @Nullable [] putEntry(byte @NonNull [] key, byte @NonNull [] value) throws IOException {
        final var previous = findByKey(key).orElse(null);
        final var entries = new HashSet<>(entries());
        if (previous != null) {
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
}
