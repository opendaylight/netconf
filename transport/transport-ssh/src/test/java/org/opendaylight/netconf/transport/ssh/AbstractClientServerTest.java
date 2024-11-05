/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.PasswordIdentityProvider;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
abstract class AbstractClientServerTest {
    static final String RSA = "RSA";
    static final String USER = "user";
    static final String PASSWORD = "pa$$w0rd";
    static final String SUBSYSTEM = "subsystem";
    static final AtomicInteger COUNTER = new AtomicInteger(0);
    static final AtomicReference<String> USERNAME = new AtomicReference<>(USER);

    static SSHTransportStackFactory FACTORY;

    @Mock
    TcpClientGrouping tcpClientConfig;
    @Mock
    TcpServerGrouping tcpServerConfig;
    @Mock
    SshClientGrouping sshClientConfig;
    @Mock
    TransportChannelListener<TransportChannel> clientListener;
    @Mock
    SshServerGrouping sshServerConfig;
    @Mock
    TransportChannelListener<TransportChannel> serverListener;

    private ServerSocket socket;

    @BeforeAll
    static void beforeAll() {
        FACTORY = new SSHTransportStackFactory("IntegrationTest", 0);
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
    }

    @BeforeEach
    void beforeEach() throws IOException {
        // create temp socket to get available port for test
        socket = new ServerSocket(0);
        final var localAddress = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());
        final var localPort = new PortNumber(Uint16.valueOf(socket.getLocalPort()));
        socket.close();

        final var localBind = new LocalBindBuilder()
            .setLocalAddress(localAddress)
            .setLocalPort(localPort)
            .build();

        when(tcpServerConfig.getLocalBind()).thenReturn(BindingMap.of(localBind));
        when(tcpServerConfig.nonnullLocalBind()).thenCallRealMethod();

        when(tcpClientConfig.getRemoteAddress()).thenReturn(new Host(localAddress));
        when(tcpClientConfig.requireRemoteAddress()).thenCallRealMethod();
        when(tcpClientConfig.getRemotePort()).thenReturn(localPort);
        when(tcpClientConfig.requireRemotePort()).thenCallRealMethod();
    }

    static String getUsername() {
        return USERNAME.get();
    }

    /**
     * Update username for next test.
     */
    static String getUsernameAndUpdate() {
        return USERNAME.getAndSet(USER + COUNTER.incrementAndGet());
    }

    static ClientFactoryManagerConfigurator clientConfigurator(final String username) {
        return new ClientFactoryManagerConfigurator() {
            @Override
            protected void configureClientFactoryManager(final ClientFactoryManager factoryManager)
                throws UnsupportedConfigurationException {
                factoryManager.setPasswordIdentityProvider(PasswordIdentityProvider.wrapPasswords(PASSWORD));
                factoryManager.setUserAuthFactories(List.of(
                    new org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory()));
            }
        };
    }
}
