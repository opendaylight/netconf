/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.nb.rfc8040.monitoring.RestconfStateStreams;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe to stream util class.
 */
public abstract class SubscribeToStreamUtil {
    /**
     * Implementation of SubscribeToStreamUtil for Server-sent events.
     */
    private static final class ServerSentEvents extends SubscribeToStreamUtil {
        ServerSentEvents(final ListenersBroker listenersBroker) {
            super(listenersBroker);
        }

        @Override
        public URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
            return uriInfo.getBaseUriBuilder()
                .replacePath(URLConstants.BASE_PATH + '/' + URLConstants.SSE_SUBPATH + '/' + streamName)
                .build();
        }
    }

    /**
     * Implementation of SubscribeToStreamUtil for Web sockets.
     */
    private static final class WebSockets extends SubscribeToStreamUtil {
        WebSockets(final ListenersBroker listenersBroker) {
            super(listenersBroker);
        }

        @Override
        public URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
            final var scheme = switch (uriInfo.getAbsolutePath().getScheme()) {
                // Secured HTTP goes to Secured WebSockets
                case "https" -> "wss";
                // Unsecured HTTP and others go to unsecured WebSockets
                default -> "ws";
            };

            return uriInfo.getBaseUriBuilder()
                .scheme(scheme)
                .replacePath(URLConstants.BASE_PATH + '/' + streamName)
                .build();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToStreamUtil.class);
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    private final @NonNull ListenersBroker listenersBroker;

    private SubscribeToStreamUtil(final ListenersBroker listenersBroker) {
        this.listenersBroker = requireNonNull(listenersBroker);
    }

    public static @NonNull SubscribeToStreamUtil serverSentEvents(final ListenersBroker listenersBroker) {
        return new ServerSentEvents(listenersBroker);
    }

    public static @NonNull SubscribeToStreamUtil webSockets(final ListenersBroker listenersBroker) {
        return new WebSockets(listenersBroker);
    }

    public final @NonNull ListenersBroker listenersBroker() {
        return listenersBroker;
    }

    /**
     * Prepare URL from base name and stream name.
     *
     * @param uriInfo base URL information
     * @param streamName name of stream for create
     * @return final URL
     */
    abstract @NonNull URI prepareUriByStreamName(UriInfo uriInfo, String streamName);

    /**
     * Register listener by streamName in identifier to listen to yang notifications, and put or delete information
     * about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Name of the stream.
     * @param uriInfo                 URI information.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @return Stream location for listening.
     */
    final @NonNull URI subscribeToYangStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        if (isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var notificationListenerAdapter = listenersBroker.notificationListenerFor(streamName);
        if (notificationListenerAdapter == null) {
            throw new RestconfDocumentedException(String.format("Stream with name %s was not found.", streamName),
                ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final URI uri = prepareUriByStreamName(uriInfo, streamName);
        notificationListenerAdapter.setQueryParams(notificationQueryParams);
        notificationListenerAdapter.listen(handlersHolder.getNotificationServiceHandler());
        final DOMDataBroker dataBroker = handlersHolder.getDataBroker();
        notificationListenerAdapter.setCloseVars(dataBroker, handlersHolder.getDatabindProvider());
        final MapEntryNode mapToStreams = RestconfStateStreams.notificationStreamEntry(streamName,
            notificationListenerAdapter.qnames(), notificationListenerAdapter.getStart(),
            notificationListenerAdapter.getOutputType(), uri);

        // FIXME: how does this correlate with the transaction notificationListenerAdapter.close() will do?
        final DOMDataTreeWriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeDataToDS(writeTransaction, mapToStreams);
        submitData(writeTransaction);
        return uri;
    }

    /**
     * Register listener by streamName in identifier to listen to data change notifications, and put or delete
     * information about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Identifier as stream name.
     * @param uriInfo                 Base URI information.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @return Location for listening.
     */
    final URI subscribeToDataStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final var streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final var listener = listenersBroker.dataChangeListenerFor(streamName);
        if (listener == null) {
            throw new RestconfDocumentedException("No listener found for stream " + streamName,
                ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }

        listener.setQueryParams(notificationQueryParams);

        final var dataBroker = handlersHolder.getDataBroker();
        final var schemaHandler = handlersHolder.getDatabindProvider();
        listener.setCloseVars(dataBroker, schemaHandler);
        listener.listen(dataBroker);

        final var uri = prepareUriByStreamName(uriInfo, streamName);
        final var schemaContext = schemaHandler.currentContext().modelContext();
        final var serializedPath = IdentifierCodec.serialize(listener.getPath(), schemaContext);

        final var mapToStreams = RestconfStateStreams.dataChangeStreamEntry(listener.getPath(),
                listener.getStart(), listener.getOutputType(), uri, schemaContext, serializedPath);
        final var writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeDataToDS(writeTransaction, mapToStreams);
        submitData(writeTransaction);
        return uri;
    }

    // FIXME: callers are utter duplicates, refactor them
    private static void writeDataToDS(final DOMDataTreeWriteOperations tx, final MapEntryNode mapToStreams) {
        // FIXME: use put() here
        tx.merge(LogicalDatastoreType.OPERATIONAL, RestconfStateStreams.restconfStateStreamPath(mapToStreams.name()),
            mapToStreams);
    }

    private static void submitData(final DOMDataTreeWriteTransaction readWriteTransaction) {
        try {
            readWriteTransaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }
}
