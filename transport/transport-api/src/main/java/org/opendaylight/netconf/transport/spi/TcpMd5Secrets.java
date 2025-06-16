/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.net.InetAddress;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Semantics of a <a href="https://www.rfc-editor.org/rfc/rfc2385">TCP MD5 Signature option</a>: an association between
 * a remote address and its corresponding key.
 *
 * <p>Be mindful of the corresponding
 * <a href="https://www.rfc-editor.org/rfc/rfc3562">Key Management Considerations</a> when using this option.
 */
@Beta
@NonNullByDefault
public final class TcpMd5Secrets {
    public static final class Builder {
        private final ImmutableMap.Builder<InetAddress, ByteBuf> builder = ImmutableMap.builder();

        private Builder() {
            // Hidden on purpose
        }

        public Builder put(final InetAddress peer, final byte[] key) {
            builder.put(peer, Unpooled.wrappedBuffer(key.clone()).asReadOnly());
            return this;
        }

        public Builder put(final InetAddress peer, String asciiKey) throws CharacterCodingException {
            return put(peer, asciiBytes(asciiKey));
        }

        public Builder put(final InetAddress peer, final ByteBuf key) {
            return putUnsafe(peer, key.copy());
        }

        public Builder putUnsafe(final InetAddress peer, final ByteBuf key) {
            builder.put(peer, key.asReadOnly());
            return this;
        }

        public TcpMd5Secrets build() {
            final var map = builder.buildKeepingLast();
            return map.isEmpty() ? EMPTY : new TcpMd5Secrets(map);
        }
    }

    private static final TcpMd5Secrets EMPTY = new TcpMd5Secrets(ImmutableMap.of());

    private final ImmutableMap<InetAddress, ByteBuf> map;

    private TcpMd5Secrets(final ImmutableMap<InetAddress, ByteBuf> map) {
        this.map = requireNonNull(map);
    }

    public static TcpMd5Secrets of() {
        return EMPTY;
    }

    public static TcpMd5Secrets of(final InetAddress inetAddress, byte[] key) {
        return new TcpMd5Secrets(ImmutableMap.of(inetAddress, Unpooled.wrappedBuffer(key.clone())));
    }

    public static TcpMd5Secrets of(final InetAddress inetAddress, final ByteBuf key) {
        return new TcpMd5Secrets(ImmutableMap.of(inetAddress, key.copy().asReadOnly()));
    }

    public static TcpMd5Secrets of(final InetAddress inetAddress, final String password)
            throws CharacterCodingException {
        return new TcpMd5Secrets(ImmutableMap.of(inetAddress, asciiBytes(password)));
    }

    public static TcpMd5Secrets of(final Map<InetAddress, String> passwords) throws CharacterCodingException {
        final var size = passwords.size();
        return switch (size) {
            case 0 -> of();
            case 1 -> {
                final var entry = passwords.entrySet().iterator().next();
                yield of(entry.getKey(), entry.getValue());
            }
            default -> {
                final var builder = builder();
                for (var entry : passwords.entrySet()) {
                    builder.put(entry.getKey(), entry.getValue());
                }
                yield builder.build();
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Map<InetAddress, ByteBuf> asMap() {
        // protect the buffers' indices
        return Maps.transformValues(map, ByteBuf::slice);
    }

    Map<InetAddress, byte[]> toOption() {
        return Maps.transformValues(map, ByteBufUtil::getBytes);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this || obj instanceof TcpMd5Secrets other && map.equals(other.map);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("addresses", map.keySet()).toString();
    }

    private static ByteBuf asciiBytes(final String str) throws CharacterCodingException {
        return Unpooled.copiedBuffer(StandardCharsets.US_ASCII.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(str)))
            .asReadOnly();
    }
}
