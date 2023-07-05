/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.cipher.Cipher;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.Mac;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;
import org.opendaylight.netconf.shaded.sshd.server.ServerBuilder;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.EcdsaSha2Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.EcdsaSha2Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.EcdsaSha2Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.PublicKeyAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.RsaSha2256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.RsaSha2512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.SshDss;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.SshEd25519;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev220616.SshRsa;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.AeadAes128Gcm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.AeadAes256Gcm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes128Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes128Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes192Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes192Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes256Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes256Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Arcfour128;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Arcfour256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.BlowfishCbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.EncryptionAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.None;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.TripleDesCbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup15Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup16Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup17Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup18Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup1Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.KeyExchangeAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd5;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd596;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha196;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.MacAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.transport.params.grouping.KeyExchange;

final class TransportUtils {
    private static final Map<EncryptionAlgBase, NamedFactory<Cipher>> CIPHERS =
            ImmutableMap.<EncryptionAlgBase, NamedFactory<Cipher>>builder()
                    .put(AeadAes128Gcm.VALUE, BuiltinCiphers.aes128gcm)
                    .put(AeadAes256Gcm.VALUE, BuiltinCiphers.aes256cbc)
                    .put(Aes128Cbc.VALUE, BuiltinCiphers.aes128cbc)
                    .put(Aes128Ctr.VALUE, BuiltinCiphers.aes128ctr)
                    .put(Aes192Cbc.VALUE, BuiltinCiphers.aes192cbc)
                    .put(Aes192Ctr.VALUE, BuiltinCiphers.aes192ctr)
                    .put(Aes256Cbc.VALUE, BuiltinCiphers.aes256cbc)
                    .put(Aes256Ctr.VALUE, BuiltinCiphers.aes256ctr)
                    .put(Arcfour128.VALUE, BuiltinCiphers.arcfour128)
                    .put(Arcfour256.VALUE, BuiltinCiphers.arcfour256)
                    .put(BlowfishCbc.VALUE, BuiltinCiphers.blowfishcbc)
                    .put(TripleDesCbc.VALUE, BuiltinCiphers.tripledescbc)
                    .put(None.VALUE, BuiltinCiphers.none)
                    .build();
    private static final List<NamedFactory<Cipher>> DEFAULT_CIPHERS =
            ImmutableList.<NamedFactory<Cipher>>builder().addAll(BaseBuilder.DEFAULT_CIPHERS_PREFERENCE).build();

    private static final Map<KeyExchangeAlgBase, KeyExchangeFactory> CLIENT_KEXS;
    private static final Map<KeyExchangeAlgBase, KeyExchangeFactory> SERVER_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_CLIENT_KEXS;
    private static final List<KeyExchangeFactory> DEFAULT_SERVER_KEXS;

