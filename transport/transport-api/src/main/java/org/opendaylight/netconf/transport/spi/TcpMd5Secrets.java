/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Semantics of a <a href="https://www.rfc-editor.org/rfc/rfc2385">TCP MD5 Signature option</a>: an association
 * between a remote address and its corresponding key.
 *
 * <p>Be mindful of the corresponding <a href="https://www.rfc-editor.org/rfc/rfc3562">Key Management Considerations</a>
 * when using this option.
 */
@Beta
@NonNullByDefault
public final class TcpMd5Secrets {
    private static final TcpMd5Secrets EMPTY = new TcpMd5Secrets(ImmutableMap.of());

    private final ImmutableMap<InetAddress, byte[]> map;

    private TcpMd5Secrets(final ImmutableMap<InetAddress, byte[]> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    public static TcpMd5Secrets of() {
        return EMPTY;
    }

    public static TcpMd5Secrets of(final InetAddress inetAddress, final String password) {
        return new TcpMd5Secrets(ImmutableMap.of(inetAddress, asciiBytes(password)));
    }

    public static TcpMd5Secrets of(final Map<InetAddress, String> passwords) {
        return switch (passwords.size()) {
            case 0 -> of();
            case 1 -> {
                final var entry = passwords.entrySet().iterator().next();
                yield of(entry.getKey(), entry.getValue());
            }
            default -> new TcpMd5Secrets(passwords.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> asciiBytes(entry.getValue()))));
        };
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    // Note: internal access only passed directly to Netty: it is okay to leak
    ImmutableMap<InetAddress, byte[]> map() {
        return map;
    }

    // Note: no hashCode()/equals() on purpose, we do not want to allow indirect probing via equals()

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("addresses", map.keySet()).toString();
    }

    private static byte[] asciiBytes(String str) {
        // FIXME: strict conversion via an explicit CharsetEncoder:
        //        final ByteBuffer encoded;
        //        try {
        //            encoded = StandardCharsets.US_ASCII.newEncoder()
        //                .onMalformedInput(CodingErrorAction.REPORT)
        //                .onUnmappableCharacter(CodingErrorAction.REPORT)
        //                .encode(CharBuffer.wrap(password));
        //        } catch (CharacterCodingException e) {
        //            throw new UnsupportedConfigurationException("Invalid password", e);
        //        }
        return str.getBytes(StandardCharsets.US_ASCII);
    }
}
