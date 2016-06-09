/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils.streams;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Subscribe to stream util class
 *
 */
public class SubscribeToStreamUtil {

    /**
     * Parse enum from URI
     *
     * @param clazz
     *            - enum type
     * @param value
     *            - string of enum value
     * @return enum
     */
    public static <T> T parseURIEnum(final Class<T> clazz, final String value) {
        if ((value == null) || value.equals("")) {
            return null;
        }
        return StreamUtil.resolveEnum(clazz, value);
    }

    /**
     * Prepare map of values from URI
     *
     * @param identifier
     *            - URI
     * @return {@link Map}
     */
    public static Map<String, String> mapValuesFromUri(final String identifier) {
        final HashMap<String, String> result = new HashMap<>();
        final String[] tokens = identifier.split(String.valueOf(RestconfConstants.SLASH));
        for (final String token : tokens) {
            final String[] paramToken = token.split(String.valueOf(RestconfStreamsConstants.EQUAL));
            if (paramToken.length == 2) {
                result.put(paramToken[0], paramToken[1]);
            }
        }
        return result;
    }

    /**
     * Register data change listener in dom data broker and set it to listener
     * on stream
     *
     * @param ds
     *            - {@link LogicalDatastoreType}
     * @param scope
     *            - {@link DataChangeScope}
     * @param listener
     *            - listener on specific stream
     * @param domDataBroker
     *            - data broker for register data change listener
     */
    public static void registration(final LogicalDatastoreType ds, final DataChangeScope scope,
            final ListenerAdapter listener, final DOMDataBroker domDataBroker) {
        if (listener.isListening()) {
            return;
        }

        final YangInstanceIdentifier path = listener.getPath();
        final ListenerRegistration<DOMDataChangeListener> registration = domDataBroker.registerDataChangeListener(ds,
                path, listener, scope);

        listener.setRegistration(registration);
    }

    /**
     * Get port from web socket server. If doesn't exit, create it.
     *
     * @return port
     */
    public static int prepareNotificationPort() {
        int port = RestconfStreamsConstants.NOTIFICATION_PORT;
        try {
            final WebSocketServer webSocketServer = WebSocketServer.getInstance();
            port = webSocketServer.getPort();
        } catch (final NullPointerException e) {
            WebSocketServer.createInstance(RestconfStreamsConstants.NOTIFICATION_PORT);
        }
        return port;
    }

}
