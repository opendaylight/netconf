/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to RFC8040 streams.
 *
 * <p>This class is created and initialized as part of Netty based RESTCONF northbound.
 */
@NonNullByDefault
final class StreamsResource extends AbstractLeafResource {
    private static final Logger LOG = LoggerFactory.getLogger(StreamsResource.class);
    private static final AsciiString STREAM_ID = HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text();

    private final RestconfStream.Registry streamRegistry;
    private final Map<Integer, Registration> senders = new HashMap<>();

    StreamsResource(final EndpointInvariants invariants, final RestconfStream.Registry streamRegistry) {
        super(invariants);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareGet(session, targetUri, headers, principal, path, true);
            case HEAD -> prepareGet(session, targetUri, headers, principal, path, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean startStream) {
        if (! startStream) {
            return HeadersResponse.of(HttpResponseStatus.OK, HttpHeaderNames.CONTENT_TYPE,
                HttpHeaderValues.TEXT_EVENT_STREAM);
        }
        final var peeler = new SegmentPeeler(targetUri);

        if (!peeler.hasNext()) {
            return EmptyResponse.NOT_FOUND;
        }
        final var encodingName = peeler.next();
        final RestconfStream.EncodingName encoding;
        try {
            encoding = new RestconfStream.EncodingName(encodingName);
        } catch (IllegalArgumentException e) {
            LOG.debug("Stream encoding name '{}' is invalid", encodingName, e);
            return EmptyResponse.NOT_FOUND;
        }

        if (!peeler.hasNext()) {
            return EmptyResponse.NOT_FOUND;
        }
        final var streamName = peeler.next();
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Stream '{}' not found", streamName);
            return EmptyResponse.NOT_FOUND;
        }

        if (headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final var decoder = new QueryStringDecoder(targetUri);
        final EventStreamGetParams streamParams;
        try {
            streamParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(decoder.parameters()));
        } catch (IllegalArgumentException e) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        Integer streamId = headers.getInt(STREAM_ID);
        if (streamId != null) {
            return addEventStream(streamId, stream, encoding, streamParams);
        } else {
            return switchToEventStream(handler, stream, encoding, streamParams);
        }

        return null;
    }

    // HTTP/1 event stream start. This amounts to a 'long GET', i.e. if our subscription attempt is successful, we will
    // not be servicing any other requests.
    private PreparedRequest switchToEventStream(final ChannelHandler handler, final RestconfStream<?> stream,
        final RestconfStream.EncodingName encoding, final EventStreamGetParams params) {
        final var sender = new ChannelSender();
        final Registration registration = registerSender(stream, encoding, params, sender);

        if (registration == null) {
            return EmptyResponse.NOT_FOUND;
        }

        // Replace ourselves with the sender and enable it wil the registration
        sender.getCtx().channel().pipeline().replace(handler, null, sender);
        sender.enable(registration);
        return EmptyResponse.OK;
    }

    // HTTP/2 event stream start.
    private PreparedRequest addEventStream(final Integer streamId, final RestconfStream<?> stream,
        final RestconfStream.EncodingName encoding, final EventStreamGetParams params) {
        final var sender = new StreamSender(streamId);
        final var registration = registerSender(stream, encoding, params, sender);
        if (registration == null) {
            return EmptyResponse.NOT_FOUND;
        }
        // Attach the
        senders.put(streamId, registration);
        // FIXME: add the sender to our a hashmap so we can respond to it being reset
        return EmptyResponse.OK;
    }

    private static @Nullable Registration registerSender(final RestconfStream<?> stream, final RestconfStream.EncodingName encoding,
        final EventStreamGetParams params, final RestconfStream.Sender sender) {
        final Registration reg;
        try {
            reg = stream.addSubscriber(sender, encoding, params);
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            // FIXME: report an error
            return null;
        }
        return reg;
    }
}
