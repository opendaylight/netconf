/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;

/**
 * Abstract base class for POST requests on /data resource and its children.
 *
 * @param <T> server response type
 * @param <B> request message body type
 */
@NonNullByDefault
abstract class AbstractPendingDataPost<T extends DataPostResult, B extends ConsumableBody>
        extends PendingRequestWithBody<T, B> {
    AbstractPendingDataPost(final EndpointInvariants invariants, final URI targetUri, final MessageEncoding encoding) {
        super(invariants, targetUri, encoding);
    }

    @Override
    final Response transformResult(final NettyServerRequest<?> request, final T result) {
        return switch (result) {
            case CreateResourceResult createResult -> {
                final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                    .set(HttpHeaderNames.LOCATION, restconfURI() + "data/" + createResult.createdPath());

//                .setMetadataHeaders(createResult)

                yield new CompletedRequest(HttpResponseStatus.CREATED, headers);
            }
            case InvokeResult invokeResult -> {
                final var output = invokeResult.output();
                yield output == null ? CompletedRequest.noContent()
                    : new FormattableDataResponse(output, encoding, request.prettyPrint());
            }
        };
    }
}
