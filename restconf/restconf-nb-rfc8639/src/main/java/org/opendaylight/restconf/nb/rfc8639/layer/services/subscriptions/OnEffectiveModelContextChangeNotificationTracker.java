/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Collections2;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.util.SimpleUriInfo;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class OnEffectiveModelContextChangeNotificationTracker implements EffectiveModelContextListener {
    private static final Collection<NotificationDefinition> NOTIFICATIONS = Collections
            .synchronizedSet(new HashSet<>());

    private final Map<QName, ListenerRegistration<NotificationListener>> notificationsRegistration = new HashMap<>();

    private final DOMNotificationService domNotificationService;
    private final TransactionChainHandler transactionChainHandler;
    private final Map<QName, ReplayBuffer> replayBuffersForNotifications;
    private final InetSocketAddress inetSocketAddress;
    private final long replayBufferMaxSize;

    public OnEffectiveModelContextChangeNotificationTracker(final DOMNotificationService domNotificationService,
            final TransactionChainHandler transactionChainHandler,
            final Map<QName, ReplayBuffer> replayBuffersForNotifications,
            final InetSocketAddress inetSocketAddress, final long replayBufferMaxSize) {
        this.domNotificationService = requireNonNull(domNotificationService);
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        this.replayBuffersForNotifications = requireNonNull(replayBuffersForNotifications);
        this.inetSocketAddress = requireNonNull(inetSocketAddress);
        this.replayBufferMaxSize = replayBufferMaxSize;
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final Collection<? extends NotificationDefinition> newNotifications = newModelContext.getNotifications();

        // returns those notifications which are in the current set, but not in the new set
        final Collection<? extends NotificationDefinition> notificationsToRemove =
                Collections2.filter(NOTIFICATIONS, input -> !newNotifications.contains(input));
        if (!notificationsToRemove.isEmpty()) {
            unregisterNotifications(notificationsToRemove);
            NOTIFICATIONS.removeAll(notificationsToRemove);
            deleteStreamsFromDatastore(notificationsToRemove, newModelContext);
        }

        // returns those notifications which are in the new set, but not in the current set
        final Collection<? extends NotificationDefinition> notificationsToAdd =
                Collections2.filter(newNotifications, input -> !NOTIFICATIONS.contains(input));
        if (!notificationsToAdd.isEmpty()) {
            writeStreamsToDatastore(notificationsToAdd, newModelContext);
            registerNotifications(notificationsToAdd, newModelContext);
            NOTIFICATIONS.addAll(notificationsToAdd);
        }
    }

    private void registerNotifications(final Collection<? extends NotificationDefinition> notifications,
            final SchemaContext schemaContext) {
        for (final NotificationDefinition notification : notifications) {
            final ReplayBuffer replayBufferForNotification = new ReplayBuffer(transactionChainHandler,
                    schemaContext, replayBufferMaxSize);
            final NotificationListener notificationListener = new NotificationListener(replayBufferForNotification);
            final ListenerRegistration<NotificationListener> listenerRegistration =
                    domNotificationService.registerNotificationListener(notificationListener, notification.getPath());
            notificationsRegistration.put(notification.getQName(), listenerRegistration);
            replayBuffersForNotifications.put(notification.getQName(), replayBufferForNotification);
        }
    }

    private void unregisterNotifications(final Collection<? extends NotificationDefinition> notifications) {
        for (final NotificationDefinition notification : notifications) {
            notificationsRegistration.get(notification.getQName()).close();
            notificationsRegistration.remove(notification.getQName());
            replayBuffersForNotifications.remove(notification.getQName());
        }
    }

    private void writeStreamsToDatastore(final Collection<? extends NotificationDefinition> notifications,
            final SchemaContext schemaContext) {
        final UriInfo uriInfo = new LocalUriInfo(inetSocketAddress);

        final DOMTransactionChain domTransactionChainExistIn = transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction wTxExistIn = domTransactionChainExistIn.newReadWriteTransaction();
        boolean streamsContainerExist = checkNodeExistInDatastore(schemaContext, wTxExistIn,
                LogicalDatastoreType.OPERATIONAL, Rfc8040.MonitoringModule.PATH_TO_STREAMS, domTransactionChainExistIn);

        final DOMTransactionChain domTransactionChain = transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction wTx = domTransactionChain.newReadWriteTransaction();

        for (final NotificationDefinition notificationDefinition : notifications) {
            final String prefixedStreamName = SubscribedNotificationsUtil.qNameToModulePrefixAndName(
                    notificationDefinition.getQName(), schemaContext);
            final URI uri = prepareUriByStreamName(uriInfo, prefixedStreamName);
            final Module module = schemaContext.findModule(Rfc8040.MonitoringModule.MODULE_QNAME).get();
            final NormalizedNode<?, ?> mapToStreams = SubscribedNotificationsUtil
                    .mapYangNotificationStreamByIetfRestconfMonitoring(notificationDefinition, prefixedStreamName,
                            null, uri, module, streamsContainerExist);
            final YangInstanceIdentifier path;
            if (streamsContainerExist) {
                path = createPathToStreamListEntry(prefixedStreamName);
            } else {
                path = createPathToStreamsContainer();
            }

            wTx.put(LogicalDatastoreType.OPERATIONAL, path, mapToStreams);
            streamsContainerExist = true;
        }

        SubscribedNotificationsUtil.submitData(wTx, domTransactionChain);
    }

    private static boolean checkNodeExistInDatastore(final SchemaContext schemaContext,
            final DOMDataTreeReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastoreType,
            final String pathToNode, final DOMTransactionChain domTransactionChain) {
        try (domTransactionChain) {
            return readWriteTransaction.exists(datastoreType, IdentifierCodec.deserialize(pathToNode, schemaContext))
                    .get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while checking if data at path " + pathToNode + " exist.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        }
    }

    /**
     * Create URI for stream.
     *
     * @param uriInfo
     *            - base URI information from request
     * @param streamName
     *            - path of stream
     * @return URI of stream
     */
    private static URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
        final URI baseUri = uriInfo.getBaseUri();

        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        final UriBuilder uriToServer = uriBuilder.port(baseUri.getPort()).scheme("http");
        return uriToServer.replacePath("/notifications/notification/" + streamName).build();
    }

    private void deleteStreamsFromDatastore(final Collection<? extends NotificationDefinition> notifications,
            final SchemaContext schemaContext) {
        final DOMTransactionChain domTransactionChain = transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction wTx = domTransactionChain.newReadWriteTransaction();
        for (final NotificationDefinition notification : notifications) {
            final YangInstanceIdentifier pathToStreamListEntry = createPathToStreamListEntry(
                    SubscribedNotificationsUtil.qNameToModulePrefixAndName(notification.getQName(), schemaContext));
            wTx.delete(LogicalDatastoreType.OPERATIONAL, pathToStreamListEntry);
        }

        SubscribedNotificationsUtil.submitData(wTx, domTransactionChain);
    }

    private static YangInstanceIdentifier createPathToStreamListEntry(final String prefixedStreamName) {
        return YangInstanceIdentifier.create(new NodeIdentifier(Rfc8040.MonitoringModule.CONT_RESTCONF_STATE_QNAME),
                new NodeIdentifier(Rfc8040.MonitoringModule.CONT_STREAMS_QNAME),
                new NodeIdentifier(Rfc8040.MonitoringModule.LIST_STREAM_QNAME),
                YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Rfc8040.MonitoringModule.LIST_STREAM_QNAME,
                        Rfc8040.MonitoringModule.LEAF_NAME_STREAM_QNAME, prefixedStreamName));
    }

    private static YangInstanceIdentifier createPathToStreamsContainer() {
        return YangInstanceIdentifier.create(new NodeIdentifier(Rfc8040.MonitoringModule.CONT_RESTCONF_STATE_QNAME),
                new NodeIdentifier(Rfc8040.MonitoringModule.CONT_STREAMS_QNAME));
    }

    private static class LocalUriInfo extends SimpleUriInfo {

        private final InetSocketAddress inetSocketAddress;

        LocalUriInfo(final InetSocketAddress inetSocketAddress) {
            super("/");
            this.inetSocketAddress = inetSocketAddress;
        }

        @Override
        public URI getBaseUri() {
            return UriBuilder.fromUri("http://" + inetSocketAddress.getHostName()).build();
        }
    }
}
