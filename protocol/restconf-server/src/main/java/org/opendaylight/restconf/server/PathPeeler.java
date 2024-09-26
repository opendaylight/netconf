/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.URI;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A utility to examine segments of a raw URI path. Each invocation of {@link #next()} consumes one segment of the raw
 * path and reports it decoded. At any time {@link #remaining()} reports the part of the path that remains to be
 * consumed.
 */
@NonNullByDefault
final class PathPeeler implements Iterator<String> {
    private final String rawPath;

    // Pointer to the first character of the next segment
    private int next;

    PathPeeler(final URI uri) {
        this(requireNonNull(uri.getRawPath(), () -> "No path present in " + uri));
    }

    PathPeeler(final String rawPath) {
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
        final var start = next;
        final var length = rawPath.length();
        if (start > length) {
            throw new NoSuchElementException();
        }

        if (start == length) {
            next = length + 1;
            return "";
        }

        final String str;
        final var end = rawPath.indexOf('/', start);
        if (end == -1) {
            str = rawPath.substring(start);
            next = length + 1;
        } else {
            str = rawPath.substring(start, end);
            next = end + 1;
        }
        return QueryStringDecoder.decodeComponent(str);
    }

    /**
     * Return the remaining path
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
