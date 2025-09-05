/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelAsyncOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.cipher.Cipher;
import org.opendaylight.netconf.shaded.sshd.common.cipher.CipherFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.DHFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.Mac;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;
import org.opendaylight.netconf.shaded.sshd.common.signature.SignatureFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerBuilder;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchange;

final class TransportUtils {
    // Corresponds to Encryption Algorithm Names in
    // https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm,
        CipherFactory> CIPHERS = Map.ofEntries(
            // Keep the same order as in iana-ssh-encryption-algs.yang

            // FIXME: audit commented-out algorithms missing in BuiltinCiphers or provide justification for exclusion
            // FIXME: update based on https://www.rfc-editor.org/rfc/rfc8758

            // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm._3desCbc, BuiltinCiphers.tripledescbc),
            // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm.BlowfishCbc, BuiltinCiphers.blowfishcbc),

            // defined in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            // SshEncryptionAlgorithm.Twofish256Cbc
            // SshEncryptionAlgorithm.TwofishCbc
            // SshEncryptionAlgorithm.Twofish192Cbc
            // SshEncryptionAlgorithm.Twofish128Cbc

            // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm.Aes256Cbc, BuiltinCiphers.aes256cbc),
            // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm.Aes192Cbc, BuiltinCiphers.aes192cbc),
            // recommended in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm.Aes128Cbc, BuiltinCiphers.aes128cbc),

            // defined in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            // SshEncryptionAlgorithm.Serpent256Cbc
            // SshEncryptionAlgorithm.Serpent192Cbc
            // SshEncryptionAlgorithm.Serpent128Cbc
            // SshEncryptionAlgorithm.Arcfour
            // SshEncryptionAlgorithm.IdeaCbc
            // SshEncryptionAlgorithm.Cast128Cbc

            // not recommended in https://www.rfc-editor.org/rfc/rfc4253#section-6.3
            entry(SshEncryptionAlgorithm.None, BuiltinCiphers.none),

            // SshEncryptionAlgorithm.DesCbc is HISTORIC and hence not implemented

            // defined in https://www.rfc-editor.org/rfc/rfc4345#section-4
            entry(SshEncryptionAlgorithm.Arcfour128, BuiltinCiphers.arcfour128),
            entry(SshEncryptionAlgorithm.Arcfour256, BuiltinCiphers.arcfour256),

            // recommended in https://www.rfc-editor.org/rfc/rfc4344.html#section-4
            entry(SshEncryptionAlgorithm.Aes128Ctr, BuiltinCiphers.aes128ctr),
            // recommended in https://www.rfc-editor.org/rfc/rfc4344.html#section-4
            entry(SshEncryptionAlgorithm.Aes192Ctr, BuiltinCiphers.aes192ctr),
            // recommended in https://www.rfc-editor.org/rfc/rfc4344.html#section-4
            entry(SshEncryptionAlgorithm.Aes256Ctr, BuiltinCiphers.aes256ctr),

            // defined in https://www.rfc-editor.org/rfc/rfc4344.html#section-4
            // SshEncryptionAlgorithm._3desCtr
            // SshEncryptionAlgorithm.BlowfishCtr
            // SshEncryptionAlgorithm.Twofish128Ctr
            // SshEncryptionAlgorithm.Twofish192Ctr
            // SshEncryptionAlgorithm.Twofish256Ctr
            // SshEncryptionAlgorithm.Serpent128Ctr
            // SshEncryptionAlgorithm.Serpent192Ctr
            // SshEncryptionAlgorithm.Serpent256Ctr
            // SshEncryptionAlgorithm.IdeaCtr
            // SshEncryptionAlgorithm.Cast128Ctr

            // defined in https://www.rfc-editor.org/rfc/rfc5647
            entry(SshEncryptionAlgorithm.AEADAES128GCM, BuiltinCiphers.aes128gcm),
            entry(SshEncryptionAlgorithm.AEADAES256GCM, BuiltinCiphers.aes256gcm)

            // defined in https://datatracker.ietf.org/doc/draft-josefsson-ssh-chacha20-poly1305-openssh/01/
            // FIXME: does this equal to BuiltinCiphers.cc20p1305_openssh? we need to read up on the drafts to
            //        determine that
            // SshEncryptionAlgorithm.Chacha20Poly1305
            );

