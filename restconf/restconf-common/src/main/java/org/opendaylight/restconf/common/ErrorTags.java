/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ErrorTag} mapping to HTTP errors. Aside from the mappings defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-7">RFC8040 section 7</a>, we also define tags which
 * map to useful {@link Status} codes.
 */
@Beta
@NonNullByDefault
public final class ErrorTags {
    /**
     * Error reported when the request is valid, but the resource cannot be accessed. This tag typically maps to
     * {@link Status#SERVICE_UNAVAILABLE}.
     */
    // FIXME: redefine as SERVICE_UNAVAILABLE? It would be more obvious
    public static final ErrorTag RESOURCE_DENIED_TRANSPORT = new ErrorTag("resource-denied-transport");

    private static final Logger LOG = LoggerFactory.getLogger(ErrorTags.class);
    private static final ImmutableMap<ErrorTag, Status> WELL_KNOWN_ERROR_TAGS = ImmutableMap.<ErrorTag, Status>builder()
        .put(ErrorTag.IN_USE,                     Status.CONFLICT)
        .put(ErrorTag.INVALID_VALUE,              Status.BAD_REQUEST)
        .put(ErrorTag.TOO_BIG,                    Status.REQUEST_ENTITY_TOO_LARGE)
        .put(ErrorTag.MISSING_ATTRIBUTE,          Status.BAD_REQUEST)
        .put(ErrorTag.BAD_ATTRIBUTE,              Status.BAD_REQUEST)
        .put(ErrorTag.UNKNOWN_ATTRIBUTE,          Status.BAD_REQUEST)
        .put(ErrorTag.MISSING_ELEMENT,            Status.BAD_REQUEST)
        .put(ErrorTag.BAD_ELEMENT,                Status.BAD_REQUEST)
        .put(ErrorTag.UNKNOWN_ELEMENT,            Status.BAD_REQUEST)
        .put(ErrorTag.UNKNOWN_NAMESPACE,          Status.BAD_REQUEST)

        .put(ErrorTag.ACCESS_DENIED,              Status.FORBIDDEN)
        .put(ErrorTag.LOCK_DENIED,                Status.CONFLICT)
        .put(ErrorTag.RESOURCE_DENIED,            Status.CONFLICT)
        .put(ErrorTag.ROLLBACK_FAILED,            Status.INTERNAL_SERVER_ERROR)
        .put(ErrorTag.DATA_EXISTS,                Status.CONFLICT)
        .put(ErrorTag.DATA_MISSING,               dataMissingHttpStatus())

        .put(ErrorTag.OPERATION_NOT_SUPPORTED,    Status.NOT_IMPLEMENTED)
        .put(ErrorTag.OPERATION_FAILED,           Status.INTERNAL_SERVER_ERROR)
        .put(ErrorTag.PARTIAL_OPERATION,          Status.INTERNAL_SERVER_ERROR)
        .put(ErrorTag.MALFORMED_MESSAGE,          Status.BAD_REQUEST)
        .put(ErrorTags.RESOURCE_DENIED_TRANSPORT, Status.SERVICE_UNAVAILABLE)
        .build();

    private ErrorTags() {
        // Hidden on purpose
    }

    /**
     * Return the HTTP {@link Status} corresponding to specified {@link ErrorTag}.
     *
     * @param tag Error tag to map
     * @return HTTP Status
     * @throws NullPointerException if {@code tag} is null
     */
    public static Status statusOf(final ErrorTag tag) {
        final Status known = WELL_KNOWN_ERROR_TAGS.get(requireNonNull(tag));
        return known != null ? known : Status.INTERNAL_SERVER_ERROR;
    }

    private static Status dataMissingHttpStatus() {
        // Control over the HTTP status reported on "data-missing" conditions. This defaults to disabled,
        // HTTP status 409 as specified by RFC8040 (and all previous drafts). See the discussion in:
        // https://www.rfc-editor.org/errata/eid5565
        // https://mailarchive.ietf.org/arch/msg/netconf/hkVDdHK4xA74NgvXzWP0zObMiyY/
        final String propName = "org.opendaylight.restconf.eid5565";
        final String propValue = System.getProperty(propName, "disabled");
        switch (propValue) {
            case "enabled":
                // RFC7231 interpretation: 404 Not Found
                LOG.info("RESTCONF data-missing condition is reported as HTTP status 404 (Errata 5565)");
                return Status.NOT_FOUND;
            case "disabled":
                break;
            default:
                LOG.warn("Unhandled {} value \"{}\", assuming disabled", propName, propValue);
        }

        // RFC8040 specification: 409 Conflict
        return Status.CONFLICT;
    }
}
