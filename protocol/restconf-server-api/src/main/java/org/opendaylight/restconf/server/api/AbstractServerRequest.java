/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.AbstractRequest;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for {@link ServerRequest} implementations. Each instance is automatically assigned a
 * <a href="https://www.rfc-editor.org/rfc/rfc4122#section-4.4">type 4 UUID</a>.
 *
 * @param <R> type of reported result
 */
public abstract non-sealed class AbstractServerRequest<R> extends AbstractRequest<R> implements ServerRequest<R> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractServerRequest.class);

    private final @NonNull QueryParameters queryParameters;
    private final @NonNull PrettyPrintParam prettyPrint;

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
    @NonNullByDefault
    protected AbstractServerRequest(final @Nullable Principal principal, final QueryParameters queryParameters,
            final PrettyPrintParam defaultPrettyPrint) {
        super(principal);

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
    public @Nullable QName contentEncoding() {
        // Default for transports or methods without a body
        return null;
    }

    /**
     * Return the effective {@link PrettyPrintParam}.
     *
     * @return the effective {@link PrettyPrintParam}
     */
    public final @NonNull PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    @Override
    public final void completeWith(final YangErrorsBody errors) {
        onFailure(requireNonNull(errors));
    }

    @Override
    protected final void onFailure(final RequestException failure) {
        LOG.debug("Request {} failed", this, failure);
        onFailure(new YangErrorsBody(failure.errors()));
    }

    protected abstract void onFailure(@NonNull YangErrorsBody errors);

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("parameters", queryParameters)
            .add("prettyPrint", prettyPrint);
    }
}
