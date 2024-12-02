/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An {@link AbstractPendingGet} subclass for YANG Data results in {@link FormattableBody} format and nothing else. The
 * only header it produces is {@code Content-Type} based on selected {@link MessageEncoding}.
 */
// FIXME: This name is confusing, can we come up with something better?
//        While we are pondering that possibility, this remains sealed with explicitly named subclasses, so as to
//        prevent accidents.
@NonNullByDefault
abstract sealed class AbstractDataPendingGet extends AbstractPendingGet<FormattableBody>
        permits PendingOperationsGet, PendingYangLibraryVersionGet {
    final MessageEncoding encoding;

    AbstractDataPendingGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final boolean withContent, final MessageEncoding encoding) {
        super(invariants, session, targetUri, principal, withContent);
        this.encoding = requireNonNull(encoding);
    }

    @Override
    final MessageEncoding errorEncoding() {
        return encoding;
    }

    @Override
    final Response transformResultImpl(final NettyServerRequest<?> request, final FormattableBody result) {
        return new FormattableDataResponse(result, encoding, request.prettyPrint());
    }

    @Override
    final List<CharSequence> extractHeaders(final FormattableBody result) {
        return List.of(HttpHeaderNames.CONTENT_TYPE, encoding.dataMediaType());
    }
}
