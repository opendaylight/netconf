/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelAsyncOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.cipher.Cipher;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.Mac;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;
import org.opendaylight.netconf.shaded.sshd.server.ServerBuilder;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev240316.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev240316.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev240316.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev240316.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchange;

final class TransportUtils {
    private static final ImmutableMap<SshEncryptionAlgorithm, NamedFactory<Cipher>> CIPHERS =
            ImmutableMap.<SshEncryptionAlgorithm, NamedFactory<Cipher>>builder()
                    .put(SshEncryptionAlgorithm.AEADAES128GCM, BuiltinCiphers.aes128gcm)
                    .put(SshEncryptionAlgorithm.AEADAES256GCM, BuiltinCiphers.aes256cbc)
                    .put(SshEncryptionAlgorithm.Aes128Cbc, BuiltinCiphers.aes128cbc)
                    .put(SshEncryptionAlgorithm.Aes128Ctr, BuiltinCiphers.aes128ctr)
                    .put(SshEncryptionAlgorithm.Aes192Cbc, BuiltinCiphers.aes192cbc)
                    .put(SshEncryptionAlgorithm.Aes192Ctr, BuiltinCiphers.aes192ctr)
                    .put(SshEncryptionAlgorithm.Aes256Cbc, BuiltinCiphers.aes256cbc)
                    .put(SshEncryptionAlgorithm.Aes256Ctr, BuiltinCiphers.aes256ctr)
                    .put(SshEncryptionAlgorithm.Arcfour128, BuiltinCiphers.arcfour128)
                    .put(SshEncryptionAlgorithm.Arcfour256, BuiltinCiphers.arcfour256)
                    .put(SshEncryptionAlgorithm.BlowfishCbc, BuiltinCiphers.blowfishcbc)
                    .put(SshEncryptionAlgorithm._3desCbc, BuiltinCiphers.tripledescbc)
                    .put(SshEncryptionAlgorithm.None, BuiltinCiphers.none)
                    .build();
    private static final List<NamedFactory<Cipher>> DEFAULT_CIPHERS =
            ImmutableList.<NamedFactory<Cipher>>builder().addAll(BaseBuilder.DEFAULT_CIPHERS_PREFERENCE).build();

    private static final ImmutableMap<SshKeyExchangeAlgorithm, KeyExchangeFactory> CLIENT_KEXS;
    private static final ImmutableMap<SshKeyExchangeAlgorithm, KeyExchangeFactory> SERVER_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_CLIENT_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_SERVER_KEXS;

    static {
        final var factories = Maps.filterValues(ImmutableMap.<SshKeyExchangeAlgorithm, BuiltinDHFactories>builder()
                .put(SshKeyExchangeAlgorithm.Curve25519Sha256, BuiltinDHFactories.curve25519)
                .put(SshKeyExchangeAlgorithm.Curve448Sha512, BuiltinDHFactories.curve448)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup1Sha1, BuiltinDHFactories.dhg1)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup14Sha1, BuiltinDHFactories.dhg14)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup14Sha256, BuiltinDHFactories.dhg14_256)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup15Sha512, BuiltinDHFactories.dhg15_512)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup16Sha512, BuiltinDHFactories.dhg16_512)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup17Sha512, BuiltinDHFactories.dhg17_512)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroup18Sha512, BuiltinDHFactories.dhg18_512)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha1, BuiltinDHFactories.dhgex)
                .put(SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha256, BuiltinDHFactories.dhgex256)
                /*
                .put(SshKeyExchangeAlgorithm.EcdhSha21284010045311, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132016, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha21313201, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132026, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132027, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132033, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132036, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132037, null)
                .put(SshKeyExchangeAlgorithm.EcdhSha213132038, null)
                 */
                .put(SshKeyExchangeAlgorithm.EcdhSha2Nistp256, BuiltinDHFactories.ecdhp256)
                .put(SshKeyExchangeAlgorithm.EcdhSha2Nistp384, BuiltinDHFactories.ecdhp384)
                .put(SshKeyExchangeAlgorithm.EcdhSha2Nistp521, BuiltinDHFactories.ecdhp521)
                /*
                .put(SshKeyExchangeAlgorithm.EcmqvSha2, null)
                .put(SshKeyExchangeAlgorithm.ExtInfoC, null)
                .put(SshKeyExchangeAlgorithm.ExtInfoS, null)
                 Gss*
                 TODO: provide solution for remaining (commented out) KEX algorithms missing in BuiltinDHFactories
                */
                .build(), BuiltinDHFactories::isSupported);

        CLIENT_KEXS = ImmutableMap.copyOf(Maps.transformValues(factories, ClientBuilder.DH2KEX::apply));
        SERVER_KEXS = ImmutableMap.copyOf(Maps.transformValues(factories, ServerBuilder.DH2KEX::apply));
        DEFAULT_CLIENT_KEXS =
                ImmutableList.copyOf(Lists.transform(BaseBuilder.DEFAULT_KEX_PREFERENCE, ClientBuilder.DH2KEX::apply));
        DEFAULT_SERVER_KEXS =
                ImmutableList.copyOf(Lists.transform(BaseBuilder.DEFAULT_KEX_PREFERENCE, ServerBuilder.DH2KEX::apply));
    }

    private static final ImmutableMap<SshMacAlgorithm, NamedFactory<Mac>> MACS =
            ImmutableMap.<SshMacAlgorithm, NamedFactory<Mac>>builder()
                    .put(SshMacAlgorithm.HmacMd5, BuiltinMacs.hmacmd5)
                    .put(SshMacAlgorithm.HmacMd596, BuiltinMacs.hmacmd596)
                    .put(SshMacAlgorithm.HmacSha1, BuiltinMacs.hmacsha1)
                    .put(SshMacAlgorithm.HmacSha196, BuiltinMacs.hmacsha196)
                    .put(SshMacAlgorithm.HmacSha2256, BuiltinMacs.hmacsha256)
                    .put(SshMacAlgorithm.HmacSha2512, BuiltinMacs.hmacsha512)
                    /*
                     SshMacAlgorithm.AeadAes128Gcm
                     SshMacAlgorithm.AeadAes256Gcm
                     SshMacAlgorithm.None
                     openssh ETM extensions
                     TODO provide solution for remaining (commented out) macs missing in BuiltinMacs
                      */
                    .build();
    private static final List<NamedFactory<Mac>> DEFAULT_MACS =
            ImmutableList.<NamedFactory<Mac>>builder().addAll(BaseBuilder.DEFAULT_MAC_PREFERENCE).build();

    static final ImmutableMap<SshPublicKeyAlgorithm, NamedFactory<Signature>> SIGNATURES =
            ImmutableMap.<SshPublicKeyAlgorithm, NamedFactory<Signature>>builder()
                    .put(SshPublicKeyAlgorithm.EcdsaSha2Nistp256, BuiltinSignatures.nistp256)
                    .put(SshPublicKeyAlgorithm.EcdsaSha2Nistp384, BuiltinSignatures.nistp384)
                    .put(SshPublicKeyAlgorithm.EcdsaSha2Nistp521, BuiltinSignatures.nistp521)
                    .put(SshPublicKeyAlgorithm.RsaSha2512, BuiltinSignatures.rsaSHA512)
