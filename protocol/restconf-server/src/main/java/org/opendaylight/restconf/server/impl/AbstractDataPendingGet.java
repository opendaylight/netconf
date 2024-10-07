/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.Response;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * An {@link AbstractPendingGet} subclass for YANG Data results in {@link FormattableBody} format and nothing else. The
 * only header it produces is {@code Content-Type} based on selected {@link MessageEncoding}.
 */
// FIXME: This name is confusing, can we come up with something better?
//        While we are pondering that possibility, this remains sealed with explicitly named subclasses, so as to
//        prevent accidents.
@NonNullByDefault
public abstract class AbstractDataPendingGet extends AbstractPendingGet<FormattableBody> {
    final MessageEncoding encoding;

    protected AbstractDataPendingGet(final EndpointInvariants invariants, final TransportSession session,
            final URI targetUri, final @Nullable Principal principal, final boolean withContent,
            final MessageEncoding encoding) {
        super(invariants, session, targetUri, principal, withContent);
        this.encoding = requireNonNull(encoding);
    }

    @Override
    protected final Response transformResultImpl(final NettyServerRequest<?> request, final FormattableBody result) {
        return new FormattableDataResponse(result, encoding, request.prettyPrint());
    }

    @Override
    protected final void fillHeaders(final FormattableBody result, final HttpHeaders headers) {
        headers.set(HttpHeaderNames.CONTENT_TYPE, encoding.dataMediaType());
    }
}
