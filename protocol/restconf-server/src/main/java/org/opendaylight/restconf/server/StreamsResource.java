/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

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
import org.opendaylight.netconf.transport.http.EventStreamResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
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
final class StreamsResource extends AbstractEventStreamResource {
    private static final Logger LOG = LoggerFactory.getLogger(StreamsResource.class);
    private static final AsciiString STREAM_ID = HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text();

    private final Map<Integer, Registration> senders = new HashMap<>();

    StreamsResource(final EndpointInvariants invariants, final RestconfStream.Registry streamRegistry,
            final int sseHeartbeatIntervalMillis, final int sseMaximumFragmentLength) {
        super(invariants, streamRegistry, sseHeartbeatIntervalMillis, sseMaximumFragmentLength);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String firstSegment,
            final SegmentPeeler peeler) {
        final MonitoringEncoding encodingName;
        try {
            encodingName = MonitoringEncoding.of(firstSegment);
        } catch (IllegalArgumentException e) {
            LOG.debug("Stream encoding name '{}' is invalid", firstSegment, e);
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

        return switch (method) {
            case GET -> prepareGet(targetUri, headers, principal, encodingName, stream);
            case HEAD -> EVENT_STREAM_HEAD;
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final MonitoringEncoding encodingName,
            final RestconfStream<?> stream) {
        if (!headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final var decoder = new QueryStringDecoder(targetUri);
        final EventStreamGetParams streamParams;
        try {
            streamParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(decoder.parameters()));
        } catch (IllegalArgumentException e) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final var streamId = headers.getInt(STREAM_ID);
        return streamId != null ? addEventStream(streamId, stream, encodingName, streamParams)
            : switchToEventStream(stream, encodingName, streamParams);
    }

    // HTTP/1 event stream start. This amounts to a 'long GET', i.e. if our subscription attempt is successful, we will
    // not be servicing any other requests.
    private PreparedRequest switchToEventStream(final RestconfStream<?> stream, final MonitoringEncoding encodingName,
            final EventStreamGetParams params) {
        final var sender = new ChannelSender(sseMaximumFragmentLength);
        final var registration = registerSender(stream, encodingName, params, sender);
        if (registration == null) {
            return EmptyResponse.NOT_FOUND;
        }

        sender.enable(registration);
        return new EventStreamResponse(HttpResponseStatus.OK, sender, sseHeartbeatIntervalMillis);
    }

    // HTTP/2 event stream start.
    private PreparedRequest addEventStream(final Integer streamId, final RestconfStream<?> stream,
            final MonitoringEncoding encodingName, final EventStreamGetParams params) {
        final var sender = new StreamSender(streamId);
        final var registration = registerSender(stream, encodingName, params, sender);
        if (registration == null) {
            return EmptyResponse.NOT_FOUND;
        }
        // Attach the
        senders.put(streamId, registration);
        // FIXME: add the sender to our a hashmap so we can respond to it being reset
        return EmptyResponse.OK;
    }

    private static @Nullable Registration registerSender(final RestconfStream<?> stream,
            final MonitoringEncoding encodingName, final EventStreamGetParams params,
            final RestconfStream.Sender sender) {
        final var encoding = encodingName.encoding();
        if (encoding == null) {
            // FIXME: report an error, as as below would
            return null;
        }

        final Registration reg;
        try {
            reg = stream.addSubscriber(sender, encoding, params);
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            // FIXME: report an error
            return null;
        }
        return reg;
    }

    public Boolean exceptionCaught(final Http2Exception.StreamException se) {
        final var sender = senders.remove(se.streamId());
        if (sender != null) {
            sender.close();
            return true;
        }
        return false;
    }
}
