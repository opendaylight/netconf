/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataStreamService;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.sse.SSEInitializer;
import org.opendaylight.restconf.nb.rfc8040.streams.sse.SSESessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfDataStreamService}.
 */
@Singleton
public class RestconfDataStreamServiceImpl implements RestconfDataStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataStreamServiceImpl.class);

    private final ListenersBroker listenersBroker = ListenersBroker.getInstance();
    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    @Inject
    public RestconfDataStreamServiceImpl(final SSEInitializer configuration) {
        executorService = configuration.getExecutorService();
        heartbeatInterval = configuration.getHeartbeatInterval();
        maximumFragmentLength = configuration.getMaximumFragmentLength();
    }

    @Override
    public EventOutput getSSE(final String identifier, final UriInfo uriInfo) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final Optional<BaseListenerInterface> listener = listenersBroker.getListenerFor(streamName);

        if (listener.isEmpty()) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            throw new RestconfDocumentedException("Data missing", ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        final EventOutput eventOutput = new EventOutput();
        final SSESessionHandler handler = new SSESessionHandler(executorService, eventOutput, listener.get(),
            maximumFragmentLength, heartbeatInterval);
        handler.init();
        return eventOutput;
    }
}
