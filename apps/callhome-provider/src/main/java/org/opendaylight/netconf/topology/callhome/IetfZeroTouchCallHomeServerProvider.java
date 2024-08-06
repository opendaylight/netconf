/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
public final class IetfZeroTouchCallHomeServerProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);
    private final CallHomeSshServer server;

    @Activate
    @Inject
    public IetfZeroTouchCallHomeServerProvider(
            final @Reference NetconfTimer timer,
            final @Reference CallHomeMountService mountService,
            final @Reference CallHomeSshAuthProvider authProvider,
            final @Reference CallHomeStatusRecorder statusRecorder,
            final CallHomeMountService.Configuration configuration) {

        LOG.info("Starting Call-Home SSH server at {}:{}", configuration.host(), configuration.ssh$_$port());

        try {
            server = CallHomeSshServer.builder()
                .withAddress(InetAddress.getByName(configuration.host()))
                .withPort(configuration.ssh$_$port())
                .withAuthProvider(authProvider)
                .withStatusRecorder(statusRecorder)
                .withTimeout(configuration.connection$_$timeout$_$millis())
                .withSessionContextManager(mountService.createSshSessionContextManager())
                .withNegotiationFactory(new NetconfClientSessionNegotiatorFactory(timer,
                    configuration.connection$_$timeout$_$millis(),
                    NetconfClientSessionNegotiatorFactory.DEFAULT_CLIENT_CAPABILITIES))
                .build();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid address", e);
        }

        LOG.info("Call-Home SSH server started successfully");
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        if (server != null) {
            server.close();
        }
        LOG.info("Call-Home SSH server stopped.");
    }
}
