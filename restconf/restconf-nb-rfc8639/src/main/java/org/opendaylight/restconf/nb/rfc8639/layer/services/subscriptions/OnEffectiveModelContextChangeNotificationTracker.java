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
import java.time.Instant;
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
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
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
            final NormalizedNode<?, ?> mapToStreams = mapYangNotificationStreamByIetfRestconfMonitoring(
                    notificationDefinition, prefixedStreamName, null, uri, module, streamsContainerExist);
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

    private static NormalizedNode<?, ?> mapYangNotificationStreamByIetfRestconfMonitoring(
            final NotificationDefinition notificationDefinition, final String prefixedStreamName,
            final Instant replayLogCreationTime, final URI uri, final Module monitoringModule,
            final boolean existParent) {
        final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                .findDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME).get())
                .findDataChildByName(MonitoringModule.CONT_STREAMS_QNAME).get())
                .findDataChildByName(MonitoringModule.LIST_STREAM_QNAME).get();
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry = Builders
                .mapEntryBuilder((ListSchemaNode) streamListSchema);

        final ListSchemaNode listSchema = (ListSchemaNode) streamListSchema;
        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.findDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME).get(),
                prefixedStreamName);
        if (notificationDefinition.getDescription().isPresent()
                && !notificationDefinition.getDescription().get().isEmpty()) {
            SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.findDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME).get(),
                    notificationDefinition.getDescription().get());
        }
        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.findDataChildByName(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME).get(), true);
        if (replayLogCreationTime != null) {
            SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.findDataChildByName(MonitoringModule.LEAF_START_TIME_STREAM_QNAME).get(),
                    SubscribedNotificationsUtil.timeStampToRFC3339Format(replayLogCreationTime));
        }
        prepareListAndFillEntryBuilder(streamEntry,
                (ListSchemaNode) listSchema.findDataChildByName(MonitoringModule.LIST_ACCESS_STREAM_QNAME).get(), uri);

        if (!existParent) {
            final DataSchemaNode contStreamsSchema = ((ContainerSchemaNode) monitoringModule
                    .findDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME).get())
                    .findDataChildByName(MonitoringModule.CONT_STREAMS_QNAME).get();
            return Builders.containerBuilder((ContainerSchemaNode) contStreamsSchema).withChild(Builders
                    .mapBuilder((ListSchemaNode) streamListSchema).withChild(streamEntry.build()).build())
                    .build();
        }
        return streamEntry.build();
    }

    private static void prepareListAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final ListSchemaNode listSchemaNode, final URI streamLocationUri) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> accessListBuilder = Builders.mapBuilder(listSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> accessListXmlEntry = Builders
                .mapEntryBuilder(listSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> accessListJsonEntry = Builders
                .mapEntryBuilder(listSchemaNode);

        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(accessListXmlEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME).get(), "xml");
        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(accessListXmlEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME).get(),
                streamLocationUri.toString());

        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(accessListJsonEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME).get(), "json");
        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(accessListJsonEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME).get(),
                streamLocationUri.toString());

        streamEntry.withChild(accessListBuilder.withChild(accessListXmlEntry.build())
                .withChild(accessListJsonEntry.build())
                .build());
    }

    private static boolean checkNodeExistInDatastore(final SchemaContext schemaContext,
            final DOMDataTreeReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastoreType,
            final String pathToNode, final DOMTransactionChain domTransactionChain) {
        try {
            return readWriteTransaction.exists(datastoreType, IdentifierCodec.deserialize(pathToNode, schemaContext))
                    .get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while checking if data at path " + pathToNode + " exist.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        } finally {
            domTransactionChain.close();
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
