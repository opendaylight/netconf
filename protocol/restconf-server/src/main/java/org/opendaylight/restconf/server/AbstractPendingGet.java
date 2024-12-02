/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PendingRequest;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An abstract base class for {@link PendingRequest}s servicing both GET and HEAD requests. It handles result
 * transformation so that of HEAD requests we only respond with appropriate headers.
 *
 * <p>We deliberately do not expose {@link PrettyPrintParam} to {@link #fillHeaders(Object, HttpHeaders)}, so that
 * subclasses are not tempted to attempt to attain the {@code Content-Type} header. While this may seem to be
 * a violation of {@code HEAD} method mechanics, it is in fact taking full advantage of efficiencies outline in second
 * paragraph of <a href="https://www.rfc-editor.org/rfc/rfc9110#name-head">RFC9110, section 9.3.2</a>:
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class AbstractPendingGet<T> extends PendingRequestWithoutBody<T> {
    private final boolean withContent;

    AbstractPendingGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final boolean withContent) {
        super(invariants, session, targetUri, principal);
        this.withContent = withContent;
    }

    @Override
    final Response transformResult(final NettyServerRequest<?> request, final T result) {
        if (withContent) {
            return transformResultImpl(request, result);
        }

        final var headers = extractHeaders(result);
        return headers.isEmpty() ? EmptyResponse.OK : HeadersResponse.ofTrusted(HttpResponseStatus.OK, headers);
    }

    abstract Response transformResultImpl(NettyServerRequest<?> request, T result);

    abstract List<CharSequence> extractHeaders(T result);

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("method", withContent ? ImplementedMethod.GET : ImplementedMethod.HEAD);
    }
}
