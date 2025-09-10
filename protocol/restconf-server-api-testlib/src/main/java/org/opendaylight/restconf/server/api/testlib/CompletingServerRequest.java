/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.AbstractServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AbstractServerRequest} which eventually completes.
 */
@NonNullByDefault
public final class CompletingServerRequest<T> extends AbstractServerRequest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(CompletingServerRequest.class);

    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final QName requestEncoding;

    public CompletingServerRequest() {
        this(PrettyPrintParam.TRUE);
    }

    public CompletingServerRequest(final PrettyPrintParam prettyPrint) {
        this(QueryParameters.of(), prettyPrint);
    }

    public CompletingServerRequest(final QueryParameters queryParameters) {
        this(queryParameters, PrettyPrintParam.TRUE);
    }

    public CompletingServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
        this(null, queryParameters, defaultPrettyPrint);
    }

    public CompletingServerRequest(final @Nullable Principal principal, final QueryParameters queryParameters,
            final PrettyPrintParam defaultPrettyPrint) {
        this(principal, queryParameters, defaultPrettyPrint, EncodeJson$I.QNAME);
    }

    public CompletingServerRequest(final @Nullable Principal principal, final QueryParameters queryParameters,
            final PrettyPrintParam defaultPrettyPrint, final QName requestEncoding) {
        super(principal, queryParameters, defaultPrettyPrint);
        this.requestEncoding = requireNonNull(requestEncoding);
    }

    public T getResult() throws RequestException, InterruptedException, TimeoutException {
        return getResult(1, TimeUnit.SECONDS);
    }

    @SuppressWarnings("checkstyle:avoidHidingCauseException")
    public T getResult(final int timeout, final TimeUnit unit)
            throws RequestException, InterruptedException, TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            LOG.debug("Request failed", e);
            final var cause = e.getCause();
            Throwables.throwIfInstanceOf(cause, RequestException.class);
            Throwables.throwIfUnchecked(cause);
            throw new UncheckedExecutionException(cause);
        }
    }

    @Override
    public QName requestEncoding() {
        return requestEncoding;
    }

    @Override
    public @Nullable TransportSession session() {
        return null;
    }

    @Override
    protected void onSuccess(final T result) {
        future.complete(result);
    }

    @Override
    protected void onFailure(final YangErrorsBody errors) {
        future.completeExceptionally(new RequestException(errors.errors(), null, "reconstructed for testing"));
    }

    @Override
    public void completeWith(final ErrorTag errorTag, final FormattableBody body) {
        throw new UnsupportedOperationException();
    }
}
