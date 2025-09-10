/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.PendingRequestListener;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.MappingServerRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * The {@link ServerRequest}s implementation we are passing to {@link RestconfServer}. Completion callbacks are routed
 * through the supplied {@link AbstractPendingRequest} towards the supplied {@link PendingRequestListener}.
 */
final class NettyServerRequest<T> extends MappingServerRequest<T> {
    private final @NonNull AbstractPendingRequest<T> request;
    private final @NonNull PendingRequestListener listener;

    private NettyServerRequest(final EndpointInvariants invariants, final AbstractPendingRequest<T> request,
            final PendingRequestListener listener) {
        super(request.principal, QueryParameters.ofMultiValue(new QueryStringDecoder(request.targetUri).parameters()),
            invariants.defaultPrettyPrint(), invariants.errorTagMapping());
        this.request = requireNonNull(request);
        this.listener = requireNonNull(listener);
    }

    NettyServerRequest(final AbstractPendingRequest<@NonNull T> request, final PendingRequestListener listener) {
        this(request.invariants, request, listener);
    }

    @Override
    public TransportSession session() {
        return request.session;
    }

    @Override
    public @Nullable QName contentEncoding() {
        if (request instanceof PendingRequestWithBody<?, ?> prb) {
            return switch (prb.contentEncoding) {
                case JSON -> EncodeJson$I.QNAME;
                case XML -> EncodeXml$I.QNAME;
            };
        }
        return null;
    }

    @Override
    protected void onFailure(final HttpStatusCode status, final FormattableBody body) {
        request.onFailure(listener, this, status, body);
    }

    @Override
    protected void onSuccess(final T result) {
        request.onSuccess(listener, this, result);
    }
}
