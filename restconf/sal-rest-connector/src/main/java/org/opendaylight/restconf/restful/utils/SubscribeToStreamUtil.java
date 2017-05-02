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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.restconf.restful.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.restful.services.impl.RestconfStreamsSubscriptionServiceImpl.NotificationQueryParams;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe to stream util class.
 *
 */
public final class SubscribeToStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToStreamUtil.class);
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HH:MM", "Z").toFormatter();

    private SubscribeToStreamUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Register listeners by streamName in identifier to listen to yang
     * notifications, put or delete info about listener to DS according to
     * ietf-restconf-monitoring.
     *
     * @param identifier
     *             identifier as stream name
     * @param uriInfo
     *             for getting base URI information
     * @param notificationQueryParams
     *             query parameters of notification
     * @param handlersHolder
     *             holder of handlers for notifications
     * @return location for listening
     */
    @SuppressWarnings("rawtypes")
    public static URI notifYangStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        List<NotificationListenerAdapter> listeners = Notificator.getNotificationListenerFor(streamName);
        if (identifier.contains(RestconfConstants.SLASH + NotificationOutputType.JSON.getName())) {
            listeners = pickSpecificListenerByOutput(listeners, NotificationOutputType.JSON.getName());
        } else {
            listeners = pickSpecificListenerByOutput(listeners, NotificationOutputType.XML.getName());
        }
        if ((listeners == null) || listeners.isEmpty()) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }

        final DOMDataReadWriteTransaction wTx =
                handlersHolder.getTransactionChainHandler().get().newReadWriteTransaction();
        final SchemaContext schemaContext = handlersHolder.getSchemaHandler().get();
        final boolean exist = checkExist(schemaContext, wTx);

        final URI uri = prepareUriByStreamName(uriInfo, streamName);
        for (final NotificationListenerAdapter listener : listeners) {
            registerToListenNotification(listener, handlersHolder.getNotificationServiceHandler());
            listener.setQueryParams(notificationQueryParams.getStart(), notificationQueryParams.getStop(),
                    notificationQueryParams.getFilter(), false);
            listener.setCloseVars(handlersHolder.getTransactionChainHandler(), handlersHolder.getSchemaHandler());
            final NormalizedNode mapToStreams = RestconfMappingNodeUtil
                    .mapYangNotificationStreamByIetfRestconfMonitoring(listener.getSchemaPath().getLastComponent(),
                            schemaContext.getNotifications(), notificationQueryParams.getStart(),
                            listener.getOutputType(), uri, getMonitoringModule(schemaContext), exist);
            writeDataToDS(schemaContext, listener.getSchemaPath().getLastComponent().getLocalName(), wTx, exist,
                    mapToStreams);
        }
        submitData(wTx);

        return uri;
    }

    static List<NotificationListenerAdapter>
            pickSpecificListenerByOutput(final List<NotificationListenerAdapter> listeners, final String outputType) {
        for (final NotificationListenerAdapter notificationListenerAdapter : listeners) {
            if (notificationListenerAdapter.getOutputType().equals(outputType)) {
                final List<NotificationListenerAdapter> list = new ArrayList<>();
                list.add(notificationListenerAdapter);
                return list;
            }
        }
        return listeners;
    }

    /**
     * Prepare InstanceIdentifierContext for Location leaf.
     *
     * @param schemaHandler
     *             schemaContext handler
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
     * Register listener by streamName in identifier to listen to data change
     * notifications, put or delete info about listener to DS according to
     * ietf-restconf-monitoring.
     *
     * @param identifier
     *             identifier as stream name
     * @param uriInfo
     *             for getting base URI information
     * @param notificationQueryParams
     *             query parameters of notification
     * @param handlersHolder
     *             holder of handlers for notifications
     * @return location for listening
     */
    @SuppressWarnings("rawtypes")
    public static URI notifiDataStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
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

        listener.setQueryParams(notificationQueryParams.getStart(), notificationQueryParams.getStop(),
                notificationQueryParams.getFilter(), false);
        listener.setCloseVars(handlersHolder.getTransactionChainHandler(), handlersHolder.getSchemaHandler());

        registration(ds, scope, listener, handlersHolder.getDomDataBrokerHandler().get());

        final URI uri = prepareUriByStreamName(uriInfo, streamName);

        final DOMDataReadWriteTransaction wTx =
                handlersHolder.getTransactionChainHandler().get().newReadWriteTransaction();
        final SchemaContext schemaContext = handlersHolder.getSchemaHandler().get();
        final boolean exist = checkExist(schemaContext, wTx);

        final NormalizedNode mapToStreams = RestconfMappingNodeUtil
                .mapDataChangeNotificationStreamByIetfRestconfMonitoring(listener.getPath(),
                        notificationQueryParams.getStart(), listener.getOutputType(), uri,
                        getMonitoringModule(schemaContext), exist, schemaContext);
        writeDataToDS(schemaContext, listener.getPath().getLastPathArgument().getNodeType().getLocalName(), wTx, exist,
                mapToStreams);
        submitData(wTx);
        return uri;
    }

    public static Module getMonitoringModule(final SchemaContext schemaContext) {
        final Module monitoringModule =
                schemaContext.findModuleByNamespaceAndRevision(MonitoringModule.URI_MODULE, MonitoringModule.DATE);
        return monitoringModule;
    }

    /**
     * Parse input of query parameters - start-time or stop-time - from
     * {@link DateAndTime} format to {@link Instant} format.
     *
     * @param entry
     *             start-time or stop-time as string in {@link DateAndTime}
     *            format
     * @return parsed {@link Instant} by entry
     */
    public static Instant parseDateFromQueryParam(final Entry<String, List<String>> entry) {
        final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
        final String value = event.getValue();
        final TemporalAccessor p;
        try {
            p = FORMATTER.parse(value);
        } catch (DateTimeParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + value, e);
        }
        return Instant.from(p);

    }

    @SuppressWarnings("rawtypes")
    static void writeDataToDS(final SchemaContext schemaContext,
                              final String name, final DOMDataReadWriteTransaction readWriteTransaction,
                              final boolean exist, final NormalizedNode mapToStreams) {
        String pathId = "";
        if (exist) {
            pathId = MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY + name;
        } else {
            pathId = MonitoringModule.PATH_TO_STREAMS;
        }
        readWriteTransaction.merge(LogicalDatastoreType.OPERATIONAL, IdentifierCodec.deserialize(pathId, schemaContext),
                mapToStreams);
    }

    static void submitData(final DOMDataReadWriteTransaction readWriteTransaction) {
        try {
            readWriteTransaction.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    /**
     * Prepare map of values from URI.
     *
     * @param identifier
     *             URI
     * @return {@link Map}
     */
    public static Map<String, String> mapValuesFromUri(final String identifier) {
        final HashMap<String, String> result = new HashMap<>();
        for (final String token : RestconfConstants.SLASH_SPLITTER.split(identifier)) {
            final String[] paramToken = token.split(String.valueOf(RestconfStreamsConstants.EQUAL));
            if (paramToken.length == 2) {
                result.put(paramToken[0], paramToken[1]);
            }
        }
        return result;
    }

    static URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
        final int port = SubscribeToStreamUtil.prepareNotificationPort();

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        final UriBuilder uriToWebSocketServer =
                uriBuilder.port(port).scheme(RestconfStreamsConstants.SCHEMA_SUBSCIBRE_URI);
        final URI uri = uriToWebSocketServer.replacePath(streamName).build();
        return uri;
    }

    /**
     * Register data change listener in dom data broker and set it to listener
     * on stream.
     *
     * @param ds
     *             {@link LogicalDatastoreType}
     * @param scope
     *             {@link DataChangeScope}
     * @param listener
     *             listener on specific stream
     * @param domDataBroker
     *             data broker for register data change listener
     */
    @SuppressWarnings("deprecation")
    private static void registration(final LogicalDatastoreType ds, final DataChangeScope scope,
            final ListenerAdapter listener, final DOMDataBroker domDataBroker) {
        if (listener.isListening()) {
            return;
        }

        final YangInstanceIdentifier path = listener.getPath();
        final ListenerRegistration<DOMDataChangeListener> registration =
                domDataBroker.registerDataChangeListener(ds, path, listener, scope);

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

    static boolean checkExist(final SchemaContext schemaContext,
                              final DOMDataReadWriteTransaction readWriteTransaction) {
        boolean exist;
        try {
            exist = readWriteTransaction.exists(LogicalDatastoreType.OPERATIONAL,
                    IdentifierCodec.deserialize(MonitoringModule.PATH_TO_STREAMS, schemaContext)).checkedGet();
        } catch (final ReadFailedException e1) {
            throw new RestconfDocumentedException("Problem while checking data if exists", e1);
        }
        return exist;
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
     * Parse enum from URI.
     *
     * @param clazz
     *             enum type
     * @param value
     *             string of enum value
     * @return enum
     */
    private static <T> T parseURIEnum(final Class<T> clazz, final String value) {
        if ((value == null) || value.equals("")) {
            return null;
        }
        return ResolveEnumUtil.resolveEnum(clazz, value);
    }

}
