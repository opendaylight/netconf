/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A request to {@link RestconfServer}. It contains state and binding established by whoever is performing binding to
 * HTTP transport layer. This includes:
 * <ul>
 *   <li>HTTP request {@link #queryParameters() query parameters},</li>
 *   <li>{@link #format() format parameters}, including those affected by query parameters<li>
 * </ul>
 * It notably does <b>not</b> hold the HTTP request path, nor the request body. Those are passed as separate arguments
 * to server methods as implementations of those methods are expected to act on them.
 */
@NonNullByDefault
public record ServerRequest(QueryParameters queryParameters, FormatParameters format) {
    // TODO: this is where a binding to security principal and access control should be:
    //       - we would like to be able to have java.security.Principal#name() for logging purposes
    //       - we need to have a NACM-capable interface, through which we can check permissions (such as data PUT) and
    //         establish output filters (i.e. excluding paths inaccessible path to user from a data GET a ContainerNode)
    public ServerRequest {
        requireNonNull(queryParameters);
        requireNonNull(format);
    }

    private ServerRequest(final QueryParameters queryParameters, final PrettyPrintParam prettyPrint) {
        this(queryParameters, prettyPrint.value() ? FormatParameters.PRETTY : FormatParameters.COMPACT);
    }

    public static ServerRequest of(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
        final var prettyPrint = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        return prettyPrint == null ? new ServerRequest(queryParameters, defaultPrettyPrint)
            : new ServerRequest(queryParameters.withoutParam(PrettyPrintParam.uriName), prettyPrint);
    }
}
