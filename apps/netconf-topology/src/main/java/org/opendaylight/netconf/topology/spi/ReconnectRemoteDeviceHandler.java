/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RemoteDeviceHandler} which handles the task of re-establishing remote connection on failure.
 */
final class ReconnectRemoteDeviceHandler implements RemoteDeviceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectRemoteDeviceHandler.class);

    private final @NonNull AbstractNetconfTopology topology;
    private final @NonNull RemoteDeviceHandler delegate;
    private final @NonNull RemoteDeviceId deviceId;
    private final long maxAttempts;
    private final int minSleep;
    private final double sleepFactor;

    @GuardedBy("this")
    private long attempts;
    @GuardedBy("this")
    private long lastSleep;

    ReconnectRemoteDeviceHandler(final AbstractNetconfTopology topology, final RemoteDeviceId deviceId,
            final RemoteDeviceHandler delegate, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        this.topology = requireNonNull(topology);
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
    }

    @Override
    public synchronized void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        attempts = 0;
    }

    @Override
    public void onDeviceDisconnected() {
        delegate.onDeviceDisconnected();
        scheduleReconnect();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        LOG.debug("Connection attempt failed", throwable);
        delegate.onDeviceFailed(throwable);
        scheduleReconnect();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    @Override
    public synchronized void close() {
        // TODO Auto-generated method stub

    }

    private synchronized void scheduleReconnect() {
        final long delayMillis;

        synchronized (this) {
            // We have exceeded the number of connection attempts
            if (maxAttempts > 0 && attempts >= maxAttempts) {
                LOG.info("Failed to connect {} after {} attempts, not attempting", deviceId, attempts);
                return;
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
        }

        // If we are not sleeping at all, return an already-succeeded future
        if (lastSleep == 0) {
            return executor.newSucceededFuture(null);
        }

        // Schedule a task for the right time. It will also clear the flag.
        return executor.schedule(() -> {
            synchronized (TimedReconnectStrategy.this) {
                checkState(TimedReconnectStrategy.this.scheduled);
                TimedReconnectStrategy.this.scheduled = false;
            }

            return null;
        }, lastSleep, TimeUnit.MILLISECONDS);
    }
}
