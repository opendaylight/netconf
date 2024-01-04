/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.mdsal.LibraryModulesSchemas;
import org.opendaylight.netconf.client.mdsal.LibrarySchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceBuilder;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.SchemalessNetconfDevice;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All state associated with a NETCONF topology node. Each node handles its own reconnection.
 */
public final class NetconfNodeHandler extends AbstractRegistration implements RemoteDeviceHandler {
    private abstract static sealed class Task {

        abstract void cancel();
    }

    private final class ConnectingTask extends Task implements FutureCallback<NetconfClientSession> {
        private final ListenableFuture<NetconfClientSession> future;

        ConnectingTask(final ListenableFuture<NetconfClientSession> future) {
            this.future = requireNonNull(future);
        }

        @Override
        void cancel() {
            future.cancel(false);
        }

        @Override
        public void onSuccess(final NetconfClientSession result) {
            connectComplete(this);
        }

        @Override
        public void onFailure(final Throwable cause) {
            if (cause instanceof CancellationException) {
                connectComplete(this);
            } else {
                connectFailed(this, cause);
            }
        }
    }

    private static final class SleepingTask extends Task {
        private final Timeout timeout;

        SleepingTask(final Timeout timeout) {
            this.timeout = requireNonNull(timeout);
        }

        @Override
        void cancel() {
            timeout.cancel();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeHandler.class);

    private final @NonNull List<SchemaSourceRegistration<?>> yanglibRegistrations;
    private final @NonNull NetconfClientFactory clientFactory;
    private final @NonNull NetconfClientConfiguration clientConfig;
    private final @NonNull NetconfDeviceCommunicator communicator;
    private final @NonNull RemoteDeviceHandler delegate;
    private final @NonNull Timer timer;
    private final @NonNull RemoteDeviceId deviceId;

    private final long maxAttempts;
    private final int minSleep;
    private final double sleepFactor;

    @GuardedBy("this")
    private long attempts;
    @GuardedBy("this")
    private long lastSleep;
    @GuardedBy("this")
    private Task currentTask;

    public NetconfNodeHandler(final NetconfClientFactory clientFactory, final Timer timer,
            final BaseNetconfSchemas baseSchemas, final SchemaResourceManager schemaManager,
            final Executor processingExecutor, final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceHandler delegate,
            final RemoteDeviceId deviceId, final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        this.clientFactory = requireNonNull(clientFactory);
        this.timer = requireNonNull(timer);
        this.delegate = requireNonNull(delegate);
        this.deviceId = requireNonNull(deviceId);

        maxAttempts = node.requireMaxConnectionAttempts().toJava();
        minSleep = node.requireBetweenAttemptsTimeoutMillis().toJava();
        sleepFactor = node.requireSleepFactor().doubleValue();

        // Setup reconnection on empty context, if so configured
        // FIXME: NETCONF-925: implement this
        if (nodeOptional != null && nodeOptional.getIgnoreMissingSchemaSources().getAllowed()) {
            LOG.warn("Ignoring missing schema sources is not currently implemented for {}", deviceId);
        }

        // The facade we are going it present to NetconfDevice
        RemoteDeviceHandler salFacade;
        final KeepaliveSalFacade keepAliveFacade;
        final long keepaliveDelay = node.requireKeepaliveDelay().toJava();
        if (keepaliveDelay > 0) {
            LOG.info("Adding keepalive facade, for device {}", nodeId);
            salFacade = keepAliveFacade = new KeepaliveSalFacade(deviceId, this, timer, keepaliveDelay,
                node.requireDefaultRequestTimeoutMillis().toJava());
        } else {
            salFacade = this;
            keepAliveFacade = null;
        }

        final RemoteDevice<NetconfDeviceCommunicator> device;
        if (node.requireSchemaless()) {
            device = new SchemalessNetconfDevice(baseSchemas, deviceId, salFacade);
            yanglibRegistrations = List.of();
        } else {
            final var resources = schemaManager.getSchemaResources(node.getSchemaCacheDirectory(), nodeId.getValue());
            device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(node.requireReconnectOnChangedSchema())
                .setSchemaResourcesDTO(resources)
                .setGlobalProcessingExecutor(processingExecutor)
                .setId(deviceId)
                .setSalFacade(salFacade)
                .setDeviceActionFactory(deviceActionFactory)
                .setBaseSchemas(baseSchemas)
                .build();
            yanglibRegistrations = registerDeviceSchemaSources(deviceId, node, resources);
        }

        final int rpcMessageLimit = node.requireConcurrentRpcLimit().toJava();
        if (rpcMessageLimit < 1) {
            LOG.info("Concurrent rpc limit is smaller than 1, no limit will be enforced for device {}", deviceId);
        }

        communicator = new NetconfDeviceCommunicator(deviceId, device, rpcMessageLimit,
            NetconfNodeUtils.extractUserCapabilities(node));

        if (keepAliveFacade != null) {
            keepAliveFacade.setListener(communicator);
        }

        clientConfig = builderFactory.createClientConfigurationBuilder(nodeId, node)
            .withSessionListener(communicator)
            .build();
    }

