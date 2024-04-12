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
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.concepts.Mutable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A request to {@link RestconfServer}. It contains state and binding established by whoever is performing binding to
 * HTTP transport layer. This includes:
 * <ul>
 *   <li>HTTP request {@link #queryParameters() query parameters},</li>
 *   <li>{@link #prettyPrint() pretty printing}, including affected by query parameters<li>
 * </ul>
 * It notably does <b>not</b> hold the HTTP request path, nor the request body. Those are passed as separate arguments
 * to server methods as implementations of those methods are expected to act on them.
 */
@NonNullByDefault
public abstract class ServerRequest<T> implements Mutable {
    private static final Logger LOG = LoggerFactory.getLogger(ServerRequest.class);

    // TODO: this is where a binding to security principal and access control should be:
    //       - we would like to be able to have java.security.Principal#name() for logging purposes
    //       - we need to have a NACM-capable interface, through which we can check permissions (such as data PUT) and
    //         establish output filters (i.e. excluding paths inaccessible path to user from a data GET a ContainerNode)

    private final QueryParameters queryParameters;
    private final PrettyPrintParam prettyPrint;

    protected ServerRequest(final QueryParameters queryParameters,
            final PrettyPrintParam defaultPrettyPrint) {
        final var prettyParam = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        if (prettyParam == null) {
            this.queryParameters = queryParameters;
            prettyPrint = requireNonNull(defaultPrettyPrint);
        } else {
            this.queryParameters = queryParameters.withoutParam(PrettyPrintParam.uriName);
            prettyPrint = prettyParam;
        }
    }

    public final QueryParameters queryParameters() {
        return queryParameters;
    }

    // FIXME: remove this method?
    public final PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    /**
     * Complete this request with a failure.
     *
     * @param errorTag the {@link ErrorTag} for HTTP status determination
     * @param body response body
     */
    public abstract void failWith(ErrorTag errorTag, FormattableBody body);

    public final void failWith(final ServerError error) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Request failed", new ServerException(error));
        }
        failWith(error.tag(), new YangErrorsBody(error));
    }

    public final void failWith(final ServerException cause) {
        LOG.debug("Request failed", cause);
        final var errors = cause.errors();
        failWith(errors.get(0).tag(), new YangErrorsBody(errors));
    }

    public abstract void succeedWith(T result);
}
