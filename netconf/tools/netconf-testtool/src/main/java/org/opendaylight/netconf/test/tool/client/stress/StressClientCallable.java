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
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StressClientCallable implements Callable<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(StressClientCallable.class);

    private final Parameters params;
    private final NetconfDeviceCommunicator sessionListener;
    private final NetconfClientDispatcherImpl netconfClientDispatcher;
    private final NetconfClientConfiguration cfg;
    private final NetconfClientSession netconfClientSession;
    private final ExecutionStrategy executionStrategy;

    public StressClientCallable(final Parameters params,
                                final NetconfClientDispatcherImpl netconfClientDispatcher,
                                final List<NetconfMessage> preparedMessages) {
        this.params = params;
        sessionListener = getSessionListener(params.getInetAddress(), params.concurrentMessageLimit);
        this.netconfClientDispatcher = netconfClientDispatcher;
        cfg = getNetconfClientConfiguration(this.params, sessionListener);

        LOG.info("Connecting to netconf server {}:{}", params.ip, params.port);
        try {
            netconfClientSession = netconfClientDispatcher.createClient(cfg).get();
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
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

    private static NetconfDeviceCommunicator getSessionListener(
            final InetSocketAddress inetAddress, final int messageLimit) {
        final RemoteDevice<NetconfDeviceCommunicator> loggingRemoteDevice = new StressClient.LoggingRemoteDevice();
        return new NetconfDeviceCommunicator(
            new RemoteDeviceId("secure-test", inetAddress), loggingRemoteDevice, messageLimit);
    }

    private static NetconfClientConfiguration getNetconfClientConfiguration(final Parameters params,
            final NetconfDeviceCommunicator sessionListener) {
        final var netconfClientConfigurationBuilder = NetconfClientConfigurationBuilder.create()
            .withSessionListener(sessionListener)
            .withAddress(params.getInetAddress())
            .withProtocol(params.ssh ? NetconfClientConfiguration.NetconfClientProtocol.SSH
                : NetconfClientConfiguration.NetconfClientProtocol.TCP)
            .withAuthHandler(new LoginPasswordHandler(params.username, params.password))
            .withConnectionTimeoutMillis(20000L);

        if (params.tcpHeader != null) {
            final String header = params.tcpHeader.replace("\"", "").trim() + "\n";
            netconfClientConfigurationBuilder.withAdditionalHeader(
                new NetconfHelloMessageAdditionalHeader(null, null, null, null, null) {
                    @Override
                    public String toFormattedString() {
                        LOG.debug("Sending TCP header {}", header);
                        return header;
                    }
                });
        }
        return netconfClientConfigurationBuilder.build();
    }
}
