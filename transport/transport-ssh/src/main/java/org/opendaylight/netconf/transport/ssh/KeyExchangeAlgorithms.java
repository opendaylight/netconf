/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.DHFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerBuilder;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchange;

/**
 * Mapping of supported key exchange algorithms, mostly as maintained by IANA in
 * <a href="https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml">Key Exchange Method Names</a>.
 */
final class KeyExchangeAlgorithms {
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
        KeyExchangeFactory> CLIENT_BY_YANG;
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
        KeyExchangeFactory> SERVER_BY_YANG;
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

            // defined in https://www.rfc-editor.org/rfc/rfc5656#section-6.3
            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp256, BuiltinDHFactories.ecdhp256),
            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp384, BuiltinDHFactories.ecdhp384),
            entry(SshKeyExchangeAlgorithm.EcdhSha2Nistp521, BuiltinDHFactories.ecdhp521),
            // FIXME: figure out the implementation of these: these are OID-based, like "ecdh-sha2-1.3.132.0.1"
            // SshKeyExchangeAlgorithm.EcdhSha21313201
            // SshKeyExchangeAlgorithm.EcdhSha21284010045311
            // SshKeyExchangeAlgorithm.EcdhSha213132033
            // SshKeyExchangeAlgorithm.EcdhSha213132026
            // SshKeyExchangeAlgorithm.EcdhSha213132027
            // SshKeyExchangeAlgorithm.EcdhSha213132016
            // SshKeyExchangeAlgorithm.EcdhSha213132036
            // SshKeyExchangeAlgorithm.EcdhSha213132037
            // SshKeyExchangeAlgorithm.EcdhSha213132038

            // defined in https://www.rfc-editor.org/rfc/rfc5656#section-6.4
            // SshKeyExchangeAlgorithm.EcmqvSha2

            // gss-* is reserved by https://www.rfc-editor.org/rfc/rfc4462
            // TODO: can we reasonably support these? I suspect we need a GSS-API provider

            // defined in https://www.rfc-editor.org/rfc/rfc8732
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

            // defined in https://www.rfc-editor.org/rfc/rfc4432
            // the first is forbidden in https://www.rfc-editor.org/rfc/rfc9142#section-3.3, we just do not care for
            // the second as it is estimated at 112bits
            // SshKeyExchangeAlgorithm.Rsa1024Sha1
            // SshKeyExchangeAlgorithm.Rsa2048Sha256

            // defined in https://www.rfc-editor.org/rfc/rfc8308
            // TODO: there are used only during negotiation should always be ignored (?)
            // SshKeyExchangeAlgorithm.ExtInfoS
            // SshKeyExchangeAlgorithm.ExtInfoC

            // defined in https://www.rfc-editor.org/rfc/rfc8732
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

        CLIENT_BY_YANG = Map.copyOf(Maps.transformValues(factories, ClientBuilder.DH2KEX::apply));
        SERVER_BY_YANG = Map.copyOf(Maps.transformValues(factories, ServerBuilder.DH2KEX::apply));
        DEFAULT_CLIENT_KEXS = BaseBuilder.DEFAULT_KEX_PREFERENCE.stream()
            .map(ClientBuilder.DH2KEX::apply)
            .collect(Collectors.toUnmodifiableList());
        DEFAULT_SERVER_KEXS = BaseBuilder.DEFAULT_KEX_PREFERENCE.stream()
            .map(ServerBuilder.DH2KEX::apply)
            .collect(Collectors.toUnmodifiableList());
    }

    private KeyExchangeAlgorithms() {
        // Hidden on purpose
    }

    static List<KeyExchangeFactory> clientFactoriesFor(final @Nullable KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        return factoriesFor(keyExchange, CLIENT_BY_YANG, DEFAULT_CLIENT_KEXS);
    }

    static List<KeyExchangeFactory> serverFactoriesFor(final @Nullable KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        return factoriesFor(keyExchange, SERVER_BY_YANG, DEFAULT_SERVER_KEXS);
    }

    private static List<KeyExchangeFactory> factoriesFor(final @Nullable KeyExchange keyExchange, final Map<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
            KeyExchangeFactory> map, final List<KeyExchangeFactory> defaultResult)
                    throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                // FIXME: this logic does not allow us to configure aliases like '@libssh.org', etc.
                return ConfigUtils.mapValues(map, kexAlg, "Unsupported Key Exchange algorithm %s");
            }
        }
        return defaultResult;
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
            DHFactory> entry(final SshKeyExchangeAlgorithm alg, final DHFactory factory) {
        return Map.entry(keyOf(alg), factory);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshKeyExchangeAlgorithm keyOf(final SshKeyExchangeAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshKeyExchangeAlgorithm(alg);
    }
}
