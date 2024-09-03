/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Request URI Path parameters representing RESTCONF API Resource.
 *
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3">RFC 8040: API Resource</a>
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
 * Only following apiResource values are supported: {@value DATA}, {@value OPERATIONS}, {@value YANG_LIBRARY_VERSION}
 * and {@value MODULES}.
 *
 * <p>
 * Example. If configured {@code basePath="/rests"} then
 * <ul>
 *     <li>Path {@code /rests/data/} will have {@code apiResource="/data"} and empty childIdentifier</li>
 *     <li>Path {@code /rests/data/child/identifier} will have {@code apiResource="/data"} and
 *     {@code childIdentifier="child/identifier"}</li>
 *     <li>Path with non-matching base path like {@code "/some/data/"} or having unsupported apiResource value
 *     like {@code "/rests/unknown"} will result both apiResource and childIdentifier being empty</li>
 * </ul>
 *
 * <p>
 * Discovery requests with URI path starting with {@value DISCOVERY_BASE} are also supported. Eligible API resource
 * equivalents are {@value HOST_META} and {@value HOST_META_JSON}.
 *
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.1">RFC 8040: Root Resource Discovery</a>
 * and <a href="https://datatracker.ietf.org/doc/html/rfc6415#appendix-A">RFC 6415: JRD Document Format</a>.
 *
 * @param apiResource requested API resource
 * @param childIdentifier optional resource child identifier
 */
@NonNullByDefault
record PathParameters(String apiResource, String childIdentifier) {

    /**
     * URI path prefix for discovery requests.
     */
    static final String DISCOVERY_BASE = "/.well-known";

    /**
     * API resource for datastore.
     */
    static final String DATA = "/data";

    /**
     * API resource for operations.
     */
    static final String OPERATIONS = "/operations";

    /**
     * API resource for supported yang library version.
     */
    static final String YANG_LIBRARY_VERSION = "/yang-library-version";

    /**
     * API resource equivalent for module requests.
     */
    static final String MODULES = "/modules";

    /**
     * API resource equivalent for streams requests.
     */

    static final String STREAMS = "/streams";

    /**
     * API resource equivalent for discovery XRD request.
     */
    static final String HOST_META = "/host-meta";

    /**
     * API resource equivalent for discovery JRD request.
     */
    static final String HOST_META_JSON = "/host-meta.json";

    private static final Set<String> API_RESOURCES = Set.of(DATA, OPERATIONS, YANG_LIBRARY_VERSION, MODULES, STREAMS);
    private static final Set<String> DISCOVERY_API_RESOURCES = Set.of(HOST_META, HOST_META_JSON);
    private static final PathParameters EMPTY = new PathParameters("", "");

    PathParameters {
        requireNonNull(apiResource);
        requireNonNull(childIdentifier);
    }

    static PathParameters from(final String fullPath, final String basePath) {
        if (!fullPath.startsWith(basePath) && !fullPath.startsWith(DISCOVERY_BASE)) {
            return EMPTY;
        }
        final var maxIndex = fullPath.length() - 1;
        final var baseEndIndex = nextSlashIndex(fullPath, 0, maxIndex);
        final var resourceEndIndex = nextSlashIndex(fullPath, baseEndIndex, maxIndex);
        final var childStartIndex = resourceEndIndex < 0 ? -1 : resourceEndIndex + 1;
        final var base = cut(fullPath, 0, baseEndIndex, maxIndex);
        final var resource = cut(fullPath, baseEndIndex, resourceEndIndex, maxIndex);
        final var child = cut(fullPath, childStartIndex, -1, maxIndex);

        return basePath.equals(base) && API_RESOURCES.contains(resource)
            || DISCOVERY_BASE.equals(base) && DISCOVERY_API_RESOURCES.contains(resource)
            ? new PathParameters(resource, child) : EMPTY;
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
