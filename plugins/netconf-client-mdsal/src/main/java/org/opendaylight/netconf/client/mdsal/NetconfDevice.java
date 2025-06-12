/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.rfc8528.model.api.SchemaMountConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This is a mediator between NetconfDeviceCommunicator and NetconfDeviceSalFacade.
 */
public class NetconfDevice implements RemoteDevice<NetconfDeviceCommunicator> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDevice.class);
    private static final QName RFC8528_SCHEMA_MOUNTS_QNAME = QName.create(
        SchemaMountConstants.RFC8528_MODULE, "schema-mounts").intern();
    private static final YangInstanceIdentifier RFC8528_SCHEMA_MOUNTS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RFC8528_SCHEMA_MOUNTS_QNAME));

    protected final RemoteDeviceId id;
    private final BaseNetconfSchemaProvider baseSchemaProvider;
    private final DeviceNetconfSchemaProvider deviceSchemaProvider;
    private final Executor processingExecutor;

    private final RemoteDeviceHandler salFacade;
    private final DeviceActionFactory deviceActionFactory;
    private final NotificationHandler notificationHandler;
    private final boolean reconnectOnSchemasChange;

    @GuardedBy("this")
    private ListenableFuture<?> schemaFuture;
    @GuardedBy("this")
    private boolean connected = false;

    public NetconfDevice(final RemoteDeviceId id,final BaseNetconfSchemaProvider baseSchemaProvider,
            final DeviceNetconfSchemaProvider deviceSchemaProvider, final RemoteDeviceHandler salFacade,
            final Executor globalProcessingExecutor, final boolean reconnectOnSchemasChange) {
        this(id, baseSchemaProvider, deviceSchemaProvider, salFacade, globalProcessingExecutor,
            reconnectOnSchemasChange, null);
    }

    public NetconfDevice(final RemoteDeviceId id, final BaseNetconfSchemaProvider baseSchemaProvider,
            final DeviceNetconfSchemaProvider deviceSchemaProvider, final RemoteDeviceHandler salFacade,
            final Executor globalProcessingExecutor, final boolean reconnectOnSchemasChange,
            final DeviceActionFactory deviceActionFactory) {
        this.id = requireNonNull(id);
        this.baseSchemaProvider = requireNonNull(baseSchemaProvider);
        this.deviceSchemaProvider = requireNonNull(deviceSchemaProvider);
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        this.deviceActionFactory = deviceActionFactory;
        this.salFacade = salFacade;
        processingExecutor = requireNonNull(globalProcessingExecutor);
        notificationHandler = new NotificationHandler(salFacade, id);
    }

    @Override
    public synchronized void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
            final NetconfDeviceCommunicator listener) {
        // EffectiveModelContext setup has to be performed in a dedicated thread since we are in a Netty thread in this
        // method.
        // YANG models are being downloaded in this method and it would cause a deadlock if we used the netty thread
        // https://netty.io/wiki/thread-model.html
        connected = true;
        LOG.debug("{}: Session to remote device established with {}", id, remoteSessionCapabilities);

        final var baseSchema = baseSchemaProvider.baseSchemaForCapabilities(remoteSessionCapabilities);
        final var initRpc = new NetconfDeviceRpc(baseSchema.modelContext(), listener,
            new NetconfMessageTransformer(baseSchema.databind(), false, baseSchema));

        final var deviceSchema = deviceSchemaProvider.deviceNetconfSchemaFor(id, remoteSessionCapabilities, initRpc,
            baseSchema, processingExecutor);

        // Potentially acquire mount point list and interpret it
        final var netconfDeviceSchemaFuture = Futures.transformAsync(deviceSchema,
            result -> Futures.transform(discoverMountPoints(result.modelContext(), baseSchema, listener),
                withMounts -> new NetconfDeviceSchema(withMounts, result.capabilities()),
                processingExecutor),
            processingExecutor);
        schemaFuture = netconfDeviceSchemaFuture;

        Futures.addCallback(netconfDeviceSchemaFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(final NetconfDeviceSchema result) {
                handleSalInitializationSuccess(listener, baseSchema, result, remoteSessionCapabilities,
                    getDeviceSpecificRpc(result.databind(), listener, baseSchema));
            }

            @Override
            public void onFailure(final Throwable cause) {
                // The method onRemoteSessionDown was called while the EffectiveModelContext for the device was being
                // processed.
                if (cause instanceof CancellationException) {
                    LOG.warn("{}: Device communicator was tear down since the schema setup started", id);
                } else {
                    handleSalInitializationFailure(listener, cause);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerToBaseNetconfStream(final NetconfRpcService deviceRpc,
                                             final NetconfDeviceCommunicator listener) {
        // TODO check whether the model describing create subscription is present in schema
        // Perhaps add a default schema context to support create-subscription if the model was not provided
        // (same as what we do for base netconf operations in transformer)
        final var rpcResultListenableFuture = deviceRpc.invokeNetconf(CreateSubscription.QNAME,
            ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(CreateSubscriptionInput.QNAME))
                // Note: default 'stream' is 'NETCONF', we do not need to create an explicit leaf
                .build());

        Futures.addCallback(rpcResultListenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult domRpcResult) {
                notificationHandler.addNotificationFilter(notification -> {
                    if (NetconfCapabilityChange.QNAME.equals(notification.getBody().name().getNodeType())) {
                        LOG.info("{}: Schemas change detected, reconnecting", id);
                        // Only disconnect is enough,
                        // the reconnecting nature of the connector will take care of reconnecting
                        listener.disconnect();
                        return false;
                    }
                    return true;
                });
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to subscribe to base notification stream. Schemas will not be reloaded on the fly",
                        throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private boolean shouldListenOnSchemaChange(final NetconfSessionPreferences remoteSessionCapabilities) {
        return remoteSessionCapabilities.isNotificationsSupported() && reconnectOnSchemasChange;
    }

    private synchronized void handleSalInitializationSuccess(final NetconfDeviceCommunicator listener,
            final BaseNetconfSchema baseSchema, final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences remoteSessionCapabilities, final Rpcs deviceRpc) {
        // NetconfDevice.SchemaSetup can complete after NetconfDeviceCommunicator was closed. In that case do nothing,
        // since salFacade.onDeviceDisconnected was already called.
        if (!connected) {
            LOG.warn("{}: Device communicator was closed before schema setup finished.", id);
            return;
        }

        if (shouldListenOnSchemaChange(remoteSessionCapabilities)) {
            registerToBaseNetconfStream(deviceRpc, listener);
        }

        final var messageTransformer = new NetconfMessageTransformer(deviceSchema.databind(), true, baseSchema);

        // Order is important here: salFacade has to see the device come up and then the notificationHandler can deliver
        // whatever notifications have been held back
        salFacade.onDeviceConnected(deviceSchema, remoteSessionCapabilities,
            new RemoteDeviceServices(deviceRpc, deviceActionFactory == null ? null
                : deviceActionFactory.createDeviceAction(messageTransformer, listener)));
        notificationHandler.onRemoteSchemaUp(messageTransformer);

        LOG.info("{}: Netconf connector initialized successfully", id);
    }

    private void handleSalInitializationFailure(final RemoteDeviceCommunicator listener, final Throwable cause) {
        LOG.warn("{}: Unexpected error resolving device sources", id, cause);
        listener.close();
        cleanupInitialization();
        salFacade.onDeviceFailed(cause);
    }

    private synchronized void cleanupInitialization() {
        connected = false;
        if (schemaFuture != null && !schemaFuture.isDone() && !schemaFuture.cancel(true)) {
            LOG.warn("The cleanup of Schema Futures for device {} was unsuccessful.", id);
        }
        notificationHandler.onRemoteSchemaDown();
    }

    private ListenableFuture<@NonNull DatabindContext> discoverMountPoints(
            final EffectiveModelContext modelContext, final BaseNetconfSchema baseSchema,
            final NetconfDeviceCommunicator listener) {
        final var emptyDatabind = DatabindContext.ofModel(modelContext);
        if (modelContext.findModule(SchemaMountConstants.RFC8528_MODULE).isEmpty()) {
            return Futures.immediateFuture(emptyDatabind);
        }

        // Create a temporary RPC invoker and acquire the mount point tree
        LOG.debug("{}: Acquiring available mount points", id);
        final NetconfDeviceRpc deviceRpc = new NetconfDeviceRpc(modelContext, listener,
            new NetconfMessageTransformer(emptyDatabind, false, baseSchema));

        return Futures.transform(deviceRpc.invokeNetconf(Get.QNAME, ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID)
            .withChild(NetconfMessageTransformUtil.toFilterStructure(RFC8528_SCHEMA_MOUNTS, modelContext))
            .build()), rpcResult -> processSchemaMounts(rpcResult, emptyDatabind), MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    private DatabindContext processSchemaMounts(final DOMRpcResult rpcResult,
            final DatabindContext emptyDatabind) {
        final var errors = rpcResult.errors();
        if (!errors.isEmpty()) {
            LOG.warn("{}: Schema-mounts acquisition resulted in errors {}", id, errors);
        }

        final var schemaMounts = rpcResult.value();
        if (schemaMounts == null) {
            LOG.debug("{}: device does not define any schema mounts", id);
            return emptyDatabind;
        }

        return DeviceMountPointContext.create(emptyDatabind, schemaMounts);
    }

    @Override
    public void onRemoteSessionDown() {
        cleanupInitialization();
        salFacade.onDeviceDisconnected();
    }

    @Override
    public void onNotification(final NetconfMessage notification) {
        notificationHandler.handleNotification(notification);
    }

    protected NetconfDeviceRpc getDeviceSpecificRpc(final DatabindContext result,
            final RemoteDeviceCommunicator listener, final BaseNetconfSchema schema) {
        return new NetconfDeviceRpc(result.modelContext(), listener,
            new NetconfMessageTransformer(result, true, schema));
    }

    /**
     * A dedicated exception to indicate when we fail to setup an {@link EffectiveModelContext}.
     */
    public static final class EmptySchemaContextException extends Exception {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public EmptySchemaContextException(final String message) {
            super(message);
        }
    }
}
