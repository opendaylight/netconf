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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * An abstract base class for {@link PendingRequest}s servicing both GET and HEAD requests. It handles result
 * transformation so that of HEAD requests we only respond with appropriate headers.
 *
 * <p>
 * We deliberately do not expose {@link PrettyPrintParam} to {@link #fillHeaders(Object, HttpHeaders)}, so that
 * subclasses are not tempted to attempt to attain the {@code Content-Type} header. While this may seem to be
 * a violation of {@code HEAD} method mechanics, it is in fact taking full advantage of efficiencies outline in second
 * paragraph of <a href="https://www.rfc-editor.org/rfc/rfc9110#name-head">RFC9110, section 9.3.2</a>:
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class AbstractPendingGet<T> extends AbstractPendingRequest<T> {
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

        final var headers = HEADERS_FACTORY.newEmptyHeaders();
        fillHeaders(result, headers);
        return new DefaultCompletedRequest(HttpResponseStatus.OK, headers);
    }

    abstract Response transformResultImpl(NettyServerRequest<?> request, T result);

    abstract void fillHeaders(T result, HttpHeaders headers);

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("method", withContent ? ImplementedMethod.GET : ImplementedMethod.HEAD);
    }
}
