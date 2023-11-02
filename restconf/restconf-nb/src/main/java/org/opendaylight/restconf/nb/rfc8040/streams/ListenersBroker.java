/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.StampedLock;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Scope;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link ListenerAdapter} or
 * {@link NotificationListenerAdapter} listeners.
 */
// FIXME: furthermore, this should be tied to ietf-restconf-monitoring, as the Strings used in its maps are stream
//        names. We essentially need a component which deals with allocation of stream names and their lifecycle and
//        the contents of /restconf-state/streams.
public abstract sealed class ListenersBroker {
    /**
     * A ListenersBroker working with Server-Sent Events.
     */
    public static final class ServerSentEvents extends ListenersBroker {
        @Override
        public URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
            return uriInfo.getBaseUriBuilder()
                .replacePath(URLConstants.BASE_PATH + '/' + URLConstants.STREAMS_SUBPATH + '/' + streamName)
                .build();
        }
    }

    /**
     * A ListenersBroker working with WebSockets.
     */
    public static final class WebSockets extends ListenersBroker {
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
                .replacePath(URLConstants.BASE_PATH + '/' + URLConstants.STREAMS_SUBPATH + '/' + streamName)
                .build();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ListenersBroker.class);

    private final StampedLock dataChangeListenersLock = new StampedLock();
    private final StampedLock notificationListenersLock = new StampedLock();
    private final StampedLock deviceNotificationListenersLock = new StampedLock();
    private final BiMap<String, ListenerAdapter> dataChangeListeners = HashBiMap.create();
    private final BiMap<String, NotificationListenerAdapter> notificationListeners = HashBiMap.create();
    private final BiMap<String, DeviceNotificationListenerAdaptor> deviceNotificationListeners = HashBiMap.create();

    private ListenersBroker() {
        // Hidden on purpose
    }

    /**
     * Gets {@link ListenerAdapter} specified by stream identification.
     *
     * @param streamName Stream name.
     * @return {@link ListenerAdapter} specified by stream name or {@code null} if listener with specified stream name
     *         does not exist.
     * @throws NullPointerException in {@code streamName} is {@code null}
     */
    public final @Nullable ListenerAdapter dataChangeListenerFor(final String streamName) {
        requireNonNull(streamName);

        final long stamp = dataChangeListenersLock.readLock();
        try {
            return dataChangeListeners.get(streamName);
        } finally {
            dataChangeListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Gets {@link NotificationListenerAdapter} specified by stream name.
     *
     * @param streamName Stream name.
     * @return {@link NotificationListenerAdapter} specified by stream name or {@code null} if listener with specified
     *         stream name does not exist.
     * @throws NullPointerException in {@code streamName} is {@code null}
     */
    public final @Nullable NotificationListenerAdapter notificationListenerFor(final String streamName) {
        requireNonNull(streamName);

        final long stamp = notificationListenersLock.readLock();
        try {
            return notificationListeners.get(streamName);
        } finally {
            notificationListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Get listener for device path.
     *
     * @param streamName name.
     * @return {@link DeviceNotificationListenerAdaptor} specified by stream name or {@code null} if listener with
     *         specified stream name does not exist.
     * @throws NullPointerException in {@code path} is {@code null}
     */
    public final @Nullable DeviceNotificationListenerAdaptor deviceNotificationListenerFor(final String streamName) {
        requireNonNull(streamName);

        final long stamp = deviceNotificationListenersLock.readLock();
        try {
            return deviceNotificationListeners.get(streamName);
        } finally {
            deviceNotificationListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Get listener for stream-name.
     *
     * @param streamName Stream name.
     * @return {@link NotificationListenerAdapter} or {@link ListenerAdapter} object wrapped in {@link Optional}
     *     or {@link Optional#empty()} if listener with specified stream name doesn't exist.
     */
    public final @Nullable BaseListenerInterface listenerFor(final String streamName) {
        if (streamName.startsWith(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            return notificationListenerFor(streamName);
        } else if (streamName.startsWith(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            return dataChangeListenerFor(streamName);
        } else if (streamName.startsWith(RestconfStreamsConstants.DEVICE_NOTIFICATION_STREAM)) {
            return deviceNotificationListenerFor(streamName);
        } else {
            return null;
        }
    }

    /**
     * Creates new {@link ListenerAdapter} listener using input stream name and path if such listener
     * hasn't been created yet.
     *
     * @param path       Path to data in data repository.
     * @param outputType Specific type of output for notifications - XML or JSON.
     * @return Created or existing data-change listener adapter.
     */
    public final ListenerAdapter registerDataChangeListener(final EffectiveModelContext modelContext,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final Scope scope,
            final NotificationOutputType outputType) {
        final var sb = new StringBuilder(RestconfStreamsConstants.DATA_SUBSCRIPTION)
            .append('/').append(createStreamNameFromUri(IdentifierCodec.serialize(path, modelContext)))
            .append('/').append(RestconfStreamsConstants.DATASTORE_PARAM_NAME).append('=').append(datastore)
            .append('/').append(RestconfStreamsConstants.SCOPE_PARAM_NAME).append('=').append(scope);
        if (outputType != NotificationOutputType.XML) {
            sb.append('/').append(outputType.getName());
        }

        final long stamp = dataChangeListenersLock.writeLock();
        try {
            return dataChangeListeners.computeIfAbsent(sb.toString(),
                streamName -> new ListenerAdapter(datastore, path, streamName, outputType, this));
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Creates new {@link NotificationDefinition} listener using input stream name and schema path
     * if such listener haven't been created yet.
     *
     * @param refSchemaCtx reference {@link EffectiveModelContext}
     * @param notifications {@link QName}s of accepted YANG notifications
     * @param outputType Specific type of output for notifications - XML or JSON.
     * @return Created or existing notification listener adapter.
     */
    public final NotificationListenerAdapter registerNotificationListener(final EffectiveModelContext refSchemaCtx,
            final ImmutableSet<QName> notifications, final NotificationOutputType outputType) {
        final var sb = new StringBuilder(RestconfStreamsConstants.NOTIFICATION_STREAM).append('/');
        var haveFirst = false;
        for (var qname : notifications) {
            final var module = refSchemaCtx.findModuleStatement(qname.getModule())
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an unknown module",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            final var stmt = module.findSchemaTreeNode(qname)
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            if (!(stmt instanceof NotificationEffectiveStatement)) {
                throw new RestconfDocumentedException(qname + " refers to a non-notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
            }

            if (haveFirst) {
                sb.append(',');
            } else {
                haveFirst = true;
            }
            sb.append(module.argument().getLocalName()).append(':').append(qname.getLocalName());
        }
        if (outputType != NotificationOutputType.XML) {
            sb.append('/').append(outputType.getName());
        }

        final long stamp = notificationListenersLock.writeLock();
        try {
            return notificationListeners.computeIfAbsent(sb.toString(),
                streamName -> new NotificationListenerAdapter(notifications, streamName, outputType, this));
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Creates new {@link DeviceNotificationListenerAdaptor} listener using input stream name and schema path
     * if such listener haven't been created yet.
     *
     * @param deviceName Device name.
     * @param outputType Specific type of output for notifications - XML or JSON.
     * @param refSchemaCtx Schema context of node
     * @param mountPointService Mount point service
     * @return Created or existing device notification listener adapter.
     */
    public final DeviceNotificationListenerAdaptor registerDeviceNotificationListener(final String deviceName,
            final NotificationOutputType outputType, final EffectiveModelContext refSchemaCtx,
            final DOMMountPointService mountPointService, final YangInstanceIdentifier path) {
        final var sb = new StringBuilder(RestconfStreamsConstants.DEVICE_NOTIFICATION_STREAM).append('/')
            .append(deviceName);

        final long stamp = deviceNotificationListenersLock.writeLock();
        try {
            return deviceNotificationListeners.computeIfAbsent(sb.toString(),
                streamName -> new DeviceNotificationListenerAdaptor(streamName, outputType, refSchemaCtx,
                    mountPointService, path, this));
        } finally {
            deviceNotificationListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Removal and closing of all data-change-event and notification listeners.
     */
    public final synchronized void removeAndCloseAllListeners() {
        final long stampNotifications = notificationListenersLock.writeLock();
        final long stampDataChanges = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseAllDataChangeListenersTemplate();
            removeAndCloseAllNotificationListenersTemplate();
        } finally {
            dataChangeListenersLock.unlockWrite(stampDataChanges);
            notificationListenersLock.unlockWrite(stampNotifications);
        }
    }

    /**
     * Closes and removes all data-change listeners.
     */
    public final void removeAndCloseAllDataChangeListeners() {
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseAllDataChangeListenersTemplate();
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeAndCloseAllDataChangeListenersTemplate() {
        dataChangeListeners.values().forEach(listenerAdapter -> {
            try {
                listenerAdapter.close();
            } catch (Exception e) {
                LOG.error("Failed to close data-change listener {}.", listenerAdapter, e);
                throw new IllegalStateException("Failed to close data-change listener %s.".formatted(listenerAdapter),
                    e);
            }
        });
        dataChangeListeners.clear();
    }

    /**
     * Closes and removes all notification listeners.
     */
    public final void removeAndCloseAllNotificationListeners() {
        final long stamp = notificationListenersLock.writeLock();
        try {
            removeAndCloseAllNotificationListenersTemplate();
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeAndCloseAllNotificationListenersTemplate() {
        notificationListeners.values().forEach(listenerAdapter -> {
            try {
                listenerAdapter.close();
            } catch (Exception e) {
                LOG.error("Failed to close notification listener {}.", listenerAdapter, e);
                throw new IllegalStateException("Failed to close notification listener %s.".formatted(listenerAdapter),
                    e);
            }
        });
        notificationListeners.clear();
    }

    /**
     * Removes and closes data-change listener of type {@link ListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void removeAndCloseDataChangeListener(final ListenerAdapter listener) {
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseDataChangeListenerTemplate(listener);
        } catch (Exception exception) {
            LOG.error("Data-change listener {} cannot be closed.", listener, exception);
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Removes and closes data-change listener of type {@link ListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    private void removeAndCloseDataChangeListenerTemplate(final ListenerAdapter listener) {
        try {
            requireNonNull(listener).close();
            if (dataChangeListeners.inverse().remove(listener) == null) {
                LOG.warn("There isn't any data-change event stream that would match listener adapter {}.", listener);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Data-change listener {} cannot be closed.", listener, e);
            throw new IllegalStateException("Data-change listener %s cannot be closed.".formatted(listener), e);
        }
    }

    /**
     * Removes and closes notification listener of type {@link NotificationListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void removeAndCloseNotificationListener(final NotificationListenerAdapter listener) {
        final long stamp = notificationListenersLock.writeLock();
        try {
            removeAndCloseNotificationListenerTemplate(listener);
        } catch (Exception e) {
            LOG.error("Notification listener {} cannot be closed.", listener, e);
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Removes and closes device notification listener of type {@link NotificationListenerAdapter}
     * specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void removeAndCloseDeviceNotificationListener(final DeviceNotificationListenerAdaptor listener) {
        final long stamp = deviceNotificationListenersLock.writeLock();
        try {
            requireNonNull(listener);
            if (deviceNotificationListeners.inverse().remove(listener) == null) {
                LOG.warn("There isn't any device notification stream that would match listener adapter {}.", listener);
            }
        } catch (final Exception exception) {
            LOG.error("Device Notification listener {} cannot be closed.", listener, exception);
        } finally {
            deviceNotificationListenersLock.unlockWrite(stamp);
        }
    }

    private void removeAndCloseNotificationListenerTemplate(final NotificationListenerAdapter listener) {
        try {
            requireNonNull(listener).close();
            if (notificationListeners.inverse().remove(listener) == null) {
                LOG.warn("There isn't any notification stream that would match listener adapter {}.", listener);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Notification listener {} cannot be closed.", listener, e);
            throw new IllegalStateException("Notification listener %s cannot be closed.".formatted(listener), e);
        }
    }

    /**
     * Removal and closing of general listener (data-change or notification listener).
     *
     * @param listener Listener to be closed and removed from cache.
     */
    final void removeAndCloseListener(final BaseListenerInterface listener) {
        requireNonNull(listener);
        if (listener instanceof ListenerAdapter) {
            removeAndCloseDataChangeListener((ListenerAdapter) listener);
        } else if (listener instanceof NotificationListenerAdapter) {
            removeAndCloseNotificationListener((NotificationListenerAdapter) listener);
        }
    }

    /**
     * Creates string representation of stream name from URI. Removes slash from URI in start and end positions,
     * and optionally {@link URLConstants#BASE_PATH} prefix.
     *
     * @param uri URI for creation of stream name.
     * @return String representation of stream name.
     */
    private static String createStreamNameFromUri(final String uri) {
        String result = requireNonNull(uri);
        while (true) {
            if (result.startsWith(URLConstants.BASE_PATH)) {
                result = result.substring(URLConstants.BASE_PATH.length());
            } else if (result.startsWith("/")) {
                result = result.substring(1);
            } else {
                break;
            }
        }
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Prepare URL from base name and stream name.
     *
     * @param uriInfo base URL information
     * @param streamName name of stream for create
     * @return final URL
     */
    public abstract @NonNull URI prepareUriByStreamName(UriInfo uriInfo, String streamName);

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
    public final @NonNull URI subscribeToYangStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final String streamName = createStreamNameFromUri(identifier);
        if (isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var notificationListenerAdapter = notificationListenerFor(streamName);
        if (notificationListenerAdapter == null) {
            throw new RestconfDocumentedException("Stream with name %s was not found.".formatted(streamName),
                ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final URI uri = prepareUriByStreamName(uriInfo, streamName);
        notificationListenerAdapter.setQueryParams(notificationQueryParams);
        notificationListenerAdapter.listen(handlersHolder.notificationService());
        final DOMDataBroker dataBroker = handlersHolder.dataBroker();
        notificationListenerAdapter.setCloseVars(dataBroker, handlersHolder.databindProvider());
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
    public final URI subscribeToDataStream(final String identifier, final UriInfo uriInfo,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final var streamName = createStreamNameFromUri(identifier);
        final var listener = dataChangeListenerFor(streamName);
        if (listener == null) {
            throw new RestconfDocumentedException("No listener found for stream " + streamName,
                ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }

        listener.setQueryParams(notificationQueryParams);

        final var dataBroker = handlersHolder.dataBroker();
        final var schemaHandler = handlersHolder.databindProvider();
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