    private static final List<NamedFactory<Cipher>> DEFAULT_CIPHERS =
        List.copyOf(BaseBuilder.DEFAULT_CIPHERS_PREFERENCE);

    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
        KeyExchangeFactory> CLIENT_KEXS;
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
        KeyExchangeFactory> SERVER_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_CLIENT_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_SERVER_KEXS;

    static {
        // Corresponds to Key Exchange Method Names in
        // https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml
        final var factories = Maps.filterValues(Map.ofEntries(
            // Keep the same order as in iana-ssh-key-exchange-algs.yang

            // FIXME: audit commented-out algorithms missing in BuiltinDHFactories or provide justification for
            //        exclusion
            // FIXME: update based on https://www.rfc-editor.org/rfc/rfc8270
            // FIXME: update based on https://www.rfc-editor.org/rfc/rfc9142

            // defined in https://www.rfc-editor.org/rfc/rfc4419#section-4
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1, BuiltinDHFactories.dhgex),
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha256, BuiltinDHFactories.dhgex256),

            // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.5
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup1Sha1, BuiltinDHFactories.dhg1),
            // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.5
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup14Sha1, BuiltinDHFactories.dhg14),

            // defined in https://www.rfc-editor.org/rfc/rfc8268#section-3
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup14Sha256, BuiltinDHFactories.dhg14_256),
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup15Sha512, BuiltinDHFactories.dhg15_512),
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup16Sha512, BuiltinDHFactories.dhg16_512),
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup17Sha512, BuiltinDHFactories.dhg17_512),
            entry(SshKeyExchangeAlgorithm.DiffieHellmanGroup18Sha512, BuiltinDHFactories.dhg18_512),


            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp256, BuiltinDHFactories.ecdhp256),
            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp384, BuiltinDHFactories.ecdhp384),
            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp521, BuiltinDHFactories.ecdhp521),

            // SshKeyExchangeAlgorithm.EcdhSha21313201
            // SshKeyExchangeAlgorithm.EcdhSha21284010045311
            // SshKeyExchangeAlgorithm.EcdhSha213132033
            // SshKeyExchangeAlgorithm.EcdhSha213132026
            // SshKeyExchangeAlgorithm.EcdhSha213132027
            // SshKeyExchangeAlgorithm.EcdhSha213132016
            // SshKeyExchangeAlgorithm.EcdhSha213132036
            // SshKeyExchangeAlgorithm.EcdhSha213132037
            // SshKeyExchangeAlgorithm.EcdhSha213132038
            // SshKeyExchangeAlgorithm.EcmqvSha2
            // SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp256
            // SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp384
            // SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp521
            // SshKeyExchangeAlgorithm.GssGroup1Sha11313201
            // SshKeyExchangeAlgorithm.GssGroup1Sha11284010045311
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132033
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132026
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132027
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132016
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132036
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132037
            // SshKeyExchangeAlgorithm.GssGroup1Sha113132038
            // SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp256
            // SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp384
            // SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp521
            // SshKeyExchangeAlgorithm.GssGroup14Sha11313201
            // SshKeyExchangeAlgorithm.GssGroup14Sha11284010045311
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132033
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132026
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132027
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132016
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132036
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132037
            // SshKeyExchangeAlgorithm.GssGroup14Sha113132038
            // SshKeyExchangeAlgorithm.GssGexSha1Nistp256
            // SshKeyExchangeAlgorithm.GssGexSha1Nistp384
            // SshKeyExchangeAlgorithm.GssGexSha1Nistp521
            // SshKeyExchangeAlgorithm.GssGexSha11313201
            // SshKeyExchangeAlgorithm.GssGexSha11284010045311
            // SshKeyExchangeAlgorithm.GssGexSha113132033
            // SshKeyExchangeAlgorithm.GssGexSha113132026
            // SshKeyExchangeAlgorithm.GssGexSha113132027
            // SshKeyExchangeAlgorithm.GssGexSha113132016
            // SshKeyExchangeAlgorithm.GssGexSha113132036
            // SshKeyExchangeAlgorithm.GssGexSha113132037
            // SshKeyExchangeAlgorithm.GssGexSha113132038
            // SshKeyExchangeAlgorithm.Gss
            // SshKeyExchangeAlgorithm.Rsa1024Sha1
            // SshKeyExchangeAlgorithm.Rsa2048Sha256
            // SshKeyExchangeAlgorithm.ExtInfoS
            // SshKeyExchangeAlgorithm.ExtInfoC
            // SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp256
            // SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp384
            // SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp521
            // SshKeyExchangeAlgorithm.GssGroup14Sha2561313201
            // SshKeyExchangeAlgorithm.GssGroup14Sha2561284010045311
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132033
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132026
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132027
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132016
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132036
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132037
            // SshKeyExchangeAlgorithm.GssGroup14Sha25613132038
            // SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssGroup15Sha5121313201
            // SshKeyExchangeAlgorithm.GssGroup15Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132033
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132026
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132027
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132016
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132036
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132037
            // SshKeyExchangeAlgorithm.GssGroup15Sha51213132038
            // SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssGroup16Sha5121313201
            // SshKeyExchangeAlgorithm.GssGroup16Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132033
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132026
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132027
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132016
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132036
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132037
            // SshKeyExchangeAlgorithm.GssGroup16Sha51213132038
            // SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssGroup17Sha5121313201
            // SshKeyExchangeAlgorithm.GssGroup17Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132033
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132026
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132027
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132016
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132036
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132037
            // SshKeyExchangeAlgorithm.GssGroup17Sha51213132038
            // SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssGroup18Sha5121313201
            // SshKeyExchangeAlgorithm.GssGroup18Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132033
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132026
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132027
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132016
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132036
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132037
            // SshKeyExchangeAlgorithm.GssGroup18Sha51213132038
            // SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp256
            // SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp384
            // SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp521
            // SshKeyExchangeAlgorithm.GssNistp256Sha2561313201
            // SshKeyExchangeAlgorithm.GssNistp256Sha2561284010045311
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132033
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132026
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132027
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132016
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132036
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132037
            // SshKeyExchangeAlgorithm.GssNistp256Sha25613132038
            // SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp256
            // SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp384
            // SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp521
            // SshKeyExchangeAlgorithm.GssNistp384Sha3841313201
            // SshKeyExchangeAlgorithm.GssNistp384Sha3841284010045311
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132033
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132026
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132027
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132016
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132036
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132037
            // SshKeyExchangeAlgorithm.GssNistp384Sha38413132038
            // SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssNistp521Sha5121313201
            // SshKeyExchangeAlgorithm.GssNistp521Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132033
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132026
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132027
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132016
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132036
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132037
            // SshKeyExchangeAlgorithm.GssNistp521Sha51213132038
            // SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp256
            // SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp384
            // SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp521
            // SshKeyExchangeAlgorithm.GssCurve25519Sha2561313201
            // SshKeyExchangeAlgorithm.GssCurve25519Sha2561284010045311
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132033
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132026
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132027
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132016
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132036
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132037
            // SshKeyExchangeAlgorithm.GssCurve25519Sha25613132038
            // SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp256
            // SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp384
            // SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp521
            // SshKeyExchangeAlgorithm.GssCurve448Sha5121313201
            // SshKeyExchangeAlgorithm.GssCurve448Sha5121284010045311
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132033
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132026
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132027
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132016
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132036
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132037
            // SshKeyExchangeAlgorithm.GssCurve448Sha51213132038

            entry(SshKeyExchangeAlgorithm.Curve25519Sha256, BuiltinDHFactories.curve25519),
            entry(SshKeyExchangeAlgorithm.Curve448Sha512, BuiltinDHFactories.curve448)

            // defined in https://datatracker.ietf.org/doc/draft-josefsson-ntruprime-ssh/02/
            // FIXME: does this match any of the following
            //        BuiltinDHFactories.sntrup761x25519
            //        BuiltinDHFactories.sntrup761x25519_openssh
            // SshKeyExchangeAlgorithm.Sntrup761x25519Sha512

            // defined in https://datatracker.ietf.org/doc/draft-kampanakis-curdle-ssh-pq-ke/04/
            // FIXME: do these match, in order:
            //        BuiltinDHFactories.mlkem768nistp256
            //        BuiltinDHFactories.mlkem1024nistp384
            //        BuiltinDHFactories.mlkem768x25519
            // SshKeyExchangeAlgorithm.Mlkem768nistp256Sha256
            // SshKeyExchangeAlgorithm.Mlkem1024nistp384Sha384
            // SshKeyExchangeAlgorithm.Mlkem768x25519Sha256
            ), DHFactory::isSupported);

        CLIENT_KEXS = Map.copyOf(Maps.transformValues(factories, ClientBuilder.DH2KEX::apply));
        SERVER_KEXS = Map.copyOf(Maps.transformValues(factories, ServerBuilder.DH2KEX::apply));
        DEFAULT_CLIENT_KEXS = BaseBuilder.DEFAULT_KEX_PREFERENCE.stream()
            .map(ClientBuilder.DH2KEX::apply)
            .collect(Collectors.toUnmodifiableList());
        DEFAULT_SERVER_KEXS = BaseBuilder.DEFAULT_KEX_PREFERENCE.stream()
            .map(ServerBuilder.DH2KEX::apply)
            .collect(Collectors.toUnmodifiableList());
    }

    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
        // Corresponds to MAC Algorithm Names in
        // https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml
        MacFactory> MACS = Map.ofEntries(
            // Keep the same order as in iana-ssh-mac-algs.yang

            // FIXME: audit commented-out algorithms missing in BuiltinMacs or provide justification for exclusion

            // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
            entry(SshMacAlgorithm.HmacSha1, BuiltinMacs.hmacsha1),
            // recommeded in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
            entry(SshMacAlgorithm.HmacSha196, BuiltinMacs.hmacsha196),
            // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
            entry(SshMacAlgorithm.HmacMd5, BuiltinMacs.hmacmd5),
            // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
            entry(SshMacAlgorithm.HmacMd596, BuiltinMacs.hmacmd596),

            // defined in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
            // SshMacAlgorithm.None

            // defined in https://www.rfc-editor.org/rfc/rfc5647
            // SshMacAlgorithm.AEADAES128GCM
            // SshMacAlgorithm.AEADAES256GCM

            // recommended in https://www.rfc-editor.org/rfc/rfc6668#section-2
            entry(SshMacAlgorithm.HmacSha2256, BuiltinMacs.hmacsha256),
            // recommended in https://www.rfc-editor.org/rfc/rfc6668#section-2
            entry(SshMacAlgorithm.HmacSha2512, BuiltinMacs.hmacsha512));

    private static final List<NamedFactory<Mac>> DEFAULT_MACS = List.copyOf(BaseBuilder.DEFAULT_MAC_PREFERENCE);

    // Corresponds to Public Key Algorithm Names in
    // https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm,
        SignatureFactory> SIGNATURES = Map.ofEntries(
            // Keep the same order as in iana-ssh-public-key-algs.yang

            // FIXME: audit commented-out algorithms missing in BuiltinSignatures or provide justification for exclusion

            // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.6
            entry(SshPublicKeyAlgorithm.SshDss, BuiltinSignatures.dsa),
            // recommended in https://www.rfc-editor.org/rfc/rfc4253#section-6.6
            entry(SshPublicKeyAlgorithm.SshRsa, BuiltinSignatures.rsa),

            // recommended in https://www.rfc-editor.org/rfc/rfc8332#section-3
            entry(SshPublicKeyAlgorithm.RsaSha2256, BuiltinSignatures.rsaSHA256),
            // optional in https://www.rfc-editor.org/rfc/rfc8332#section-3
            entry(SshPublicKeyAlgorithm.RsaSha2512, BuiltinSignatures.rsaSHA512),

            // defined in https://www.rfc-editor.org/rfc/rfc4253#section-6.6
            // SshPublicKeyAlgorithm.SpkiSignRsa
            // SshPublicKeyAlgorithm.SpkiSignDss
            // SshPublicKeyAlgorithm.PgpSignRsa
            // SshPublicKeyAlgorithm.PgpSignDss

            // defined in https://www.rfc-editor.org/rfc/rfc4462#section-5
            // SshPublicKeyAlgorithm.Null

            // defined in https://www.rfc-editor.org/rfc/rfc5656, the first three are required curves
            entry(SshPublicKeyAlgorithm.EcdsaSha2Nistp256, BuiltinSignatures.nistp256),
            entry(SshPublicKeyAlgorithm.EcdsaSha2Nistp384, BuiltinSignatures.nistp384),
            entry(SshPublicKeyAlgorithm.EcdsaSha2Nistp521, BuiltinSignatures.nistp521),
            // SshPublicKeyAlgorithm.EcdsaSha21313201
            // SshPublicKeyAlgorithm.EcdsaSha21284010045311
            // SshPublicKeyAlgorithm.EcdsaSha213132033
            // SshPublicKeyAlgorithm.EcdsaSha213132026
            // SshPublicKeyAlgorithm.EcdsaSha213132027
            // SshPublicKeyAlgorithm.EcdsaSha213132016
            // SshPublicKeyAlgorithm.EcdsaSha213132036
            // SshPublicKeyAlgorithm.EcdsaSha213132037
            // SshPublicKeyAlgorithm.EcdsaSha213132038

            // defined in https://www.rfc-editor.org/rfc/rfc6187
            // SshPublicKeyAlgorithm.X509v3SshDss
            // SshPublicKeyAlgorithm.X509v3SshRsa
            // SshPublicKeyAlgorithm.X509v3Rsa2048Sha256
            // SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp256
            // SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp384
            // SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp521
            // SshPublicKeyAlgorithm.X509v3EcdsaSha21313201
            // SshPublicKeyAlgorithm.X509v3EcdsaSha21284010045311
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132033
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132026
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132027
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132016
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132036
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132037
            // SshPublicKeyAlgorithm.X509v3EcdsaSha213132038

            // defined in https://www.rfc-editor.org/rfc/rfc8709#section-4
            entry(SshPublicKeyAlgorithm.SshEd25519, BuiltinSignatures.ed25519)
            // SshPublicKeyAlgorithm.SshEd448
            );

    private static final List<NamedFactory<Signature>> DEFAULT_SIGNATURES =
        List.copyOf(BaseBuilder.DEFAULT_SIGNATURE_PREFERENCE);

    private TransportUtils() {
        // utility class
    }

    public static List<NamedFactory<Cipher>> getCipherFactories(final @Nullable Encryption encryption)
            throws UnsupportedConfigurationException {
        if (encryption != null) {
            final var encAlg = encryption.getEncryptionAlg();
            if (encAlg != null && !encAlg.isEmpty()) {
                return mapValues(CIPHERS, encAlg, "Unsupported Encryption algorithm %s");
            }
        }
        return DEFAULT_CIPHERS;
    }

    public static List<NamedFactory<Signature>> getSignatureFactories(@Nullable final HostKey hostKey)
            throws UnsupportedConfigurationException {
        if (hostKey != null) {
            final var hostKeyAlg = hostKey.getHostKeyAlg();
            if (hostKeyAlg != null && hostKeyAlg.isEmpty()) {
                return mapValues(SIGNATURES, hostKeyAlg, "Unsupported Host Key algorithm %s");
            }
        }
        return DEFAULT_SIGNATURES;
    }

    public static List<KeyExchangeFactory> getClientKexFactories(final KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        return getKexFactories(keyExchange, CLIENT_KEXS, DEFAULT_CLIENT_KEXS);
    }

    public static List<KeyExchangeFactory> getServerKexFactories(final KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        return getKexFactories(keyExchange, SERVER_KEXS, DEFAULT_SERVER_KEXS);
    }

    private static List<KeyExchangeFactory> getKexFactories(final KeyExchange keyExchange, final Map<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
            KeyExchangeFactory> map, final List<KeyExchangeFactory> defaultResult)
                    throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                return mapValues(map, kexAlg, "Unsupported Key Exchange algorithm %s");
            }
        }
        return defaultResult;
    }

    public static List<NamedFactory<Mac>> getMacFactories(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
                    .transport.params.grouping.Mac mac) throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null && !macAlg.isEmpty()) {
                return mapValues(MACS, macAlg, "Unsupported MAC algorithm %s");
            }
        }
        return DEFAULT_MACS;
    }

    private static <K, V> List<V> mapValues(final Map<K, ? extends V> map, final List<K> values,
            final String errorTemplate) throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (var value : values) {
            final var mapped = map.get(value);
            if (mapped == null) {
                throw new UnsupportedOperationException(String.format(errorTemplate, value));
            }
            builder.add(mapped);
        }
        return builder.build();
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm,
            CipherFactory> entry(final SshEncryptionAlgorithm alg, final CipherFactory factory) {
        return Map.entry(wrap(alg), factory);
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
            DHFactory> entry(final SshKeyExchangeAlgorithm alg, final DHFactory factory) {
        return Map.entry(wrap(alg), factory);
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
            MacFactory> entry(final SshMacAlgorithm alg, final MacFactory factory) {
        return Map.entry(wrap(alg), factory);
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm,
            SignatureFactory> entry(final SshPublicKeyAlgorithm alg, final SignatureFactory factory) {
        return Map.entry(wrap(alg), factory);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshEncryptionAlgorithm wrap(final SshEncryptionAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshEncryptionAlgorithm(alg);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshKeyExchangeAlgorithm wrap(final SshKeyExchangeAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshKeyExchangeAlgorithm(alg);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshMacAlgorithm wrap(final SshMacAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshMacAlgorithm(alg);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshPublicKeyAlgorithm wrap(final SshPublicKeyAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshPublicKeyAlgorithm(alg);
    }

    @FunctionalInterface
    interface ChannelInactive {

        void onChannelInactive() throws Exception;
    }

    static ChannelHandlerContext attachUnderlay(final IoOutputStream out, final TransportChannel underlay,
            final ChannelInactive inactive) {
        if (!(out instanceof ChannelAsyncOutputStream asyncOut)) {
            throw new VerifyException("Unexpected output " + out);
        }

        // Note that there may be multiple handlers already present on the channel, hence we are attaching last, but
        // from the logical perspective we are the head handlers.
        final var pipeline = underlay.channel().pipeline();

        // outbound packet handler, i.e. moving bytes from the channel into SSHD's pipeline
        pipeline.addLast(new OutboundChannelHandler(asyncOut));

        // invoke requested action on channel termination
        underlay.channel().closeFuture().addListener(future -> inactive.onChannelInactive());

        // last handler context is used as entry point to direct inbound packets (captured by SSH adapter)
        // back to same channel pipeline
        return pipeline.lastContext();
    }
}
