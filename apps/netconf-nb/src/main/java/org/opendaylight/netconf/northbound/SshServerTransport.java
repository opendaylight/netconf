/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.server.ServerTransportInitializer;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev231228.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NETCONF server for MD-SAL (listening by default on port 2830).
 */
@Component(service = { }, configurationPid = "org.opendaylight.netconf.ssh")
@Designate(ocd = SshServerTransport.Configuration.class)
public final class SshServerTransport implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";
        // NOTE: default is not TransportConstants.SSH_TCP_PORT to allow unprivileged execution
        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 2830;
    }

    private static final Logger LOG = LoggerFactory.getLogger(SshServerTransport.class);

    private final SSHServer sshServer;

    @Activate
    public SshServerTransport(@Reference final TransportFactoryHolder factoryHolder,
            @Reference final OSGiNetconfServer backend,
            @Reference(target = "(type=netconf-auth-provider)") final AuthProvider authProvider,
            final Configuration configuration) {
        this(factoryHolder, backend.serverTransportInitializer(), authProvider, new TcpServerParametersBuilder()
            .setLocalAddress(IetfInetUtil.ipAddressFor(configuration.bindingAddress()))
            .setLocalPort(new PortNumber(Uint16.valueOf(configuration.portNumber())))
            .build());
    }

    public SshServerTransport(final TransportFactoryHolder factoryHolder, final ServerTransportInitializer initializer,
            final AuthProvider authProvider, final TcpServerGrouping listenParams) {
        final var localAddr = listenParams.requireLocalAddress().stringValue();
        final var localPort = listenParams.requireLocalPort().getValue();

        try {
            sshServer = factoryHolder.factory().listenServer(TransportConstants.SSH_SUBSYSTEM, initializer,
                listenParams, null, factoryMgr -> {
                    factoryMgr.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE));
                    factoryMgr.setPasswordAuthenticator(
                        (username, password, session) -> authProvider.authenticated(username, password));
                    factoryMgr.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
                })
                .get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            LOG.warn("Could not start SSH NETCONF server at {}:{}", localAddr, localPort, e);
            throw new IllegalStateException("Unable to start SSH netconf server", e);
        }

        LOG.info("SSH NETCONF server at {}:{} started", localAddr, localPort);
    }

    @Deactivate
    @Override
    public void close() throws IOException {
        try {
            sshServer.shutdown().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Could not stop SSH NETCONF server {}", sshServer, e);
        }
    }
}
