/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import com.google.common.base.Strings;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ResolveEnumUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.AbstractCommonSubscriber;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe to stream util class.
 */
final class SubscribeToStreamUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToStreamUtil.class);

    private SubscribeToStreamUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Register listener by streamName in identifier to listen to yang notifications, and put or delete information
     * about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Name of the stream.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @param streamUrlResolver       Stream URL resolver.
     * @param uriInfo                 Request URI information.
     * @return Stream location for listening.
     */
    @NonNull
    static URI subscribeToYangStream(final String identifier, final NotificationQueryParams notificationQueryParams,
            final HandlersHolder handlersHolder, final StreamUrlResolver streamUrlResolver, final UriInfo uriInfo) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final Optional<NotificationListenerAdapter> notificationListenerAdapter =
                ListenersBroker.getInstance().getNotificationListenerFor(streamName);

        if (notificationListenerAdapter.isEmpty()) {
            throw new RestconfDocumentedException(String.format(
                    "Listener for stream with name %s was not found.", streamName),
                    ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }
        updateListener(notificationQueryParams, handlersHolder, notificationListenerAdapter.get());

        final boolean registered = registerToListenNotification(
                notificationListenerAdapter.get(), handlersHolder.getNotificationServiceHandler());
        if (registered) {
            final EffectiveModelContext schemaContext = handlersHolder.getSchemaHandler().get();
            final String serializedPath = SchemaPathCodec.serialize(
                    notificationListenerAdapter.get().getSchemaPath(), schemaContext);
            writeSubscriptionToDatastore(notificationQueryParams, handlersHolder, schemaContext, serializedPath);
        }
        return streamUrlResolver.prepareUriByStreamName(streamName, uriInfo);
    }

    /**
     * Register listener by streamName in identifier to listen to data change notifications, and put or delete
     * information about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Identifier as stream name.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @param streamUrlResolver       Stream URL resolver.
     * @param uriInfo                 Request URI information.
     * @return Location for listening.
     */
    @NonNull
    static URI subscribeToDataStream(final String identifier, final NotificationQueryParams notificationQueryParams,
            final HandlersHolder handlersHolder, final StreamUrlResolver streamUrlResolver, final UriInfo uriInfo) {
        final Map<String, String> mapOfValues = mapValuesFromUri(identifier);
        final LogicalDatastoreType datastoreType = parseURIEnum(
                LogicalDatastoreType.class,
                mapOfValues.get(RestconfStreamsConstants.DATASTORE_PARAM_NAME));
        if (datastoreType == null) {
            final String message = "Stream name doesn't contain datastore value (pattern /datastore=)";
            LOG.warn(message);
            throw new RestconfDocumentedException(message, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final DataChangeScope scope = parseURIEnum(
                DataChangeScope.class,
                mapOfValues.get(RestconfStreamsConstants.SCOPE_PARAM_NAME));
        if (scope == null) {
            final String message = "Stream name doesn't contains datastore value (pattern /scope=)";
            LOG.warn(message);
            throw new RestconfDocumentedException(message, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final Optional<ListenerAdapter> listener = ListenersBroker.getInstance().getDataChangeListenerFor(streamName);
        if (listener.isEmpty()) {
            throw new RestconfDocumentedException(String.format(
                    "Listener for stream with name %s was not found.", streamName),
                    ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }
        updateListener(notificationQueryParams, handlersHolder, listener.get());

        final boolean registered = registration(datastoreType, listener.get(),
                handlersHolder.getDomDataBrokerHandler().get());
        if (registered) {
            final EffectiveModelContext schemaContext = handlersHolder.getSchemaHandler().get();
            final String serializedPath = IdentifierCodec.serialize(listener.get().getPath(), schemaContext);
            writeSubscriptionToDatastore(notificationQueryParams, handlersHolder, schemaContext, serializedPath);
        }
        return streamUrlResolver.prepareUriByStreamName(streamName, uriInfo);
    }

    private static void updateListener(final NotificationQueryParams notificationQueryParams,
            final HandlersHolder handlersHolder, final AbstractCommonSubscriber listenerAdapter) {
        listenerAdapter.setQueryParams(
                notificationQueryParams.getStart(),
                notificationQueryParams.getStop().orElse(null),
                notificationQueryParams.getFilter().orElse(null),
                false);
        listenerAdapter.setCloseVars(handlersHolder.getTransactionChainHandler(),
                handlersHolder.getSchemaHandler());
    }

    private static void writeSubscriptionToDatastore(final NotificationQueryParams notificationQueryParams,
            final HandlersHolder handlersHolder, final EffectiveModelContext schemaContext,
            final String serializedPath) {
        final MapEntryNode streamNode = RestconfMappingNodeUtil.mapStreamSubscription(
                serializedPath, notificationQueryParams.getStart());
        final DOMTransactionChain transactionChain = handlersHolder.getTransactionChainHandler().get();
        final DOMDataTreeWriteTransaction writeTransaction = transactionChain.newWriteOnlyTransaction();
        writeDataToDS(schemaContext, serializedPath, writeTransaction, streamNode);
        try {
            writeTransaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        } finally {
            transactionChain.close();
        }
    }

    private static void writeDataToDS(final EffectiveModelContext schemaContext, final String streamName,
            final DOMDataTreeWriteTransaction writeTransaction, final MapEntryNode streamNode) {
        final String pathId;
        try {
            pathId = MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY
                    + URLEncoder.encode(streamName, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL,
                IdentifierCodec.deserialize(pathId, schemaContext), streamNode);
    }

    /**
     * Prepare map of URI parameter-values.
     *
     * @param identifier String identification of URI.
     * @return Map od URI parameters and values.
     */
    private static Map<String, String> mapValuesFromUri(final String identifier) {
        final HashMap<String, String> result = new HashMap<>();
        for (final String token : RestconfConstants.SLASH_SPLITTER.split(identifier)) {
            final String[] paramToken = token.split("=");
            if (paramToken.length == 2) {
                result.put(paramToken[0], paramToken[1]);
            }
        }
        return result;
    }

    /**
     * Register data change listener in {@link DOMDataBroker} and set registration to stream listener.
     *
     * @param datastore     {@link LogicalDatastoreType}
     * @param listener      data change event stream listener
     * @param domDataBroker data broker for register data change listener
     * @return {@code true}, if the the listener has been registered
     */
    private static boolean registration(final LogicalDatastoreType datastore, final ListenerAdapter listener,
                                        final DOMDataBroker domDataBroker) {
        if (listener.isListening()) {
            return false;
        }

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        if (changeService == null) {
            throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService");
        }

        final DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(datastore, listener.getPath());
        final ListenerRegistration<ListenerAdapter> registration =
                changeService.registerDataTreeChangeListener(root, listener);
        listener.setRegistration(registration);
        return true;
    }

    /**
     * Register YANG notification listener in {@link NotificationListenerAdapter} and
     * set registration to stream listener.
     *
     * @param listener                   notification stream listener
     * @param notificationServiceHandler notification service handler
     * @return {@code true}, if the the listener has been registered
     */
    private static boolean registerToListenNotification(final NotificationListenerAdapter listener,
                                                        final NotificationServiceHandler notificationServiceHandler) {
        if (listener.isListening()) {
            return false;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration =
                notificationServiceHandler.get().registerNotificationListener(listener, path);
        listener.setRegistration(registration);
        return true;
    }

    /**
     * Parse out enumeration from URI.
     *
     * @param clazz Target enumeration type.
     * @param value String representation of enumeration value.
     * @return Parsed enumeration type.
     */
    private static <T> T parseURIEnum(final Class<T> clazz, final String value) {
        if (value == null || value.equals("")) {
            return null;
        }
        return ResolveEnumUtil.resolveEnum(clazz, value);
    }
}