//                    .put(SshPublicKeyAlgorithm.PgpSignDss, null)
//                    .put(SshPublicKeyAlgorithm.PgpSignRsa, null)
                    .put(SshPublicKeyAlgorithm.RsaSha2256, BuiltinSignatures.rsaSHA256)
//                    .put(SshPublicKeyAlgorithm.SpkiSignRsa, null)
//                    .put(SshPublicKeyAlgorithm.SpkiSignDss, null)
                    .put(SshPublicKeyAlgorithm.SshDss, BuiltinSignatures.dsa)
//                    .put(SshPublicKeyAlgorithm.SshEd448, null)
                    .put(SshPublicKeyAlgorithm.SshEd25519, BuiltinSignatures.ed25519)
                    .put(SshPublicKeyAlgorithm.SshRsa, BuiltinSignatures.rsa)
                    /*
                    .put(SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp256, null)
                    .put(SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp384, null)
                    .put(SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp521, null)
                    .put(SshPublicKeyAlgorithm.X509v3Rsa2048Sha256, null)
                    .put(SshPublicKeyAlgorithm.X509v3SshDss, null)
                    .put(SshPublicKeyAlgorithm.X509v3SshRsa, null)
                    .put(SshPublicKeyAlgorithm.Null, null)
                     TODO provide solution for remaining (commented out) signatures missing in BuiltinSignatures
                    */
                    .build();

    static final List<NamedFactory<Signature>> DEFAULT_SIGNATURES =
            ImmutableList.<NamedFactory<Signature>>builder().addAll(BaseBuilder.DEFAULT_SIGNATURE_PREFERENCE).build();

    private TransportUtils() {
        // utility class
    }

    public static List<NamedFactory<Cipher>> getCipherFactories(final @Nullable Encryption encryption)
            throws UnsupportedConfigurationException {
        if (encryption != null) {
            final var encAlg = encryption.getEncryptionAlg();
            if (encAlg != null && !encAlg.isEmpty()) {
                return mapValues(CIPHERS, encAlg,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
                    .SshEncryptionAlgorithm::getSshEncryptionAlgorithm, "Unsupported Encryption algorithm %s");
            }
        }
        return DEFAULT_CIPHERS;
    }

    public static List<NamedFactory<Signature>> getSignatureFactories(@Nullable final HostKey hostKey)
            throws UnsupportedConfigurationException {
        if (hostKey != null) {
            final var hostKeyAlg = hostKey.getHostKeyAlg();
            if (hostKeyAlg != null && hostKeyAlg.isEmpty()) {
                return mapValues(SIGNATURES, hostKeyAlg,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
                    .SshPublicKeyAlgorithm::getSshPublicKeyAlgorithm,
                    "Unsupported Host Key algorithm %s");
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

    private static List<KeyExchangeFactory> getKexFactories(final KeyExchange keyExchange,
            final Map<SshKeyExchangeAlgorithm, KeyExchangeFactory> map,
            final List<KeyExchangeFactory> defaultResult) throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                return mapValues(map, kexAlg,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
                    .SshKeyExchangeAlgorithm::getSshKeyExchangeAlgorithm, "Unsupported Key Exchange algorithm %s");
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
                return mapValues(MACS, macAlg,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
                    .SshMacAlgorithm::getSshMacAlgorithm, "Unsupported MAC algorithm %s");
            }
        }
        return DEFAULT_MACS;
    }

    private static <K, U, V> List<V> mapValues(final Map<K, V> map, final List<U> values, final Function<U, K> func,
            final String errorTemplate) throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (var value : values) {
            final var key = func.apply(value);
            if (key != null) {
                final var mapped = map.get(key);
                if (mapped != null) {
                    builder.add(mapped);
                    continue;
                }
            }
            throw new UnsupportedOperationException(String.format(errorTemplate, value));
        }
        return builder.build();
    }

    static <T> T checkCast(final Class<T> clazz, final Object obj) throws IOException {
        try {
            return clazz.cast(requireNonNull(obj));
        } catch (ClassCastException e) {
            throw new IOException(e);
        }
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
