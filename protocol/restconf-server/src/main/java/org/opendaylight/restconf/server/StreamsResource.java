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
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyRequestResponse;
import org.opendaylight.netconf.transport.http.EventStreamResult;
import org.opendaylight.netconf.transport.http.ExceptionRequestResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to RESTCONF streams.
 */
@NonNullByDefault
final class StreamsResource extends AbstractResource {
    private static final Logger LOG = LoggerFactory.getLogger(StreamsResource.class);
    private static final HttpHeaders STREAM_HEADERS = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
        .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM)
        .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
    private static final EmptyRequestResponse NOT_ACCEPTABLE_GET =
        new EmptyRequestResponse(HttpResponseStatus.NOT_ACCEPTABLE,
            DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM));
    private static final EmptyRequestResponse OK_HEAD = new EmptyRequestResponse(HttpResponseStatus.OK, STREAM_HEADERS);

    StreamsResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final SegmentPeeler peeler, final TransportSession session, final ImplementedMethod method,
            final URI targetUri, final HttpHeaders headers, final @Nullable Principal principal) {
        if (!peeler.hasNext()) {
            return switch (method) {
                case OPTIONS -> OPTIONS_ONLY_OK;
                default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
            };
        }

        final var encodingStr = peeler.next();
        final EncodingName encodingName;
        try {
            encodingName = new EncodingName(encodingStr);
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid encoding '{}'", encodingStr, e);
            return EmptyRequestResponse.NOT_FOUND;
        }

        final var streamName = peeler.remaining();
        if (streamName.isEmpty()) {
            return switch (method) {
                case OPTIONS -> OPTIONS_ONLY_OK;
                default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
            };
        }

        final var stream = invariants.streamRegistry().lookupStream(streamName);
        if (stream == null) {
            return EmptyRequestResponse.NOT_FOUND;
        }

        return switch (method) {
            case GET -> prepareGet(targetUri, headers, stream, encodingName, true);
            case HEAD -> prepareGet(targetUri, headers, stream, encodingName, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private static PreparedRequest prepareGet(final URI targetUri, final HttpHeaders headers,
            final RestconfStream<?> stream, final EncodingName encodingName, final boolean start) {
        final EventStreamGetParams params;
        try {
            params = EventStreamGetParams.of(QueryParameters.ofMultiValue(
                new QueryStringDecoder(targetUri).parameters()));
        } catch (IllegalArgumentException e) {
            return new ExceptionRequestResponse(HttpResponseStatus.BAD_REQUEST, e);
        }

        if (!headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)
            && !headers.contains(HttpHeaderNames.ACCEPT, "*/*", false)) {
            return NOT_ACCEPTABLE_GET;
        }
        if (!start) {
            return OK_HEAD;
        }

        // Try starting stream via registry stream subscriber
        final var sender = new RestconfStream.Sender() {
            @Override
            public void sendDataMessage(final String data) {
                listener.onEventField("data", data);
            }

            @Override
            public void endOfStream() {
                listener.onStreamEnd();
            }
        };

        return new (RestconfEventStreamHandler

        stream.addSubscriber(sender, encodingName, params);




        return new EventStreamResult(null);
    }
}
