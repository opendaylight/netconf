/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshAuthProvider;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshServer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { }, configurationPid = "org.opendaylight.netconf.callhome.mount.ssh.server")
@Designate(ocd = IetfZeroTouchCallHomeServerProvider.Configuration.class)
@Singleton
public final class IetfZeroTouchCallHomeServerProvider implements AutoCloseable {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String host() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int port() default 4334;
    }

    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);
    private final CallHomeSshServer server;

    @Activate
    @Inject
    public IetfZeroTouchCallHomeServerProvider(
            final @Reference CallHomeMountService mountService,
            final @Reference CallHomeSshAuthProvider authProvider,
            final @Reference CallHomeStatusRecorder statusRecorder,
            final Configuration configuration) {

        LOG.info("Starting Call-Home SSH server at {}:{}", configuration.host(), configuration.port());

        try {
            server = CallHomeSshServer.builder()
                .withAddress(InetAddress.getByName(configuration.host()))
                .withPort(configuration.port())
                .withAuthProvider(authProvider)
                .withStatusRecorder(statusRecorder)
                .withSessionContextManager(mountService.createSshSessionContextManager())
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
