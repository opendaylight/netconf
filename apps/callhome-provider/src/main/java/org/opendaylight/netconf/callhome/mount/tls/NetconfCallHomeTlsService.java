/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.callhome.mount.CallHomeMountService;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsAuthProvider;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsServer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { }, configurationPid = "org.opendaylight.netconf.callhome.mount.tls.server")
@Designate(ocd = NetconfCallHomeTlsService.Configuration.class)
@Singleton
public class NetconfCallHomeTlsService implements AutoCloseable {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String host() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int port() default 4335;

        @AttributeDefinition
        int timeoutMillis() default 10_000;

        @AttributeDefinition
        int maxConnections() default 64;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private final CallHomeTlsServer server;

    @Activate
    @Inject
    public NetconfCallHomeTlsService(
            final @Reference CallHomeMountService mountService,
            final @Reference CallHomeTlsAuthProvider authProvider,
            final @Reference CallHomeStatusRecorder statusRecorder,
            final Configuration configuration) {

        LOG.info("Starting Call-Home TLS server at {}:{}", configuration.host(), configuration.port());
        try {
            server = CallHomeTlsServer.builder()
                .withAddress(InetAddress.getByName(configuration.host()))
                .withPort(configuration.port())
                .withTimeout(configuration.timeoutMillis())
                .withMaxConnections(configuration.maxConnections())
                .withAuthProvider(authProvider)
                .withStatusRecorder(statusRecorder)
                .withSessionContextManager(
                    mountService.createTlsSessionContextManager(authProvider, statusRecorder))
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