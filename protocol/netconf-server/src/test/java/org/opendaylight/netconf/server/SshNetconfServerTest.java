/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.SshServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.UsersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.server.identity.HostKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.server.identity.host.key.host.key.type.PublicKeyBuilder;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

@ExtendWith(MockitoExtension.class)
class SshNetconfServerTest extends AbstractNetconfServerTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0rd";
    private static final String RSA = "RSA";

    @Test
    void sshServer() throws Exception {
        assertSshServer(bootstrapFactory.listenServer(TransportConstants.SSH_SUBSYSTEM, initializer, tcpServerParams,
            new SshServerParametersBuilder()
                .setServerIdentity(buildSshServerIdentityWithKeyPair())
                .setClientAuthentication(new ClientAuthenticationBuilder()
                    .setUsers(new UsersBuilder()
                        .setUser(BindingMap.of(new UserBuilder()
                            .setName(USERNAME)
                            .setPassword(new CryptHash("$0$" + PASSWORD))
                            .build()))
                        .build())
                    .build())
                .build()));
    }

    @Test
    void sshServerExtInitializer() throws Exception {
        assertSshServer(bootstrapFactory.listenServer(TransportConstants.SSH_SUBSYSTEM, initializer, tcpServerParams,
            null, factoryManager -> {
                factoryManager.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
                factoryManager.setPasswordAuthenticator((username, password, session) -> true);
                factoryManager.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
            }));
    }

    private void assertSshServer(final ListenableFuture<SSHServer> serverFuture) throws Exception {
        final var server = serverFuture.get(2, TimeUnit.SECONDS);
        try {
            final var client = bootstrapFactory.connectClient("netconf", clientListener, tcpClientParams,
                sshClientParams()).get(2, TimeUnit.SECONDS);
            try {
                // FIXME commented line requires netconf client to trigger netconf subsystem initialization on server
                // verify(serverChannelInitializer, timeout(10_000L)).initialize(any(Channel.class), any());
                verify(clientListener, timeout(10_000L)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static ServerIdentity buildSshServerIdentityWithKeyPair() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance(RSA).generateKeyPair();
        return new ServerIdentityBuilder()
            .setHostKey(List.of(new HostKeyBuilder()
                .setName("test-name")
                .setHostKeyType(new PublicKeyBuilder()
                    .setPublicKey(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417
                        .ssh.server.grouping.server.identity.host.key.host.key.type._public.key.PublicKeyBuilder()
                        .setInlineOrKeystore(new InlineBuilder()
                            .setInlineDefinition(new InlineDefinitionBuilder()
                                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                                .setPublicKey(keyPair.getPublic().getEncoded())
                                .setPrivateKeyFormat(RsaPrivateKeyFormat.VALUE)
                                .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                                    .setCleartextPrivateKey(keyPair.getPrivate().getEncoded())
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build()))
            .build();
    }

    private static SshClientParameters sshClientParams() {
        return new SshClientParametersBuilder()
            .setClientIdentity(new ClientIdentityBuilder()
                .setUsername(USERNAME)
                .setPassword(new PasswordBuilder()
                    .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(PASSWORD).build())
                    .build())
                .build())
            .build();
    }
}
