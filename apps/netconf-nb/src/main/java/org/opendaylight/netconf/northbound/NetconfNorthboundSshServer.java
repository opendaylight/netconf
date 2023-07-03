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
import java.util.function.Consumer;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
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
@Designate(ocd = NetconfNorthboundSshServer.Configuration.class)
public final class NetconfNorthboundSshServer implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";
        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 2830;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNorthboundSshServer.class);

    private final SSHServer sshServer;

    @Activate
    public NetconfNorthboundSshServer(
            @Reference final NetconfServerFactory netconfServerFactory,
            @Reference(target = "(type=netconf-auth-provider)") final AuthProvider authProvider,
            final Configuration configuration) {
        this(netconfServerFactory, authProvider, configuration.bindingAddress(), configuration.portNumber());
    }

    public NetconfNorthboundSshServer(final NetconfServerFactory netconfServerFactory,
            final AuthProvider authProvider, final String bindingAddress, final int portNumber) {

        final TcpServerParameters tcpConfig = new TcpServerParametersBuilder()
            .setLocalAddress(IetfInetUtil.ipAddressFor(bindingAddress))
            .setLocalPort(new PortNumber(Uint16.valueOf(portNumber))).build();

        final Consumer<ServerFactoryManager> serverInitializer = factoryMgr -> {
            factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
            factoryMgr.setPasswordAuthenticator(
                (username, password, session) -> authProvider.authenticated(username, password));
            factoryMgr.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        };

        try {
            sshServer = netconfServerFactory.createSshServer(tcpConfig, null, serverInitializer).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            LOG.warn("Could not start SSH netconf server at {}", bindingAddress, e);
            throw new IllegalStateException("Unable to start SSH netconf server", e);
        }
    }

    @Deactivate
    @Override
    public void close() throws IOException {
        try {
            sshServer.shutdown().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Could not stop SSH netconf server", e);
        }
    }
}
