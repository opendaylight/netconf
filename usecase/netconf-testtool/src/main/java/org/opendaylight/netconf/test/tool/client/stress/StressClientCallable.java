/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.stress;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StressClientCallable implements Callable<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(StressClientCallable.class);

    private final NetconfDeviceCommunicator sessionListener;
    private final NetconfClientSession netconfClientSession;
    private final ExecutionStrategy executionStrategy;

    public StressClientCallable(final Parameters params,
                                final NetconfClientFactory netconfClientFactory,
                                final NetconfClientConfiguration baseConfiguration,
                                final List<NetconfMessage> preparedMessages) {
        sessionListener = getSessionListener(params.getInetAddress(), params.concurrentMessageLimit);
        final var cfg = getNetconfClientConfiguration(baseConfiguration, sessionListener);

        LOG.info("Connecting to netconf server {}:{}", params.ip, params.port);
        try {
            netconfClientSession = netconfClientFactory.createClient(cfg).get();
        } catch (final InterruptedException | ExecutionException | UnsupportedConfigurationException e) {
            throw new IllegalStateException("Unable to connect", e);
        }
        executionStrategy = getExecutionStrategy(params, preparedMessages, sessionListener);
    }

    @Override
    public Boolean call() {
        executionStrategy.invoke();
        netconfClientSession.close();
        return Boolean.TRUE;
    }

    private static ExecutionStrategy getExecutionStrategy(final Parameters params,
            final List<NetconfMessage> preparedMessages, final NetconfDeviceCommunicator sessionListener) {
        if (params.async) {
            return new AsyncExecutionStrategy(params, preparedMessages, sessionListener);
        } else {
            return new SyncExecutionStrategy(params, preparedMessages, sessionListener);
        }
    }

    private static NetconfDeviceCommunicator getSessionListener(final InetSocketAddress inetAddress,
            final int messageLimit) {
        return new NetconfDeviceCommunicator(new RemoteDeviceId("secure-test", inetAddress),
            StressClient.LOGGING_REMOTE_DEVICE, messageLimit);
    }

    private static NetconfClientConfiguration getNetconfClientConfiguration(final NetconfClientConfiguration base,
            final NetconfDeviceCommunicator sessionListener) {
        return NetconfClientConfigurationBuilder.create()
            .withProtocol(base.getProtocol())
            .withTcpParameters(base.getTcpParameters())
            .withSshParameters(base.getSshParameters())
            .withOdlHelloCapabilities(base.getOdlHelloCapabilities())
            .withAdditionalHeader(base.getAdditionalHeader().orElse(null))
            .withConnectionTimeoutMillis(base.getConnectionTimeoutMillis())
            .withSessionListener(sessionListener).build();
    }
}
