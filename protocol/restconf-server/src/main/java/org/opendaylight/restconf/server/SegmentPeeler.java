/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.URI;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A utility to examine segments of a raw URI path. Given a raw path, it splits it up into segments and presents them
 * as a sequence of strings. The {@link #nextRaw()} method reports each segment as a raw string, whereas the
 * {@link #next()} method also performs a round of percent-decoding.
 */
@NonNullByDefault
final class SegmentPeeler implements Iterator<String> {
    private final String rawPath;

    // Pointer to the first character of the next segment
    private int next;

    SegmentPeeler(final URI uri) {
        this(requireNonNull(uri.getRawPath(), () -> "No path present in " + uri));
    }

    @VisibleForTesting
    SegmentPeeler(final String rawPath) {
        final var length = rawPath.length();
        if (length == 0 || rawPath.charAt(0) != '/') {
            throw new IllegalArgumentException("Path must start with a '/'");
        }
        this.rawPath = rawPath;
        next = length != 1 ? 1 : 2;
    }

    @Override
    public boolean hasNext() {
        return next <= rawPath.length();
    }

    @Override
    public String next() {
        return QueryStringDecoder.decodeComponent(nextRaw());
    }

    /**
     * Return the next segment in raw, undecoded form.
     *
     * @return the next segment in raw, undecoded form
     * @throws NoSuchElementException if there are no remaining segments
     */
    public String nextRaw() {
        final var begin = next;
        final var length = rawPath.length();
        if (begin > length) {
            throw new NoSuchElementException();
        }
        if (begin == length) {
            next = length + 1;
            return "";
        }

        var end = rawPath.indexOf('/', begin);
        if (end == -1) {
            end = length;
        }
        next = end + 1;
        return rawPath.substring(begin, end);
    }

    /**
     * Return the remaining path, or an empty string.
     *
     * @return the remaining path
     */
    String remaining() {
        return rawPath.substring(next - 1);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("path", rawPath).add("remaining", remaining()).toString();
    }
}
