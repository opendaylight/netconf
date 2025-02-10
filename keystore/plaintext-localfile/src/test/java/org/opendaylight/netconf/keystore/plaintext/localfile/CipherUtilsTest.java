/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.localfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CipherUtilsTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<StorageEntry> TEST_ENTRIES = IntStream.range(0, 10)
        .mapToObj(index -> new StorageEntry("key-" + index, "value-" + index))
        .toList();

    @ParameterizedTest(name = "Write then read encrypted with {0} byte secret")
    @ValueSource(ints = {16, 32})
    void twoWayConversion(final int keyLength) throws IOException {
        final var output = new ByteArrayOutputStream();
        final var secret = RANDOM.generateSeed(keyLength);
        CipherUtils.toBase64Stream(TEST_ENTRIES, secret, output);
        final var encoded = output.toByteArray();

        final var input = new ByteArrayInputStream(encoded);
        final var decoded = CipherUtils.fromBase64Stream(secret, input);
        assertNotNull(decoded);
        assertEquals(TEST_ENTRIES.size(), decoded.size());
        assertTrue(decoded.containsAll(TEST_ENTRIES));
    }
}
