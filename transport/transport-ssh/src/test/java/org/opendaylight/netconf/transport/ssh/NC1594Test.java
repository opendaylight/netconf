/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPassword;

import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.kex.KexProposalOption;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

@ExtendWith(MockitoExtension.class)
class NC1594Test {
    private static final SshKeyExchangeAlgorithm KEX_SNT = new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn
        .ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Sntrup761x25519Sha512);
    private static final SshKeyExchangeAlgorithm KEX_DIF = new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn
        .ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016
        .SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha256);
    private static final SshPublicKeyAlgorithm HOST_KEY_RSA = new SshPublicKeyAlgorithm(org.opendaylight.yang.gen.v1.urn
        .ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.RsaSha2512);
    private static final SshPublicKeyAlgorithm HOST_KEY_SSH = new SshPublicKeyAlgorithm(org.opendaylight.yang.gen.v1.urn
        .ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519);
    private static final SshEncryptionAlgorithm ENCRYPTION_CHA = new SshEncryptionAlgorithm(org.opendaylight.yang.gen.v1
        .urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Chacha20Poly1305);
    private static final SshEncryptionAlgorithm ENCRYPTION_AES = new SshEncryptionAlgorithm(org.opendaylight.yang.gen.v1
        .urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc);
    private static final SshMacAlgorithm MAC_NONE = new SshMacAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf
        .params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.None);
    private static final SshMacAlgorithm MAC_HMAC = new SshMacAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf
        .params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256);
    private static final String USER = "user";

    private final EventLoopGroup group = org.opendaylight.netconf.transport.tcp.NettyTransportSupport
        .newEventLoopGroup("SessionFailure", 0);
    private final NettyIoServiceFactoryFactory serviceFactory = new NettyIoServiceFactoryFactory(group);

    private SSHClient sshClient;

    @Mock
    private TransportSshClient transportSsh;
    @Mock
    private TransportClientSession transportClientSession;
    @Mock
    private IoSession ioSession;
    @Mock
    private AuthFuture auth;
    @Mock
    private TransportChannelListener<TransportChannel> clientListener;
    @Mock
    private SSHNegotiatedAlgListener algListener;

    @BeforeEach
    void beforeEach() throws Exception {
        final var clientIdentity = buildClientIdentityWithPassword(USER, AbstractClientServerTest.PASSWORD);
        transportSsh = new TransportSshClient.Builder(serviceFactory, group)
            .clientIdentity(clientIdentity)
            .buildChecked();
        doReturn(ioSession).when(transportClientSession).getIoSession();
        doReturn(auth).when(transportClientSession).auth();

        sshClient = SSHClient.of(AbstractClientServerTest.SUBSYSTEM, clientListener, algListener, transportSsh);
    }

    @Test
    void testValidAlgorithms() throws Exception {
        doReturn("aes256-cbc").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("ssh-ed25519").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("hmac-sha2-256").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);
        doReturn("diffie-hellman-group-exchange-sha256").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        sshClient.onKeyEstablished(transportClientSession);

        verify(algListener).onAlgorithmsNegotiated(KEX_DIF, HOST_KEY_SSH, ENCRYPTION_AES, MAC_HMAC);
    }

    @Test
    void testInvalidAlgorithms() throws Exception {
        doReturn("Test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));

        doReturn("sntrup761x25519-sha512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn("Test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));

        doReturn("sntrup761x25519-sha512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn("rsa-sha2-512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));

        doReturn("sntrup761x25519-sha512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn("rsa-sha2-512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("chacha20-poly1305@openssh.com").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));
    }

    @Test
    void testAeadMac() throws Exception {
        doReturn("chacha20-poly1305@openssh.com").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("rsa-sha2-512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("aead").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);
        doReturn("sntrup761x25519-sha512").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        sshClient.onKeyEstablished(transportClientSession);

        verify(algListener).onAlgorithmsNegotiated(KEX_SNT, HOST_KEY_RSA, ENCRYPTION_CHA, MAC_NONE);
    }
}
