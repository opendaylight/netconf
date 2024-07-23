/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * Mapping of {@link ErrorTag}s to {@link HttpStatusCode}s.
 */
@NonNullByDefault
public enum ErrorTagMapping {
    /**
     * Mapping specified by <a href="https://www.rfc-editor.org/rfc/rfc8040#page-74">RFC8040</a>:
     * {@link ErrorTag#DATA_MISSING} is reported as {@code 409 Conflict}. This may be confusing to users, as {@code GET}
     * requests to non-existent datastore resources do not report {@code 404 Not Found} as would be expected from any
     * other HTTP server.
     */
    RFC8040(HttpStatusCode.CONFLICT),
    /**
     * Mapping proposed by <a href="https://www.rfc-editor.org/errata/eid5565">Errata 5565</a>:
     * {@link ErrorTag#DATA_MISSING} is reported as {@code 404 Not Found}. This is more in-line with expectations rooted
     * in <a href="https://www.rfc-editor.org/rfc/rfc7231#section-6.5.4">HTTP/1.1 specification</a>.
     */
    ERRATA_5565(HttpStatusCode.NOT_FOUND);

    private ImmutableMap<ErrorTag, HttpStatusCode> tagToStatus;

    ErrorTagMapping(final HttpStatusCode dataMissing) {
        tagToStatus = ImmutableMap.<ErrorTag, HttpStatusCode>builder()
            .put(ErrorTag.IN_USE,                     HttpStatusCode.CONFLICT)
            .put(ErrorTag.INVALID_VALUE,              HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.TOO_BIG,                    HttpStatusCode.CONTENT_TOO_LARGE)
            .put(ErrorTag.MISSING_ATTRIBUTE,          HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.BAD_ATTRIBUTE,              HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.UNKNOWN_ATTRIBUTE,          HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.MISSING_ELEMENT,            HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.BAD_ELEMENT,                HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.UNKNOWN_ELEMENT,            HttpStatusCode.BAD_REQUEST)
            .put(ErrorTag.UNKNOWN_NAMESPACE,          HttpStatusCode.BAD_REQUEST)

            .put(ErrorTag.ACCESS_DENIED,              HttpStatusCode.FORBIDDEN)
            .put(ErrorTag.LOCK_DENIED,                HttpStatusCode.CONFLICT)
            .put(ErrorTag.RESOURCE_DENIED,            HttpStatusCode.CONFLICT)
            .put(ErrorTag.ROLLBACK_FAILED,            HttpStatusCode.INTERNAL_SERVER_ERROR)
            .put(ErrorTag.DATA_EXISTS,                HttpStatusCode.CONFLICT)
            .put(ErrorTag.DATA_MISSING,               dataMissing)

            .put(ErrorTag.OPERATION_NOT_SUPPORTED,    HttpStatusCode.NOT_IMPLEMENTED)
            .put(ErrorTag.OPERATION_FAILED,           HttpStatusCode.INTERNAL_SERVER_ERROR)
            .put(ErrorTag.PARTIAL_OPERATION,          HttpStatusCode.INTERNAL_SERVER_ERROR)
            .put(ErrorTag.MALFORMED_MESSAGE,          HttpStatusCode.BAD_REQUEST)
            .put(ErrorTags.RESOURCE_DENIED_TRANSPORT, HttpStatusCode.SERVICE_UNAVAILABLE)
            .build();
    }

    /**
     * Return the HTTP {@link HttpStatusCode} corresponding to specified {@link ErrorTag}.
     *
     * @param tag Error tag to map
     * @return A {@link HttpStatusCode}
     * @throws NullPointerException if {@code tag} is null
     */
    public HttpStatusCode statusOf(final ErrorTag tag) {
        final var known = tagToStatus.get(requireNonNull(tag));
        return known != null ? known : HttpStatusCode.INTERNAL_SERVER_ERROR;
    }
}
