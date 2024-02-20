/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class. Provides methods for dynamic encryption/description using AES-GSM-SIV
 */
final class CipherUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CipherUtils.class);

    private static final int NONCE_BYTES = 12;
    private static final int MAC_SIZE = 128;
    private static final int ENTRY_LENGTHS_BYTES = Integer.BYTES * 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final boolean ENCRYPTING_MODE = true;
    private static final boolean DECRYPTING_MODE = false;

    private CipherUtils() {
        // utility class
    }

    static void toBase64Stream(final Collection<StorageEntry> collection, final byte[] secret,
            final OutputStream output) throws IOException {
        try (var out = Base64.getEncoder().wrap(output)) {
            final var nonce = RANDOM.generateSeed(NONCE_BYTES);
            out.write(nonce);
            try (var cipherOut = new CipherOutputStream(out, cipher(ENCRYPTING_MODE, secret, nonce))) {
                for (var entry : collection) {
                    writeEntry(entry, cipherOut);
                }
            }
        }
    }

    static Collection<StorageEntry> fromBase64Stream(final byte[] secret, final InputStream input) throws IOException {
        try (var in = Base64.getDecoder().wrap(input)) {
            final var nonce = in.readNBytes(NONCE_BYTES);
            try (var cipherIn = new CipherInputStream(in, cipher(DECRYPTING_MODE, secret, nonce))) {
                final var collection = new LinkedList<StorageEntry>();
                while (readEntry(cipherIn, collection)) {
                    // loop reading entries to collection
                }
                return List.copyOf(collection);
            }
        }
    }

    private static GCMSIVBlockCipher cipher(boolean mode, final byte[] secret, final byte[] nonce) {
        final var cipher = new GCMSIVBlockCipher();
        cipher.init(mode, new AEADParameters(new KeyParameter(secret), MAC_SIZE, nonce));
        return cipher;
    }

    private static void writeEntry(final StorageEntry entry, final OutputStream out) throws IOException {
        // write key length as int, value length as int
        final var lengthData = ByteBuffer.allocate(ENTRY_LENGTHS_BYTES)
            .putInt(entry.key().length).putInt(entry.value().length).array();
        out.write(lengthData);
        LOG.info("write entry {} bytes key + {} bytes value", entry.key().length, entry.value().length);
        // write key then value
        out.write(entry.key());
        out.write(entry.value());
        LOG.info("entry written");
    }

    private static boolean readEntry(final InputStream in, final Collection<StorageEntry> collection)
            throws IOException {
        // read key and value data lengths first
        final var lengthsData = new byte[ENTRY_LENGTHS_BYTES];
        if (in.read(lengthsData) != ENTRY_LENGTHS_BYTES) {
            return false;
        }
        final var lengths = ByteBuffer.wrap(lengthsData);
        final var keyLength = lengths.getInt();
        final var valueLength = lengths.getInt();
        LOG.info("read entry {} bytes key + {} bytes value", keyLength, valueLength);
        // read data
        final var key = new byte [keyLength];
        final var value = new byte [valueLength];
        if (in.read(key) == keyLength && in.read(value) == valueLength) {
            collection.add(new StorageEntry(key, value));
            return true;
        }
        return false;
    }
}
