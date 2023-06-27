/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.common.NetconfTimer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { }, configurationPid = "org.opendaylight.netconf.topology.callhome")
@Singleton
public class NetconfCallHomeTlsService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private final CallHomeTlsServer server;

    @Activate
    @Inject
    public NetconfCallHomeTlsService(
            final @Reference NetconfTimer timer,
            final @Reference CallHomeMountService mountService,
            final @Reference CallHomeTlsAuthProvider authProvider,
            final @Reference CallHomeStatusRecorder statusRecorder,
            final CallHomeMountService.Configuration configuration) {

        LOG.info("Starting Call-Home TLS server at {}:{}", configuration.host(), configuration.tls$_$Port());
        try {
            server = CallHomeTlsServer.builder()
                .withAddress(InetAddress.getByName(configuration.host()))
                .withPort(configuration.tls$_$Port())
                .withTimeout(configuration.connection$_$Timeout$_$Millis())
                .withMaxConnections(configuration.max$_$Connections())
                .withAuthProvider(authProvider)
                .withStatusRecorder(statusRecorder)
                .withSessionContextManager(
                    mountService.createTlsSessionContextManager(authProvider, statusRecorder))
                .withNegotiationFactory(new NetconfClientSessionNegotiatorFactory(timer, Optional.empty(),
                    configuration.connection$_$Timeout$_$Millis(),
                    NetconfClientSessionNegotiatorFactory.DEFAULT_CLIENT_CAPABILITIES))
                .build();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("invalid host", e);
        }
        LOG.info("Call-Home TLS server started successfully");
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        server.close();
        LOG.info("Call-Home TLS server stopped");
    }
}