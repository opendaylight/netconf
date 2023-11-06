/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ScheduledExecutorService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.streams.EventFormatter;
import org.opendaylight.restconf.nb.rfc8040.streams.EventFormatterFactory;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.EncodingName;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to notification streams via Server-Sent Events.
 */
@Path("/")
public final class RestconfDataStreamServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataStreamServiceImpl.class);

    private final ListenersBroker listenersBroker;
    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    public RestconfDataStreamServiceImpl(final ScheduledThreadPool scheduledThreadPool,
            final ListenersBroker listenersBroker, final StreamsConfiguration configuration) {
        executorService = scheduledThreadPool.getExecutor();
        this.listenersBroker = requireNonNull(listenersBroker);
        heartbeatInterval = configuration.heartbeatInterval();
        maximumFragmentLength = configuration.maximumFragmentLength();
    }

    /**
     * Attach to a particular notification stream.
     *
     * @param streamName path to target
     */
    @GET
    @Path("/{encodingName:[a-zA-Z]+}/{streamName:.+}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getSSE(@PathParam("encodingName") final EncodingName encodingName,
            @PathParam("streamName") final String streamName, @Context final UriInfo uriInfo,
            @Context final SseEventSink sink, @Context final Sse sse) {
        final var params = QueryParams.newReceiveEventsParams(uriInfo);
        final var stream = listenersBroker.getStream(streamName);
        if (stream == null) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            throw new WebApplicationException("No such stream: " + streamName, Status.NOT_FOUND);
        }

        final EventFormatterFactory<?> factory;
        try {
            factory = stream.formatterFactory(encodingName);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), e, Status.NOT_FOUND);
        }

        final EventFormatter<?> formatter;
        try {
            formatter = factory.getFormatter(null, streamName);
        } catch (XPathExpressionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }



//        /**
//         * Set query parameters for listener.
//         *
//         * @param params NotificationQueryParams to use.
//         */
//        public final void setQueryParams(final ReceiveEventsParams params) {
//            final var startTime = params.startTime();
//            if (startTime != null) {
//                throw new RestconfDocumentedException("Stream " + name + " does not support replay",
//                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
//            }
//
//            final var leafNodes = params.leafNodesOnly();
//            final var skipData = params.skipNotificationData();
//            final var changedLeafNodes = params.changedLeafNodesOnly();
//            final var childNodes = params.childNodesOnly();
//
//            final var textParams = new TextParameters(
//                leafNodes != null && leafNodes.value(),
//                skipData != null && skipData.value(),
//                changedLeafNodes != null && changedLeafNodes.value(),
//                childNodes != null && childNodes.value());
//
//            final var filter = params.filter();
//            final var filterValue = filter == null ? null : filter.paramValue();
//
//            final EventFormatter<T> newFormatter;
//            if (filterValue != null && !filterValue.isEmpty()) {
//                try {
//                    newFormatter = formatterFactory.getFormatter(textParams, filterValue);
//                } catch (XPathExpressionException e) {
//                    throw new IllegalArgumentException("Failed to get filter", e);
//                }
//            } else {
//                newFormatter = formatterFactory.getFormatter(textParams);
//            }
//
//            // Single assign
//            formatter = newFormatter;
//        }


        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESessionHandler(executorService, sink, sse, stream, maximumFragmentLength,
            heartbeatInterval);
        handler.init();
    }
}