    public synchronized void connect() {
        attempts = 1;
        lastSleep = minSleep;
        lockedConnect();
    }

    @Holding("this")
    private void lockedConnect() {
        final ListenableFuture<NetconfClientSession> connectFuture;
        try {
            connectFuture = clientFactory.createClient(clientConfig);
        } catch (UnsupportedConfigurationException e) {
            onDeviceFailed(e);
            return;
        }

        final var nextTask = new ConnectingTask(connectFuture);
        currentTask = nextTask;
        Futures.addCallback(connectFuture, nextTask, MoreExecutors.directExecutor());
    }

    private synchronized void connectComplete(final ConnectingTask task) {
        // Just clear the task, if it matches our expectation
        completeTask(task);
    }

    private void connectFailed(final ConnectingTask task, final Throwable cause) {
        synchronized (this) {
            if (completeTask(task)) {
                // Mismatched future or the connection has been cancelled: nothing else to do
                return;
            }
            LOG.debug("Connection attempt {} to {} failed", attempts, deviceId, cause);
        }

        // We are invoking callbacks, do not hold locks
        reconnectOrFail();
    }

    @Holding("this")
    private boolean completeTask(final ConnectingTask task) {
        // A quick sanity check
        if (task.equals(currentTask)) {
            currentTask = null;
            return false;
        }
        LOG.warn("Ignoring connection completion, expected {} actual {}", currentTask, task);
        return true;
    }

    @Override
    protected synchronized void removeRegistration() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }

        communicator.close();
        delegate.close();
        yanglibRegistrations.forEach(SchemaSourceRegistration::close);
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        synchronized (this) {
            attempts = 0;
        }
        delegate.onDeviceConnected(deviceSchema, sessionPreferences, services);
    }

    @Override
    public void onDeviceDisconnected() {
        delegate.onDeviceDisconnected();
        reconnectOrFail();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        // We have not reported onDeviceConnected(), so from the view of delete we are still connecting
        LOG.debug("Connection attempt failed", throwable);
        reconnectOrFail();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    private void reconnectOrFail() {
        final var ex = scheduleReconnect();
        if (ex != null) {
            delegate.onDeviceFailed(ex);
        }
    }

    private synchronized Exception scheduleReconnect() {
        if (isClosed()) {
            return null;
        }

        final long delayMillis;

        // We have exceeded the number of connection attempts
        if (maxAttempts > 0 && attempts >= maxAttempts) {
            LOG.info("Failed to connect {} after {} attempts, not attempting", deviceId, attempts);
            return new ConnectGivenUpException("Given up connecting " + deviceId + " after " + attempts + " attempts");
        }

        // First connection attempt gets initialized to minimum sleep, each subsequent is exponentially backed off
        // by sleepFactor.
        if (attempts != 0) {
            final long nextSleep = (long) (lastSleep * sleepFactor);
            // check for overflow
            delayMillis = nextSleep >= 0 ? nextSleep : Long.MAX_VALUE;
        } else {
            delayMillis = minSleep;
        }

        attempts++;
        lastSleep = delayMillis;
        LOG.debug("Retrying {} connection attempt {} after {} milliseconds", deviceId, attempts, delayMillis);

        // Schedule a task for the right time. We always go through the executor to eliminate the special case of
        // immediate reconnect. While we could check and got to lockedConnect(), it makes for a rare special case.
        // That special case makes for more code paths to test and introduces additional uncertainty as to whether
        // the attempt was executed on this thread or not.
        currentTask = new SleepingTask(timer.newTimeout(this::reconnect, delayMillis, TimeUnit.MILLISECONDS));
        return null;
    }

    private synchronized void reconnect(final Timeout timeout) {
        currentTask = null;
        if (notClosed()) {
            lockedConnect();
        }
    }

    private static List<SchemaSourceRegistration<?>> registerDeviceSchemaSources(final RemoteDeviceId remoteDeviceId,
            final NetconfNode node, final SchemaResourcesDTO resources) {
        final var yangLibrary = node.getYangLibrary();
        if (yangLibrary != null) {
            final Uri uri = yangLibrary.getYangLibraryUrl();
            if (uri != null) {
                final var registrations = new ArrayList<SchemaSourceRegistration<?>>();
                final var yangLibURL = uri.getValue();
                final var schemaRegistry = resources.getSchemaRegistry();

                // pre register yang library sources as fallback schemas to schema registry
                final var yangLibUsername = yangLibrary.getUsername();
                final var yangLigPassword = yangLibrary.getPassword();
                final var schemas = yangLibUsername != null && yangLigPassword != null
                    ? LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword)
                        : LibraryModulesSchemas.create(yangLibURL);

                for (var entry : schemas.getAvailableModels().entrySet()) {
                    registrations.add(schemaRegistry.registerSchemaSource(new LibrarySchemaSourceProvider(
                        remoteDeviceId, schemas.getAvailableModels()),
                        PotentialSchemaSource.create(entry.getKey(), YangTextSchemaSource.class,
                            PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
                return List.copyOf(registrations);
            }
        }

        return List.of();
    }

    @VisibleForTesting
    synchronized long attempts() {
        return attempts;
    }
}