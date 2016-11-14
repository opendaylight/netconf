/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe to stream util class
 *
 */
public final class SubscribeToStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToStreamUtil.class);

    private SubscribeToStreamUtil() {
        throw new UnsupportedOperationException("Util class");
    }

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
    private static void registration(final LogicalDatastoreType ds, final DataChangeScope scope,
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
    private static int prepareNotificationPort() {
        int port = RestconfStreamsConstants.NOTIFICATION_PORT;
        try {
            final WebSocketServer webSocketServer = WebSocketServer.getInstance();
            port = webSocketServer.getPort();
        } catch (final NullPointerException e) {
            WebSocketServer.createInstance(RestconfStreamsConstants.NOTIFICATION_PORT);
        }
        return port;
    }

    /**
     * Register listeners by streamName in identifier to listen to yang notifications
     *
     * @param identifier
     *            - identifier as stream name
     * @param uriInfo
     *            - for getting base URI information
     * @param start
     *            - start-time query parameter
     * @param stop
     *            - stop-time query parameter
     * @param notifiServiceHandler
     *            - DOMNotificationService handler for register listeners
     * @param filter
     *            - indicate which subset of all possible events are of interest
     * @return location for listening
     */
    public static URI notifStream(final String identifier, final UriInfo uriInfo, final Date start, final Date stop,
            final NotificationServiceHandler notifiServiceHandler, final String filter) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final List<NotificationListenerAdapter> listeners = Notificator.getNotificationListenerFor(streamName);
        if ((listeners == null) || listeners.isEmpty()) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }

        for (final NotificationListenerAdapter listener : listeners) {
            registerToListenNotification(listener, notifiServiceHandler);
            listener.setQueryParams(start, stop, filter);
        }

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        int notificationPort = RestconfStreamsConstants.NOTIFICATION_PORT;
        try {
            final WebSocketServer webSocketServerInstance = WebSocketServer.getInstance();
            notificationPort = webSocketServerInstance.getPort();
        } catch (final NullPointerException e) {
            WebSocketServer.createInstance(RestconfStreamsConstants.NOTIFICATION_PORT);
        }
        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme("ws");
        final URI uriToWebsocketServer = uriToWebsocketServerBuilder.replacePath(streamName).build();

        return uriToWebsocketServer;
    }

    private static void registerToListenNotification(final NotificationListenerAdapter listener,
            final NotificationServiceHandler notificationServiceHandler) {
        if (listener.isListening()) {
            return;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration =
                notificationServiceHandler.get().registerNotificationListener(listener, path);

        listener.setRegistration(registration);
    }

    /**
     * Prepare InstanceIdentifierContext for Location leaf
     *
     * @param schemaHandler
     *            - schemaContext handler
     * @return InstanceIdentifier of Location leaf
     */
    public static InstanceIdentifierContext<?> prepareIIDSubsStreamOutput(final SchemaContextHandler schemaHandler) {
        final QName qnameBase = QName.create("subscribe:to:notification", "2016-10-28", "notifi");
        final DataSchemaNode location = ((ContainerSchemaNode) schemaHandler.get()
                .findModuleByNamespaceAndRevision(qnameBase.getNamespace(), qnameBase.getRevision())
                .getDataChildByName(qnameBase)).getDataChildByName(QName.create(qnameBase, "location"));
        final List<PathArgument> path = new ArrayList<>();
        path.add(NodeIdentifier.create(qnameBase));
        path.add(NodeIdentifier.create(QName.create(qnameBase, "location")));

        return new InstanceIdentifierContext<SchemaNode>(YangInstanceIdentifier.create(path), location, null,
                schemaHandler.get());
    }

    /**
     * Register listener by streamName in identifier to listen to yang notifications
     *
     * @param identifier
     *            - identifier as stream name
     * @param uriInfo
     *            - for getting base URI information
     * @param start
     *            - start-time query parameter
     * @param stop
     *            - stop-time query parameter
     * @param domDataBrokerHandler
     *            - DOMDataBroker handler for register listener
     * @param filter
     *            - indicate which subset of all possible events are of interest
     * @return location for listening
     */
    public static URI dataSubs(final String identifier, final UriInfo uriInfo, final Date start, final Date stop,
            final DOMDataBrokerHandler domDataBrokerHandler, final String filter) {
        final Map<String, String> mapOfValues = SubscribeToStreamUtil.mapValuesFromUri(identifier);

        final LogicalDatastoreType ds = SubscribeToStreamUtil.parseURIEnum(LogicalDatastoreType.class,
                mapOfValues.get(RestconfStreamsConstants.DATASTORE_PARAM_NAME));
        if (ds == null) {
            final String msg = "Stream name doesn't contains datastore value (pattern /datastore=)";
            LOG.debug(msg);
            throw new RestconfDocumentedException(msg, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final DataChangeScope scope = SubscribeToStreamUtil.parseURIEnum(DataChangeScope.class,
                mapOfValues.get(RestconfStreamsConstants.SCOPE_PARAM_NAME));
        if (scope == null) {
            final String msg = "Stream name doesn't contains datastore value (pattern /scope=)";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final String streamName = Notificator.createStreamNameFromUri(identifier);

        final ListenerAdapter listener = Notificator.getListenerFor(streamName);
        Preconditions.checkNotNull(listener, "Listener doesn't exist : " + streamName);

        listener.setQueryParams(start, stop, filter);

        SubscribeToStreamUtil.registration(ds, scope, listener, domDataBrokerHandler.get());

        final int port = SubscribeToStreamUtil.prepareNotificationPort();

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        final UriBuilder uriToWebSocketServer =
                uriBuilder.port(port).scheme(RestconfStreamsConstants.SCHEMA_SUBSCIBRE_URI);
        return uriToWebSocketServer.replacePath(streamName).build();
    }

    public static Date parseDateFromQueryParam(final Entry<String, List<String>> entry) {
        final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
        String numOf_ms = "";
        final String value = event.getValue();
        if (value.contains(".")) {
            numOf_ms = numOf_ms + ".";
            final int lastChar = value.contains("Z") ? value.indexOf("Z") : (value.contains("+") ? value.indexOf("+")
                    : (value.contains("-") ? value.indexOf("-") : value.length()));
            for (int i = 0; i < (lastChar - value.indexOf(".") - 1); i++) {
                numOf_ms = numOf_ms + "S";
            }
        }
        String zone = "";
        if (!value.contains("Z")) {
            zone = zone + "XXX";
        }
        final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss" + numOf_ms + zone);

        try {
            return dateFormatter.parse(value.contains("Z") ? value.replace('T', ' ').substring(0, value.indexOf("Z"))
                    : value.replace('T', ' '));
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + value + e);
        }
    }
}
