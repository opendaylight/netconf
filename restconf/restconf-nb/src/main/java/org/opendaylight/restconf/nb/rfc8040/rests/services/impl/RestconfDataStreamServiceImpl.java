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
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataStreamService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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
    public RestconfDataStreamServiceImpl(final ScheduledThreadPool scheduledThreadPool,
            final Configuration configuration) {
        executorService = scheduledThreadPool.getExecutor();
        heartbeatInterval = configuration.getHeartbeatInterval();
        maximumFragmentLength = configuration.getMaximumFragmentLength();
    }

    @Override
    public void getSSE(final String identifier, final UriInfo uriInfo, final SseEventSink sink, final Sse sse) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final BaseListenerInterface listener;
        final String notificaionType =
            uriInfo.getQueryParameters().getFirst(RestconfStreamsConstants.NOTIFICATION_TYPE);
        if (notificaionType != null && notificaionType.equals(RestconfStreamsConstants.DEVICE)) {
            listener = listenersBroker.getDeviceNotificationListenerFor(streamName)
                .orElseThrow(() -> {
                    LOG.debug("Listener for device path with name {} was not found.", streamName);
                    throw new RestconfDocumentedException("Data missing", ErrorType.APPLICATION,
                        ErrorTag.DATA_MISSING);
                });
        } else {
            listener = listenersBroker.getListenerFor(streamName)
                .orElseThrow(() -> {
                    LOG.debug("Listener for stream with name {} was not found.", streamName);
                    throw new RestconfDocumentedException("Data missing", ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
                });
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final SSESessionHandler handler = new SSESessionHandler(executorService, sink, sse, listener,
            maximumFragmentLength, heartbeatInterval);
        handler.init();
    }
}
