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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Utility class. Provides methods for AES-GSM-SIV (RFC-8452) dynamic encryption/description of binary data.
 */
final class CipherUtils {
    private static final int NONCE_BYTES = 12;
    private static final int MAC_SIZE = 128;
    private static final int ENTRY_LENGTHS_BYTES = Integer.BYTES * 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final boolean ENCRYPTING_MODE = true;
    private static final boolean DECRYPTING_MODE = false;

    private CipherUtils() {
        // utility class
    }

    /**
     * Encrypts data entries and writes result into provided {@link OutputStream} as Base64 encoded binary data.
     * The 12 byte long nonce is generated dynamically and placed into beginning of output before encrypted data.
     *
     * @param collection collection of data entries to be encrypted
     * @param secret 16 or 32 byte long secret key
     * @param output the output stream the result data expected to be written to
     * @throws IOException on write failure
     */
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

    /**
     * Decrypts data entries from binary data readable from {@link InputStream} provided. The binary data is expected
     * to be Base 64 encoded, 12 byte long nonce is expected in vary beginning of data before encrypted data.
     *
     * @param secret 16 or 32 byte long secret key
     * @param input the input stream the data to be read from
     * @return collection of decrypted data entries
     * @throws IOException on read failure
     */
    static Collection<StorageEntry> fromBase64Stream(final byte[] secret, final InputStream input) throws IOException {
        try (var in = Base64.getDecoder().wrap(input)) {
            final var nonce = in.readNBytes(NONCE_BYTES);
            try (var cipherIn = new CipherInputStream(in, cipher(DECRYPTING_MODE, secret, nonce))) {
                StorageEntry entry;
                final var collection = new ArrayList<StorageEntry>();
                while ((entry = readEntry(cipherIn)) != null) {
                    collection.add(entry);
                }
                return List.copyOf(collection);
            }
        }
    }

    /**
     * Generates random secret of 32 bytes.
     *
     * @return generate secret as byte array
     */
    static byte[] generateSecret() {
        return RANDOM.generateSeed(32);
    }

    private static GCMSIVBlockCipher cipher(final boolean mode, final byte[] secret, final byte[] nonce) {
        final var cipher = new GCMSIVBlockCipher();
        cipher.init(mode, new AEADParameters(new KeyParameter(secret), MAC_SIZE, nonce));
        return cipher;
    }

    private static void writeEntry(final StorageEntry entry, final OutputStream out) throws IOException {
        // write key length as int, value length as int
        final var lengthData = ByteBuffer.allocate(ENTRY_LENGTHS_BYTES)
            .putInt(entry.key().length)
            .putInt(entry.value().length)
            .array();
        out.write(lengthData);
        // write key then value
        out.write(entry.key());
        out.write(entry.value());
    }

    private static StorageEntry readEntry(final InputStream in) throws IOException {
        // read key and value data lengths first
        final var lengthsData = new byte[ENTRY_LENGTHS_BYTES];
        if (in.read(lengthsData) != ENTRY_LENGTHS_BYTES) {
            return null;
        }
        final var lengths = ByteBuffer.wrap(lengthsData);
        final var keyLength = lengths.getInt();
        final var valueLength = lengths.getInt();
        // read data
        final var key = new byte [keyLength];
        final var value = new byte [valueLength];
        if (in.read(key) == keyLength && in.read(value) == valueLength) {
            return new StorageEntry(key, value);
        }
        return null;
    }
}
