/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
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
    private final @NonNull BigDecimal sleepFactor;
    private final long maxConnectionAttempts;
    private final int millisBetweenAttempts;

    ReconnectRemoteDeviceHandler(final AbstractNetconfTopology topology, final RemoteDeviceId deviceId,
            final RemoteDeviceHandler delegate, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        this.topology = requireNonNull(topology);
        this.delegate = requireNonNull(delegate);
        this.deviceId = requireNonNull(deviceId);

        maxConnectionAttempts = node.requireMaxConnectionAttempts().toJava();
        millisBetweenAttempts = node.requireBetweenAttemptsTimeoutMillis().toJava();
        sleepFactor = node.requireSleepFactor().decimalValue();

        // Setup reconnection on empty context, if so configured
        // FIXME: NETCONF-925: implement this
        if (nodeOptional != null && nodeOptional.getIgnoreMissingSchemaSources().getAllowed()) {
            LOG.warn("Ignoring missing schema sources is not currently implemented for {}", deviceId);
        }
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {

        // TODO Auto-generated method stub

    }

    @Override
    public void onDeviceDisconnected() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
