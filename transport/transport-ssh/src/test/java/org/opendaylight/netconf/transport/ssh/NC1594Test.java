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
import static org.opendaylight.netconf.transport.tcp.NettyTransportSupport.newEventLoopGroup;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.RsaSha2512;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm.SshEd25519;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Aes256Cbc;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.Chacha20Poly1305;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Sntrup761x25519Sha512;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.HmacSha2256;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm.None;

import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KexProposalOption;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;

@ExtendWith(MockitoExtension.class)
class NC1594Test {
    private static final SshKeyExchangeAlgorithm KEX_SNT = new SshKeyExchangeAlgorithm(Sntrup761x25519Sha512);
    private static final SshKeyExchangeAlgorithm KEX_DIF = new SshKeyExchangeAlgorithm(DiffieHellmanGroupExchangeSha1);
    private static final SshPublicKeyAlgorithm HOST_KEY_RSA = new SshPublicKeyAlgorithm(RsaSha2512);
    private static final SshPublicKeyAlgorithm HOST_KEY_SSH = new SshPublicKeyAlgorithm(SshEd25519);
    private static final SshEncryptionAlgorithm ENCRYPTION_CHA = new SshEncryptionAlgorithm(Chacha20Poly1305);
    private static final SshEncryptionAlgorithm ENCRYPTION_AES = new SshEncryptionAlgorithm(Aes256Cbc);
    private static final SshMacAlgorithm MAC_NONE = new SshMacAlgorithm(None);
    private static final SshMacAlgorithm MAC_HMAC = new SshMacAlgorithm(HmacSha2256);
    private static final String USER = "user";

    private final EventLoopGroup group = newEventLoopGroup("SessionFailure", 0);
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

    /**
     *  Test that correct algorithms are reported to SSHNegotiatedAlgListener on key established.
     *  Verify mina factory names are translated to RFC9644 enums values.
     */
    @Test
    void testValidAlgorithms() throws Exception {
        doReturn(BuiltinCiphers.aes256cbc.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(BuiltinSignatures.ed25519.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinMacs.hmacsha256.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);
        doReturn(BuiltinDHFactories.dhgex.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        sshClient.onKeyEstablished(transportClientSession);

        verify(algListener).onAlgorithmsNegotiated(KEX_DIF, HOST_KEY_SSH, ENCRYPTION_AES, MAC_HMAC);
    }

    /**
     * Test that invalid algorithms are handled properly.
     * To test all KexProposalOption options (key exchange, mac, host keys, encryption) other executed options needs
     * to be set to correct value.
     */
    @Test
    void testInvalidAlgorithms() {
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        assertThrows(IllegalArgumentException.class, () -> sshClient.onKeyEstablished(transportClientSession));

        doReturn(BuiltinDHFactories.sntrup761x25519_openssh.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));

        doReturn(BuiltinDHFactories.sntrup761x25519_openssh.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.rsaSHA512.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));

        doReturn(BuiltinDHFactories.sntrup761x25519_openssh.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);
        doReturn(BuiltinSignatures.rsaSHA512.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn(BuiltinCiphers.cc20p1305_openssh.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn("test").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);

        assertThrows(IllegalArgumentException.class, () ->  sshClient.onKeyEstablished(transportClientSession));
    }

    /**
     *  Test special case of mac being reported as "aead". This is special case when
     *  Authenticated Encryption with Associated Data (AEAD) is used mina-ssh reports "aead" as mac algorithm.
     */
    @Test
    void testAeadMac() throws Exception {
        doReturn(BuiltinCiphers.cc20p1305_openssh.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SENC);
        doReturn(BuiltinSignatures.rsaSHA512.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.SERVERKEYS);
        doReturn("aead").when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.C2SMAC);
        doReturn(BuiltinDHFactories.sntrup761x25519.getName()).when(transportClientSession)
            .getNegotiatedKexParameter(KexProposalOption.ALGORITHMS);

        sshClient.onKeyEstablished(transportClientSession);

        verify(algListener).onAlgorithmsNegotiated(KEX_SNT, HOST_KEY_RSA, ENCRYPTION_CHA, MAC_NONE);
    }
}
