/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.testkit;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.ActionProviderService;
import org.opendaylight.mdsal.binding.api.ActionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.NotificationPublishService;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.RpcService;
import org.opendaylight.mdsal.binding.dom.adapter.AdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitDeadlockException;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreConfigProperties;
import org.opendaylight.yangtools.binding.DataRoot;
import org.opendaylight.yangtools.binding.data.codec.dynamic.DynamicBindingDataCodec;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingCodecContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.util.concurrent.DeadlockDetectingListeningExecutorService;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A working MD-SAL container. Instances can be created via {@link #builder(Class)} and provide all the usual MD-SAL
 * services.
 */
@NonNullByDefault
public final class MdsalTestkit implements AutoCloseable {
    private static final ThreadFactory COMMIT_TF = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("databroker-commit-%s")
        .build();

    // Binding/Data codec, including Java Binding runtime and EffectiveModelContext
    private final BindingCodecContext codecContext;
    // Binding/DOM adapter
    private final ConstantAdapterContext adapterContext;

    // Simple DOM services
    private final DOMMountPointService domMountPointService = new DOMMountPointServiceImpl();
    private final DOMNotificationRouter notificationRouter;
    private final DOMSchemaService schemaService;
    private final DOMRpcRouter rpcRouter;

    // DOMDataBroker and related
    private final List<InMemoryDOMDataStore> datastores;
    private final SerializedDOMDataBroker domDataBroker;
    private final ExecutorService commitExecutor;
    private final ExecutorService futureExecutor;

    // Binding services
    private final ActionService actionService;
    private final ActionProviderService actionProviderService;
    private final DataBroker dataBroker;
    private final MountPointService mountPointService;
    private final NotificationService notificationService;
    private final NotificationPublishService notificationPublishService;
    private final RpcService rpcService;
    private final RpcProviderService rpcProviderService;

    private MdsalTestkit(final BindingRuntimeContext context, final boolean immediateFutures) {
        codecContext = new BindingCodecContext(context);
        adapterContext = new ConstantAdapterContext(codecContext);
        schemaService = new FixedDOMSchemaService(context::modelContext);
        notificationRouter = new DOMNotificationRouter(0);
        rpcRouter = new DOMRpcRouter(schemaService);

        datastores = List.of(
            new InMemoryDOMDataStore("CFG", LogicalDatastoreType.CONFIGURATION,
                MoreExecutors.newDirectExecutorService(),
                InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE, false),
            new InMemoryDOMDataStore("OPER", LogicalDatastoreType.OPERATIONAL, MoreExecutors.newDirectExecutorService(),
                InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE, false));
        datastores.forEach(datastore -> datastore.onModelContextUpdated(schemaService.getGlobalContext()));

        // TODO: we may want to
        futureExecutor = immediateFutures ? MoreExecutors.newDirectExecutorService()
            : SpecialExecutors.newBlockingBoundedCachedThreadPool(1, 5, "databroker-future", MdsalTestkit.class);
        commitExecutor = new DeadlockDetectingListeningExecutorService(Executors.newSingleThreadExecutor(COMMIT_TF),
            TransactionCommitDeadlockException.DEADLOCK_EXCEPTION_SUPPLIER, futureExecutor);

        domDataBroker = new SerializedDOMDataBroker(Map.of(
            LogicalDatastoreType.CONFIGURATION, datastores.getFirst(),
            LogicalDatastoreType.OPERATIONAL, datastores.getLast()), commitExecutor);

        final var loader = new TestkitAdapterLoader(adapterContext, List.of(
            domActionService(), domActionProviderService(), domDataBroker(), domMountPointService(),
            domNotificationService(), domNotificationPublishService(), domRpcService(), domRpcProviderService()));

        actionService = loader.getService(ActionService.class);
        actionProviderService = loader.getService(ActionProviderService.class);
        dataBroker = loader.getService(DataBroker.class);
        mountPointService = loader.getService(MountPointService.class);
        notificationService = loader.getService(NotificationService.class);
        notificationPublishService = loader.getService(NotificationPublishService.class);
        rpcService = loader.getService(RpcService.class);
        rpcProviderService = loader.getService(RpcProviderService.class);
    }

    public static Builder builder(final Class<? extends DataRoot<?>> root) {
        return new Builder(new Class<?>[] { root.asSubclass(DataRoot.class) });
    }

    @SafeVarargs
    public static Builder builder(final Class<? extends DataRoot<?>> first,
            final Class<? extends DataRoot<?>>... others) {
        return new Builder(Stream.concat(Stream.of(first), Arrays.stream(others))
            .<Class<?>>map(root -> root.asSubclass(DataRoot.class))
            .sorted(Comparator.comparing(Class::getName))
            .distinct()
            .toArray(Class<?>[]::new));
    }

    public EffectiveModelContext modelContext() {
        return codecContext.modelContext();
    }

    public BindingRuntimeContext runtimeContext() {
        return codecContext.runtimeContext();
    }

    public DynamicBindingDataCodec bindingCodec() {
        return codecContext;
    }

    public AdapterContext adapterContext() {
        return adapterContext;
    }

    public DOMActionService domActionService() {
        return rpcRouter.actionService();
    }

    public DOMActionProviderService domActionProviderService() {
        return rpcRouter.actionProviderService();
    }

    public DOMDataBroker domDataBroker() {
        return domDataBroker;
    }

    public DOMMountPointService domMountPointService() {
        return domMountPointService;
    }

    public DOMNotificationService domNotificationService() {
        return notificationRouter.notificationService();
    }

    public DOMNotificationPublishService domNotificationPublishService() {
        return notificationRouter.notificationPublishService();
    }

    public DOMRpcService domRpcService() {
        return rpcRouter.rpcService();
    }

    public DOMRpcProviderService domRpcProviderService() {
        return rpcRouter.rpcProviderService();
    }

    public DOMSchemaService domSchemaService() {
        return schemaService;
    }

    public ActionService actionService() {
        return actionService;
    }

    public ActionProviderService actionProviderService() {
        return actionProviderService;
    }

    public DataBroker dataBroker() {
        return dataBroker;
    }

    public MountPointService mountPointService() {
        return mountPointService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public NotificationPublishService notificationPublishService() {
        return notificationPublishService;
    }

    public RpcService rpcService() {
        return rpcService;
    }

    public RpcProviderService rpcProviderService() {
        return rpcProviderService;
    }

    @Override
    public void close() {
        rpcRouter.close();
        notificationRouter.close();
        domDataBroker.close();
        commitExecutor.close();
        futureExecutor.close();
        datastores.forEach(InMemoryDOMDataStore::close);
    }

    public static final class Builder {
        private final BindingRuntimeContext runtimeContext;

        private boolean immediateFutures;

        Builder(final Class<?>[] modules) {
            runtimeContext = BindingRuntimeHelpers.createRuntimeContext(modules);
        }

        public Builder setImmediateFutures(final boolean newImmediateFutures) {
            immediateFutures = newImmediateFutures;
            return this;
        }

        public MdsalTestkit build() {
            return new MdsalTestkit(runtimeContext, immediateFutures);
        }
    }
}
