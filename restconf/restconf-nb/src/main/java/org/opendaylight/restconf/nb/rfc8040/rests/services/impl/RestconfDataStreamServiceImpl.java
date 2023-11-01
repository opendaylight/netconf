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
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
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
    @Path("/{streamName:.+}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getSSE(@PathParam("streamName") final String streamName, @Context final SseEventSink sink,
            @Context final Sse sse) {
        final var listener = listenersBroker.listenerFor(streamName);
        if (listener == null) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            throw new WebApplicationException("No such stream: " + streamName, Status.NOT_FOUND);
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESessionHandler(executorService, sink, sse, listener, maximumFragmentLength,
            heartbeatInterval);
        handler.init();
    }
}
