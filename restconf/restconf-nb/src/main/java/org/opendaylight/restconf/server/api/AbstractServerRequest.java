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
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * Abstract base class for {@link ServerRequest} implementations.
 *
 * @param <T> type of reported result
 */
@NonNullByDefault
public abstract non-sealed class AbstractServerRequest<T> implements ServerRequest<T> {
    private final QueryParameters queryParameters;
    private final PrettyPrintParam prettyPrint;

    // TODO: this is where a binding to security principal and access control should be:
    //       - we would like to be able to have java.security.Principal#name() for logging purposes
    //       - we need to have a NACM-capable interface, through which we can check permissions (such as data PUT) and
    //         establish output filters (i.e. excluding paths inaccessible path to user from a data GET a ContainerNode)

    /**
     * Default constructor. It takes user-supplied {@link QueryParameters} and a set of default implemented parameter
     * values (for now only {@link PrettyPrintParam}). It implemented parameters are stripped from the user-supplied
     * ones, hence not exposing to {@link RestconfServer} implementation.
     *
     * @param queryParameters user-supplied query parameters
     * @param defaultPrettyPrint default {@link PrettyPrintParam}
     */
    protected AbstractServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
        // We always recognize PrettyPrintParam and it is an output processing flag. We therefore filter it if present.
        final var tmp = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        if (tmp != null) {
            this.queryParameters = queryParameters.withoutParam(PrettyPrintParam.uriName);
            prettyPrint = tmp;
        } else {
            this.queryParameters = queryParameters;
            prettyPrint = requireNonNull(defaultPrettyPrint);
        }
    }

    @Override
    public final QueryParameters queryParameters() {
        return queryParameters;
    }

    @Override
    public final void completeWith(final T result) {
        onSuccess(requireNonNull(result));
    }

    @Override
    public final void completeWith(final RestconfDocumentedException failure) {
        onFailure(requireNonNull(failure));
    }

    /**
     * Return the effective {@link PrettyPrintParam}.
     *
     * @return the effective {@link PrettyPrintParam}
     */
    protected final PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    protected abstract void onSuccess(T result);

    protected abstract void onFailure(RestconfDocumentedException failure);
}
