/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaders;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * An abstract base class for {@link PendingRequest}s servicing both GET and HEAD requests. It handles result
 * transformation so that of HEAD requests we only respond with appropriate headers.
 *
 * <p>
 * We deliberately do not expose {@link PrettyPrintParam} to {@link #fillHeaders(MessageEncoding, Object, HttpHeaders)},
 * so that subclasses are not tempted to attempt to attain the {@code Content-Type} header. While this may seem to be
 * a violation of {@code HEAD} method mechanics, it is in fact taking full advantage of efficiencies outline in second
 * paragraph of <a href="https://www.rfc-editor.org/rfc/rfc9110#name-head">RFC9110, section 9.3.2</a>:
 */
@NonNullByDefault
abstract class AbstractPendingGet<T> extends PendingRequestWithEncoding<T> {
    private final boolean withContent;

    AbstractPendingGet(final EndpointInvariants invariants, final MessageEncoding encoding, final boolean withContent) {
        super(invariants, encoding);
        this.withContent = withContent;
    }

    @Override
    final Response transformResult(final NettyServerRequest<?> request, final T result) {
        if (withContent) {
            return transformResult(request, result);
        }

        final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders();
        fillHeaders(result, headers);
        return new HeadResponse(headers);
    }

    abstract Response transformResultImpl(NettyServerRequest<?> request, T result);

    abstract void fillHeaders(T result, HttpHeaders headers);
}
