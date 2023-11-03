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
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.ReceiveEventsParams;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamInput;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Scope;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
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

    /**
     * Holder of all handlers for notifications.
     */
    // FIXME: why do we even need this class?!
    private record HandlersHolder(
            @NonNull DOMDataBroker dataBroker,
            @NonNull DOMNotificationService notificationService,
            @NonNull DatabindProvider databindProvider) {

        HandlersHolder {
            requireNonNull(dataBroker);
            requireNonNull(notificationService);
            requireNonNull(databindProvider);
        }
    }

//    private static final QName LOCATION_QNAME = QName.create(Notifi.QNAME, "location").intern();
//    private static final NodeIdentifier LOCATION_NODEID = NodeIdentifier.create(LOCATION_QNAME);
//    private static final String STREAMS_PATH = "ietf-restconf-monitoring:restconf-state/streams";
//    private static final String STREAM_PATH_PART = "/stream=";
//    private static final String STREAM_PATH = STREAMS_PATH + STREAM_PATH_PART;
//    private static final String STREAM_ACCESS_PATH_PART = "/access=";
//    private static final String STREAM_LOCATION_PATH_PART = "/location";
//
//    private final ListenersBroker listenersBroker;
//    private final HandlersHolder handlersHolder;
//
//  // FIXME: NETCONF:1102: do not instantiate this service
//  new RestconfStreamsSubscriptionServiceImpl(dataBroker, notificationService, databindProvider,
//      listenersBroker),
//
//    /**
//     * Initialize holder of handlers with holders as parameters.
//     *
//     * @param dataBroker {@link DOMDataBroker}
//     * @param notificationService {@link DOMNotificationService}
//     * @param databindProvider a {@link DatabindProvider}
//     * @param listenersBroker a {@link ListenersBroker}
//     */
//    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBroker dataBroker,
//            final DOMNotificationService notificationService, final DatabindProvider databindProvider,
//            final ListenersBroker listenersBroker) {
//        handlersHolder = new HandlersHolder(dataBroker, notificationService, databindProvider);
//        this.listenersBroker = requireNonNull(listenersBroker);
//    }
//
//    @Override
//    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
//        final var params = QueryParams.newReceiveEventsParams(uriInfo);
//
//        final URI location;
//        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
//            location = listenersBroker.subscribeToDataStream(identifier, uriInfo, params, handlersHolder);
//        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
//            location = listenersBroker.subscribeToYangStream(identifier, uriInfo, params, handlersHolder);
//        } else {
//            final String msg = "Bad type of notification of sal-remote";
//            LOG.warn(msg);
//            throw new RestconfDocumentedException(msg);
//        }
//
//        return Response.ok()
//            .location(location)
//            .entity(new NormalizedNodePayload(
//                Inference.ofDataTreePath(handlersHolder.databindProvider().currentContext().modelContext(),
//                    Notifi.QNAME, LOCATION_QNAME),
//                ImmutableNodes.leafNode(LOCATION_NODEID, location.toString())))
//            .build();
//    }

    private static final Logger LOG = LoggerFactory.getLogger(ListenersBroker.class);

    // Prefixes for stream names
    private static final String DATA_SUBSCRIPTION = "data-change-event-subscription";
    private static final String NOTIFICATION_STREAM = "notification-stream";
    private static final String DEVICE_NOTIFICATION_STREAM = "device-notification-stream";

    private static final QNameModule SAL_REMOTE_AUGMENT = NotificationOutputTypeGrouping.QNAME.getModule();

    private static final QNameModule DEVICE_NOTIFICATION_MODULE = SubscribeDeviceNotificationInput.QNAME.getModule();
    private static final QName DATASTORE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.DATASTORE_PARAM_NAME).intern();
    private static final QName SCOPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.SCOPE_PARAM_NAME).intern();
    private static final QName OUTPUT_TYPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, "notification-output-type").intern();
    private static final QName DEVICE_NOTIFICATION_PATH_QNAME =
        QName.create(DEVICE_NOTIFICATION_MODULE, "path").intern();
    private static final QName DEVICE_NOTIFICATION_STREAM_PATH =
        QName.create(DEVICE_NOTIFICATION_PATH_QNAME, "stream-path").intern();
    private static final NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(DATASTORE_QNAME);
    private static final NodeIdentifier SCOPE_NODEID = NodeIdentifier.create(SCOPE_QNAME);
    private static final NodeIdentifier OUTPUT_TYPE_NODEID = NodeIdentifier.create(OUTPUT_TYPE_QNAME);
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(DEVICE_NOTIFICATION_PATH_QNAME);
    private static final NodeIdentifier SAL_REMOTE_OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);
    private static final NodeIdentifier NOTIFICATIONS =
        NodeIdentifier.create(QName.create(CreateNotificationStreamInput.QNAME, "notifications").intern());
    private static final NodeIdentifier PATH_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionInput.QNAME, "path").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());

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
    public final @Nullable AbstractStream<?> listenerFor(final String streamName) {
        if (streamName.startsWith(NOTIFICATION_STREAM)) {
            return notificationListenerFor(streamName);
        } else if (streamName.startsWith(DATA_SUBSCRIPTION)) {
            return dataChangeListenerFor(streamName);
        } else if (streamName.startsWith(DEVICE_NOTIFICATION_STREAM)) {
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
        final var sb = new StringBuilder(DATA_SUBSCRIPTION)
            .append('/').append(createStreamNameFromUri(IdentifierCodec.serialize(path, modelContext)))
            .append('/').append(RestconfStreamsConstants.DATASTORE_PARAM_NAME).append('=').append(datastore)
            .append('/').append(RestconfStreamsConstants.SCOPE_PARAM_NAME).append('=').append(scope);
        if (outputType != NotificationOutputType.XML) {
            sb.append('/').append(outputType.getName());
        }

        final long stamp = dataChangeListenersLock.writeLock();
        try {
            return dataChangeListeners.computeIfAbsent(sb.toString(),
                streamName -> new ListenerAdapter(streamName, outputType, this, datastore, path));
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
        final var sb = new StringBuilder(NOTIFICATION_STREAM).append('/');
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
                streamName -> new NotificationListenerAdapter(streamName, outputType, this, notifications));
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
    private DeviceNotificationListenerAdaptor registerDeviceNotificationListener(final String deviceName,
            final NotificationOutputType outputType, final EffectiveModelContext refSchemaCtx,
            final DOMMountPointService mountPointService, final YangInstanceIdentifier path) {
        final var sb = new StringBuilder(DEVICE_NOTIFICATION_STREAM).append('/')
            .append(deviceName);

        final long stamp = deviceNotificationListenersLock.writeLock();
        try {
            return deviceNotificationListeners.computeIfAbsent(sb.toString(),
                streamName -> new DeviceNotificationListenerAdaptor(streamName, outputType, this, refSchemaCtx,
                    mountPointService, path));
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
     * @param stream Stream to be closed and removed from cache.
     */
    final void removeAndCloseListener(final AbstractStream<?> stream) {
        requireNonNull(stream);
        if (stream instanceof ListenerAdapter dataChange) {
            removeAndCloseDataChangeListener(dataChange);
        } else if (stream instanceof NotificationListenerAdapter notification) {
            removeAndCloseNotificationListener(notification);
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
            final ReceiveEventsParams notificationQueryParams, final HandlersHolder handlersHolder) {
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
            notificationListenerAdapter.qnames(), notificationListenerAdapter.getOutputType(), uri);

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
            final ReceiveEventsParams notificationQueryParams, final HandlersHolder handlersHolder) {
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
                listener.getOutputType(), uri, schemaContext, serializedPath);
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


    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param input Input of RPC - example in JSON (data-change-event stream):
     *              <pre>
     *              {@code
     *                  {
     *                      "input": {
     *                          "path": "/toaster:toaster/toaster:toasterStatus",
     *                          "sal-remote-augment:datastore": "OPERATIONAL",
     *                          "sal-remote-augment:scope": "ONE"
     *                      }
     *                  }
     *              }
     *              </pre>
     * @param modelContext Reference to {@link EffectiveModelContext}.
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    // FIXME: this really should be a normal RPC implementation
    public final RestconfFuture<Optional<ContainerNode>> createDataChangeNotifiStream(final ContainerNode input,
            final EffectiveModelContext modelContext) {
        final var datastoreName = extractStringLeaf(input, DATASTORE_NODEID);
        final var scopeName = extractStringLeaf(input, SCOPE_NODEID);
        final var adapter = registerDataChangeListener(modelContext,
            datastoreName != null ? LogicalDatastoreType.valueOf(datastoreName) : LogicalDatastoreType.CONFIGURATION,
            preparePath(input), scopeName != null ? Scope.ofName(scopeName) : Scope.BASE, prepareOutputType(input));

        // building of output
        return RestconfFuture.of(Optional.of(Builders.containerBuilder()
            .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
            .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, adapter.getStreamName()))
            .build()));
    }

    // FIXME: this really should be a normal RPC implementation
    public final RestconfFuture<Optional<ContainerNode>> createNotificationStream(final ContainerNode input,
            final EffectiveModelContext modelContext) {
        final var qnames = ((LeafSetNode<String>) input.getChildByArg(NOTIFICATIONS)).body().stream()
            .map(LeafSetEntryNode::body)
            .map(QName::create)
            .sorted()
            .collect(ImmutableSet.toImmutableSet());

        for (var qname : qnames) {
            if (modelContext.findNotification(qname).isEmpty()) {
                throw new RestconfDocumentedException(qname + " refers to an unknown notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
            }
        }

        // registration of the listener
        final var adapter = registerNotificationListener(modelContext, qnames, prepareOutputType(input));

        return RestconfFuture.of(Optional.of(Builders.containerBuilder()
            .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
            .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, adapter.getStreamName()))
            .build()));
    }

    /**
     * Create device notification stream.
     *
     * @param baseUrl base Url
     * @param input RPC input
     * @param mountPointService dom mount point service
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON
     */
    // FIXME: this should be an RPC invocation
    public final RestconfFuture<Optional<ContainerNode>> createDeviceNotificationListener(final ContainerNode input,
            final String baseUrl, final DOMMountPointService mountPointService) {
        // parsing out of container with settings and path
        // FIXME: ugly cast
        final var path = (YangInstanceIdentifier) input.findChildByArg(DEVICE_NOTIFICATION_PATH_NODEID)
                .map(DataContainerChild::body)
                .orElseThrow(() -> new RestconfDocumentedException("No path specified", ErrorType.APPLICATION,
                    ErrorTag.DATA_MISSING));

        if (!(path.getLastPathArgument() instanceof NodeIdentifierWithPredicates listId)) {
            throw new RestconfDocumentedException("Path does not refer to a list item", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }
        if (listId.size() != 1) {
            throw new RestconfDocumentedException("Target list uses multiple keys", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }
        final String deviceName = listId.values().iterator().next().toString();

        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point not available", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED));

        final DOMNotificationService mountNotifService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point does not support notifications",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED));

        final var mountModelContext = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point schema not available",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED))
            .getGlobalContext();
        final var notificationPaths = mountModelContext.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> Absolute.of(notification.argument()))
            .collect(ImmutableSet.toImmutableSet());
        if (notificationPaths.isEmpty()) {
            throw new RestconfDocumentedException("Device does not support notification", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED);
        }

        final var notificationListenerAdapter = registerDeviceNotificationListener(deviceName,
            prepareOutputType(input), mountModelContext, mountPointService, mountPoint.getIdentifier());
        notificationListenerAdapter.listen(mountNotifService, notificationPaths);

        return RestconfFuture.of(Optional.of(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SubscribeDeviceNotificationOutput.QNAME))
            .withChild(ImmutableNodes.leafNode(DEVICE_NOTIFICATION_STREAM_PATH,
                baseUrl + notificationListenerAdapter.getStreamName()))
            .build()));
    }

    /**
     * Prepare {@link NotificationOutputType}.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link NotificationOutputType}.
     */
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        final String outputName = extractStringLeaf(data, OUTPUT_TYPE_NODEID);
        return outputName != null ? NotificationOutputType.valueOf(outputName) : NotificationOutputType.XML;
    }

    /**
     * Prepare {@link YangInstanceIdentifier} of stream source.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link YangInstanceIdentifier} of data element from which the data-change-event notifications
     *         are going to be generated.
     */
    private static YangInstanceIdentifier preparePath(final ContainerNode data) {
        final var pathLeaf = data.childByArg(PATH_NODEID);
        if (pathLeaf != null && pathLeaf.body() instanceof YangInstanceIdentifier pathValue) {
            return pathValue;
        }

        throw new RestconfDocumentedException("Instance identifier was not normalized correctly",
            ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    private static @Nullable String extractStringLeaf(final ContainerNode data, final NodeIdentifier childName) {
        return data.childByArg(childName) instanceof LeafNode<?> leafNode && leafNode.body() instanceof String str
            ? str : null;
    }
}
