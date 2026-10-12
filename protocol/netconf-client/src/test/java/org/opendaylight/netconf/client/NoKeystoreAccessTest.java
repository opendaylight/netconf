/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHNegotiatedAlgListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.initiate.stack.grouping.transport.tls.tls.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PublicKeyBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * {@link NetconfClientFactoryImpl} created without a {@link KeystoreAccess}, as OSGi does when there is no
 * {@link KeystoreAccess} service.
 */
@ExtendWith(MockitoExtension.class)
class NoKeystoreAccessTest {
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private SSHNegotiatedAlgListener algListener;

    @Test
    void centralKeystoreKeyIsRejected() {
        final var clientConfig = NetconfClientConfigurationBuilder.create()
            .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
            .withTcpParameters(new TcpClientParametersBuilder()
                .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress())))
                .setRemotePort(new PortNumber(Uint16.valueOf(830)))
                .build())
            .withSshParameters(new SshClientParametersBuilder()
                .setClientIdentity(new ClientIdentityBuilder()
                    .setUsername("username")
                    .setPublicKey(new PublicKeyBuilder()
                        .setInlineOrKeystore(new CentralKeystoreBuilder().setCentralKeystoreReference("central-key")
                            .build())
                        .build())
                    .build())
                .build())
            .withSessionListener(sessionListener)
            .build();

        try (var timer = new DefaultNetconfTimer();
             var factory = new NetconfClientFactoryImpl(timer, (KeystoreAccess) null)) {
            // the key is resolved before connecting, so no server is needed
            final var ex = assertThrows(UnsupportedConfigurationException.class,
                () -> factory.createClient(clientConfig, algListener));
            assertEquals("Unsupported configuration " + CentralKeystore.class.getCanonicalName(), ex.getMessage());
        }
    }
}
