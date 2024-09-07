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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResource;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.yangtools.concepts.AbstractObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The root of resource hierarchy exposed from a particular endpoint.
 */
final class EndpointRoot {
    // split out to minimized retained fields
    private static final class ResourceReg extends AbstractObjectRegistration<WebHostResourceInstance> {
        private final ConcurrentHashMap<String, WebHostResource> map;

        @NonNullByDefault
        ResourceReg(final WebHostResourceInstance instance, final ConcurrentHashMap<String, WebHostResource> map) {
            super(instance);
            this.map = requireNonNull(map);
        }

        @Override
        protected void removeRegistration() {
            final var res = getInstance();
            final var path = res.path();
            if (map.remove(path, res)) {
                LOG.info("unregistered {} -> {}", path, res);
            } else {
                LOG.warn("unregister non existing {} -> {}, weird but harmless?", path, res);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(EndpointRoot.class);
    private static final AsciiString STREAM_ID = HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text();

    private final ConcurrentHashMap<String, WebHostResource> resources = new ConcurrentHashMap<>();
    private final PrincipalService principalService;
    // FIXME: at some point this should just be 'XRD xrd'
    private final WellKnownResources wellKnown;
    // FIXME: at some point these two fields should be integrated into 'providers' Map with a coherent resource access
    //        API across the three classes of resources we have today
    private final APIResource apiResource;
    private final String apiSegment;
    private final Map<Integer, Registration> senders = new HashMap<>();
    private final RestconfStream.Registry streamRegistry;

    @NonNullByDefault
    EndpointRoot(final PrincipalService principalService, final WellKnownResources wellKnown,
            final String apiSegment, final APIResource apiResource, final RestconfStream.Registry streamRegistry) {
        this.principalService = requireNonNull(principalService);
        this.wellKnown = requireNonNull(wellKnown);
        this.apiSegment = requireNonNull(apiSegment);
        this.apiResource = requireNonNull(apiResource);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @NonNullByDefault
    Registration registerProvider(final WebHostResourceProvider provider) {
        for (var path = provider.defaultPath(); ; path = provider.defaultPath() + "-" + UUID.randomUUID().toString()) {
            @SuppressWarnings("resource")
            final var resource = provider.createInstance(path);
            final var prev = resources.putIfAbsent(path, resource);
            if (prev == null) {
                LOG.info("registered {} -> {}", path, resource);
                return new ResourceReg(resource, resources);
            }

            LOG.warn("{} -> {} conficts with registered {}, retrying mapping", path, resource, prev);
            resource.close();
        }
    }

    @NonNullByDefault
    PreparedRequest prepare(final ChannelHandler channelHandler, final TransportSession session,
            final ImplementedMethod method, final URI targetUri, final HttpHeaders headers) {
        final var peeler = new SegmentPeeler(targetUri);
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? AbstractResource.OPTIONS_ONLY_OK
                : AbstractResource.OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        }

        final var segment = peeler.next();
        if (segment.equals(".well-known")) {
            return wellKnown.request(peeler, method, headers);
        } else if (segment.equals("streams")) {
            return streamsRequest(peeler, channelHandler, method, headers, new QueryStringDecoder(targetUri));
        } else if (segment.equals(apiSegment)) {
            return apiResource.prepare(peeler, session, method, targetUri, headers,
                principalService.acquirePrincipal(headers));
        }

        final var resource = resources.get(segment);
        return resource == null ? EmptyResponse.NOT_FOUND
            : resource.prepare(method, targetUri, headers, peeler, wellKnown);
    }

    private PreparedRequest streamsRequest(final SegmentPeeler peeler, final ChannelHandler handler,
            final ImplementedMethod method, final HttpHeaders headers, final QueryStringDecoder decoder) {
        if (!peeler.hasNext()) {
            return EmptyResponse.NOT_FOUND;
        }
        final var encodingName = peeler.next();
        final EncodingName encoding;
        try {
            encoding = new EncodingName(encodingName);
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
        if (!method.equals(ImplementedMethod.GET)) {
            return new EmptyResponse(HttpResponseStatus.NOT_IMPLEMENTED);
        }

        if (headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

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

    }

    // HTTP/1 event stream start. This amounts to a 'long GET', i.e. if our subscription attempt is successful, we will
    // not be servicing any other requests.
    private PreparedRequest switchToEventStream(final ChannelHandler handler, final RestconfStream<?> stream,
            final EncodingName encoding, final EventStreamGetParams params) {
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
            final EncodingName encoding, final EventStreamGetParams params) {
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

    private static @Nullable Registration registerSender(final RestconfStream<?> stream, final EncodingName encoding,
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

    public Boolean exceptionCaught(Http2Exception.StreamException se) {
        final var sender = senders.remove(se.streamId());
        if (sender != null) {
            sender.close();
            return true;
        }
        return false;
    }
}
