/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.api.parser.Parser;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RestconfStreamsHandler implements {@link SseListener}. It handles server sent events from restconf server and
 * transforms them to {@link DOMNotification}. Then, it notifies reqistered {@link RestconfDeviceStreamListener}.
 */
class RestconfStreamsHandler implements SseListener {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsHandler.class);

    private final List<RestconfDeviceStreamListener> listeners = new ArrayList<>();
    private final Parser parser;

    /**
     * @param parser parser for event to {@link DOMNotification} transformation
     */
    public RestconfStreamsHandler(final Parser parser) {
        this.parser = parser;
    }

    /**
     * Registers stream listener. The listener will receive {@link DOMNotification}s parsed from restconf streams.
     *
     * @param listener listener
     */
    public void registerListener(final RestconfDeviceStreamListener listener) {
        listeners.add(listener);
    }

    /**
     * Parses event to {@link DOMNotification} and notifies listeners
     *
     * @param message event
     */
    @Override
    public void onMessage(final String message) {
        //todo multiline messages
        try {
            final DOMNotification notification = parser.parseNotification(message);
            for (final RestconfDeviceStreamListener listener : listeners) {
                notifyListener(listener, notification);
            }
        } catch (final Exception e) {
            LOG.warn("Failed to parse notification");
            LOG.debug("Failed to parse notification", e);
        }
    }

    private static void notifyListener(final RestconfDeviceStreamListener listener, final DOMNotification notification) {
        try {
            listener.onNotification(notification);
        } catch (final Exception e) {
            LOG.error("Notification listener error:", e);
        }
    }
}