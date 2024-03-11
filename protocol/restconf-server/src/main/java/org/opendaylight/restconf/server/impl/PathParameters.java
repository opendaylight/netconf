/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;

/**
 * Request URI Path parameters representing RESTCONF API Resource.
 *
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3">RFC 8040 -- API Resource</a>
 *
 * <p>
 * The URI path is being interpreted as a sequence of following elements
 * <ul>
 *     <li>Base path {@code "/{+restconf}"} representing root of the "ietf-restconf" module -- mandatory</li>
 *     <li>API resource name -- mandatory</li>
 *     <li>Child identifier within requested resource -- could contain slashes, optional</li>
 * </ul>
 *
 * <p>
 * Example. If configured {@code basePath="/rests"} then
 * <ul>
 *     <li>Path {@code /rests/data/} will have {@code apiResource="/data"} and empty childIdentifier</li>
 *     <li>Path {@code /rests/data/child/identifier} will have {@code apiResource="/data"} and
 *     {@code childIdentifier="child/identifier"}</li>
 *     <li>Path with non-matching base path like {@code /some/resource/} will have both apiResource and childIdentifier
 *     being empty</li>
 * </ul>
 *
 * @param apiResource requested API resource
 * @param childIdentifier optional resource child identifier
 */
record PathParameters(String apiResource, String childIdentifier) {
    static PathParameters EMPTY = new PathParameters("", "");

    public PathParameters {
        requireNonNull(apiResource);
        requireNonNull(childIdentifier);
    }

    boolean isEmpty() {
        return apiResource.isEmpty();
    }

    static PathParameters from(final String fullPath, final String basePath) {
        if (!fullPath.startsWith(basePath)) {
            return EMPTY;
        }
        final var maxIndex = fullPath.length() - 1;
        final var baseEndIndex = nextSlashIndex(fullPath, 0, maxIndex);
        final var resourceEndIndex = nextSlashIndex(fullPath, baseEndIndex, maxIndex);
        final var childStartIndex = resourceEndIndex < 0 ? -1 : resourceEndIndex + 1;
        final var base = cut(fullPath, 0, baseEndIndex, maxIndex);
        final var resource = cut(fullPath, baseEndIndex, resourceEndIndex, maxIndex);
        final var child = cut(fullPath, childStartIndex, -1, maxIndex);
        return base.equals(basePath) ? new PathParameters(resource, child) : EMPTY;
    }

    private static int nextSlashIndex(final String path, final int lastIndex, final int maxIndex) {
        return lastIndex < 0 || lastIndex == maxIndex ? -1 : path.indexOf('/', lastIndex + 1);
    }

    private static String cut(final String source, final int from, final int upTo, final int maxIndex) {
        if (from < 0 || from == maxIndex) {
            return "";
        }
        return upTo < 0 || upTo == source.length() ? source.substring(from) : source.substring(from, upTo);
    }
}
