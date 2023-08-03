/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to notification streams via Server-Sent Events.
 */
@Path("/")
@Singleton
public final class RestconfDataStreamServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataStreamServiceImpl.class);

    private final ListenersBroker listenersBroker = ListenersBroker.getInstance();
    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    @Inject
    public RestconfDataStreamServiceImpl(final ScheduledThreadPool scheduledThreadPool,
            final StreamsConfiguration configuration) {
        executorService = scheduledThreadPool.getExecutor();
        heartbeatInterval = configuration.heartbeatInterval();
        maximumFragmentLength = configuration.maximumFragmentLength();
    }

    /**
     * Get target data resource.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     */
    @GET
    @Path("/{identifier:.+}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getSSE(@Encoded @PathParam("identifier") final String identifier, @Context final UriInfo uriInfo,
            @Context final SseEventSink sink, @Context final Sse sse) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final BaseListenerInterface listener;
        final String notificaionType =
            uriInfo.getQueryParameters().getFirst(RestconfStreamsConstants.NOTIFICATION_TYPE);
        if (notificaionType != null && notificaionType.equals(RestconfStreamsConstants.DEVICE)) {
            listener = listenersBroker.deviceNotificationListenerFor(streamName);
            if (listener == null) {
                LOG.debug("Listener for device path with name {} was not found.", streamName);
                throw new RestconfDocumentedException("Data missing", ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
            }
        } else {
            listener = listenersBroker.listenerFor(streamName);
            if (listener == null) {
                LOG.debug("Listener for stream with name {} was not found.", streamName);
                throw new RestconfDocumentedException("Data missing", ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
            }
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final SSESessionHandler handler = new SSESessionHandler(executorService, sink, sse, listener,
            maximumFragmentLength, heartbeatInterval);
        handler.init();
    }
}
