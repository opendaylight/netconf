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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.netty.util.Timeout;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHNegotiatedAlgListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParameters;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All state associated with a NETCONF topology node to which we connect. Each node handles its own reconnection,
 * with backoff between attempts as configured for the node.
 */
public final class NetconfNodeHandler extends AbstractNodeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeHandler.class);

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

    private final @NonNull NetconfClientFactory clientFactory;
    private final @NonNull NetconfClientConfigurationBuilderFactory builderFactory;
    private final @NonNull SshTransportTopologyParameters sshParams;
    private final @NonNull NetconfTimer timer;

    // SSH algorithms known up-front for call-home connections (null for regular connections); used as the per-attempt
    // baseline for negotiatedSshAlg.
    private final @Nullable NegotiatedSshAlg callHomeSshAlg;

    private final long maxBackoff;
    private final long maxAttempts;
    private final int minBackoff;
    private final double backoffMultiplier;
    private final double jitter;

    @GuardedBy("this")
    private long attempts;
    @GuardedBy("this")
    private long lastMultipliedBackoff;
    @GuardedBy("this")
    private Task currentTask;

    @GuardedBy("this")
    private NetconfClientConfiguration clientConfig;

    public NetconfNodeHandler(final NetconfClientFactory clientFactory, final NetconfTimer timer,
            final BaseNetconfSchemaProvider baseSchemaProvider, final SchemaResourceManager schemaManager,
            final NetconfTopologySchemaAssembler schemaAssembler,
            final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceHandler delegate,
            final RemoteDeviceId deviceId, final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional, final SshTransportTopologyParameters sshParams,
            final @Nullable NegotiatedSshAlg callHomeSshAlg) {
        super(timer, baseSchemaProvider, schemaManager, schemaAssembler,
            deviceActionFactory, delegate, deviceId, nodeId, node, nodeOptional);
        this.clientFactory = requireNonNull(clientFactory);
        this.builderFactory = requireNonNull(builderFactory);
        this.sshParams = requireNonNull(sshParams);
        this.timer = requireNonNull(timer);
        this.callHomeSshAlg = callHomeSshAlg;

        maxAttempts = node.requireMaxConnectionAttempts().toJava();
        minBackoff = node.requireMinBackoffMillis().toJava();
        backoffMultiplier = node.requireBackoffMultiplier().doubleValue();
        final long potentialMaxBackoff = node.requireMaxBackoffMillis().toJava();
        maxBackoff = potentialMaxBackoff >= minBackoff ? potentialMaxBackoff : minBackoff;
        jitter = node.getBackoffJitter().doubleValue();
    }

    @Override
    public synchronized void connect() {
        attempts = 1;
        lastMultipliedBackoff = minBackoff;
        lockedConnect();
    }

    @GuardedBy("this")
    private void lockedConnect() {
        // Reset to the per-attempt baseline; the SSHNegotiatedAlgListener will refill it during key establishment for
        // regular SSH connections.
        setNegotiatedSshAlg(callHomeSshAlg);
        if (clientConfig == null) {
            try {
                clientConfig = builderFactory.createClientConfigurationBuilder(nodeId(), netconfNode(), sshParams)
                    .withSessionListener(communicator())
                    .build();
            } catch (IllegalArgumentException | IllegalStateException e) {
                LOG.warn("RemoteDevice{{}} failed to connect", nodeId(), e);
                delegate().onDeviceFailed(e);
                return;
            }
        }
        final var algListener = new SSHNegotiatedAlgListener() {
            @Override
            public void onAlgorithmsNegotiated(final SshKeyExchangeAlgorithm kexAlgorithm,
                    final SshPublicKeyAlgorithm hostKey, final SshEncryptionAlgorithm encryption,
                    final SshMacAlgorithm mac) {
                setNegotiatedSshAlg(new NegotiatedSshAlg(kexAlgorithm, hostKey, encryption, mac));
            }
        };
        final ListenableFuture<NetconfClientSession> connectFuture;
        try {
            connectFuture = clientFactory.createClient(clientConfig, algListener);
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
            LOG.debug("Connection attempt {} to {} failed", attempts, deviceId(), cause);
        }

        // We are invoking callbacks, do not hold locks
        reconnectOrFail();
    }

    @GuardedBy("this")
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
    synchronized void closeSession() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services,
            final @Nullable NegotiatedSshAlg ignoredSshAlg) {
        synchronized (this) {
            attempts = 0;
        }
        super.onDeviceConnected(deviceSchema, sessionPreferences, services, ignoredSshAlg);
    }

    @Override
    public void onDeviceDisconnected() {
        if (isClosed()) {
            return;
        }
        delegate().onDeviceDisconnected();
        reconnectOrFail();
    }


    @Override
    public void onDeviceFailed(final Throwable throwable) {
        // We have not reported onDeviceConnected(), so from the view of delete we are still connecting
        LOG.debug("Connection attempt failed", throwable);
        reconnectOrFail();
    }

    private void reconnectOrFail() {
        final var ex = scheduleReconnect();
        if (ex != null) {
            delegate().onDeviceFailed(ex);
        }
    }

    private synchronized Exception scheduleReconnect() {
        if (isClosed()) {
            return null;
        }

        // We have exceeded the number of connection attempts
        if (maxAttempts > 0 && attempts >= maxAttempts) {
            LOG.info("Failed to connect {} after {} attempts, not attempting", deviceId(), attempts);
            return new ConnectGivenUpException("Given up connecting %s after %d attempts"
                .formatted(deviceId(), attempts));
        }

        // First connection attempt gets initialized to minimum backoff, each subsequent is exponentially backed off
        // by backoffMultiplier (default 1.5) until reach max sleep and randomized by +/- jitter (default 0.1).
        if (attempts != 0) {
            lastMultipliedBackoff = (long) Math.min(lastMultipliedBackoff * backoffMultiplier, maxBackoff);
        } else {
            lastMultipliedBackoff = minBackoff;
        }
        final var backoffMillis = (long) (lastMultipliedBackoff * (Math.random() * (jitter * 2) + (1 - jitter)));
        attempts++;
        LOG.debug("Retrying {} connection attempt {} after {} milliseconds", deviceId(), attempts, backoffMillis);

        // Schedule a task for the right time. We always go through the executor to eliminate the special case of
        // immediate reconnect. While we could check and got to lockedConnect(), it makes for a rare special case.
        // That special case makes for more code paths to test and introduces additional uncertainty as to whether
        // the attempt was executed on this thread or not.
        currentTask = new SleepingTask(timer.newTimeout(this::reconnect, backoffMillis, TimeUnit.MILLISECONDS));
        return null;
    }

    private synchronized void reconnect(final Timeout timeout) {
        currentTask = null;
        if (notClosed()) {
            lockedConnect();
        }
    }

    @VisibleForTesting
    synchronized long attempts() {
        return attempts;
    }
}