    static {
        final var factories = Maps.filterValues(ImmutableMap.<KeyExchangeAlgBase, BuiltinDHFactories>builder()
                .put(Curve25519Sha256.VALUE, BuiltinDHFactories.curve25519)
                .put(Curve448Sha512.VALUE, BuiltinDHFactories.curve448)
                .put(DiffieHellmanGroup1Sha1.VALUE, BuiltinDHFactories.dhg1)
                .put(DiffieHellmanGroup14Sha1.VALUE, BuiltinDHFactories.dhg14)
                .put(DiffieHellmanGroup14Sha256.VALUE, BuiltinDHFactories.dhg14_256)
                .put(DiffieHellmanGroup15Sha512.VALUE, BuiltinDHFactories.dhg15_512)
                .put(DiffieHellmanGroup16Sha512.VALUE, BuiltinDHFactories.dhg16_512)
                .put(DiffieHellmanGroup17Sha512.VALUE, BuiltinDHFactories.dhg17_512)
                .put(DiffieHellmanGroup18Sha512.VALUE, BuiltinDHFactories.dhg18_512)
                .put(DiffieHellmanGroupExchangeSha1.VALUE, BuiltinDHFactories.dhgex)
                .put(DiffieHellmanGroupExchangeSha256.VALUE, BuiltinDHFactories.dhgex256)
                /*
                .put(EcdhSha21284010045311.VALUE, null)
                .put(EcdhSha213132016.VALUE, null)
                .put(EcdhSha21313201.VALUE, null)
                .put(EcdhSha213132026.VALUE, null)
                .put(EcdhSha213132027.VALUE, null)
                .put(EcdhSha213132033.VALUE, null)
                .put(EcdhSha213132036.VALUE, null)
                .put(EcdhSha213132037.VALUE, null)
                .put(EcdhSha213132038.VALUE, null)
                 */
                .put(EcdhSha2Nistp256.VALUE, BuiltinDHFactories.ecdhp256)
                .put(EcdhSha2Nistp384.VALUE, BuiltinDHFactories.ecdhp384)
                .put(EcdhSha2Nistp521.VALUE, BuiltinDHFactories.ecdhp521)
                /*
                .put(EcmqvSha2.VALUE, null)
                .put(ExtInfoC.VALUE, null)
                .put(ExtInfoS.VALUE, null)
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

    private static final Map<MacAlgBase, NamedFactory<Mac>> MACS =
            ImmutableMap.<MacAlgBase, NamedFactory<Mac>>builder()
                    .put(HmacMd5.VALUE, BuiltinMacs.hmacmd5)
                    .put(HmacMd596.VALUE, BuiltinMacs.hmacmd596)
                    .put(HmacSha1.VALUE, BuiltinMacs.hmacsha1)
                    .put(HmacSha196.VALUE, BuiltinMacs.hmacsha196)
                    .put(HmacSha2256.VALUE, BuiltinMacs.hmacsha256)
                    .put(HmacSha2512.VALUE, BuiltinMacs.hmacsha512)
                    /*
                     AeadAes128Gcm.VALUE
                     AeadAes256Gcm.VALUE
                     None.VALUE
                     openssh ETM extensions
                     TODO provide solution for remaining (commented out) macs missing in BuiltinMacs
                      */
                    .build();
    private static final List<NamedFactory<Mac>> DEFAULT_MACS =
            ImmutableList.<NamedFactory<Mac>>builder().addAll(BaseBuilder.DEFAULT_MAC_PREFERENCE).build();

    static final Map<PublicKeyAlgBase, NamedFactory<Signature>> SIGNATURES =
            ImmutableMap.<PublicKeyAlgBase, NamedFactory<Signature>>builder()
                    .put(EcdsaSha2Nistp256.VALUE, BuiltinSignatures.nistp256)
                    .put(EcdsaSha2Nistp384.VALUE, BuiltinSignatures.nistp384)
                    .put(EcdsaSha2Nistp521.VALUE, BuiltinSignatures.nistp521)
                    .put(RsaSha2512.VALUE, BuiltinSignatures.rsaSHA512)
//                    .put(PgpSignDss.VALUE, null)
//                    .put(PgpSignRsa.VALUE, null)
                    .put(RsaSha2256.VALUE, BuiltinSignatures.rsaSHA256)
//                    .put(SpkiSignRsa.VALUE, null)
//                    .put(SpkiSignDss.VALUE, null)
                    .put(SshDss.VALUE, BuiltinSignatures.dsa)
//                    .put(SshEd448.VALUE, null)
                    .put(SshEd25519.VALUE, BuiltinSignatures.ed25519)
                    .put(SshRsa.VALUE, BuiltinSignatures.rsa)
                    /*
                    .put(X509v3EcdsaSha2Nistp256.VALUE, null)
                    .put(X509v3EcdsaSha2Nistp384.VALUE, null)
                    .put(X509v3EcdsaSha2Nistp521.VALUE, null)
                    .put(X509v3Rsa2048Sha256.VALUE, null)
                    .put(X509v3SshDss.VALUE, null)
                    .put(X509v3SshRsa.VALUE, null)
                    .put(Null.VALUE, null)
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

    private static List<KeyExchangeFactory> getKexFactories(final KeyExchange keyExchange,
            final Map<KeyExchangeAlgBase, KeyExchangeFactory> map,
            final List<KeyExchangeFactory> defaultResult) throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                return mapValues(map, kexAlg, "Unsupported Key Exchange algorithm %s");
            }
        }
        return defaultResult;
    }

    public static List<NamedFactory<Mac>> getMacFactories(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417
                    .transport.params.grouping.Mac mac) throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null && !macAlg.isEmpty()) {
                return mapValues(MACS, macAlg, "Unsupported MAC algorithm %s");
            }
        }
        return DEFAULT_MACS;
    }

    private static <K, V> List<V> mapValues(final Map<K, V> map, final List<K> values, final String errorTemplate)
            throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (K value : values) {
            final V mapped = map.get(value);
            if (mapped == null) {
                throw new UnsupportedOperationException(String.format(errorTemplate, value));
            }
            builder.add(mapped);
        }
        return builder.build();
    }
}
