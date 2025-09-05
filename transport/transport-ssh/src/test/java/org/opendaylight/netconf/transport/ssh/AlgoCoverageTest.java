/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm;

/**
 * This is a two-way consistency test for the four aspects of SSH crypto:
 * <ul>
 *   <li>{@link SshEncryptionAlgorithm} and {@link BuiltinCiphers}</li>
 *   <li>{@link SshKeyExchangeAlgorithm} and {@link BuiltinDHFactories}</li>
 *   <li>{@link SshMacExchangeAlgorithm} and {@link BuiltinMacs}</li>
 *   <li>{@link SshPublicKeyAlgorithm} and {@link BuiltinSignatures}</li>
 * </ul>
 * Each side has an enumeration and thus this test goes through values of each and checks whether they are covered in
 * {@link TransportUtils}' lookup maps (for each {@code Ssh*Algorithm} or are explicitly suppressed (for both sides).
 *
 * <p>This provides explicit guards for evolution:
 * <ul>
 *   <li>every time new algorithm are added by IANA, we need to take action on how to implement them</li>
 *   <li>every time SSHD grows a built in, we need to see how best we can take advantage of it</li>
 * </ul>
 */
class AlgoCoverageTest {
    @Test
    void coveredSshEncryptionAlgorithm() {
        assertEquals(List.of(), Arrays.stream(SshEncryptionAlgorithm.values())
            .filter(alg -> !SUPPRESSED_ENCRYPTION.contains(alg))
            .filter(alg -> !TransportUtils.CIPHERS.containsKey(TransportUtils.wrap(alg)))
            .toList());
    }

    // Keep the same order as in iana-ssh-encryption-algs.yang
    private static final Set<SshEncryptionAlgorithm> SUPPRESSED_ENCRYPTION = Set.of(
        // FIXME: provide reasons for exclusion
        SshEncryptionAlgorithm.Twofish256Cbc,
        SshEncryptionAlgorithm.TwofishCbc,
        SshEncryptionAlgorithm.Twofish192Cbc,
        SshEncryptionAlgorithm.Twofish128Cbc,
        SshEncryptionAlgorithm.Serpent256Cbc,
        SshEncryptionAlgorithm.Serpent192Cbc,
        SshEncryptionAlgorithm.Serpent128Cbc,
        SshEncryptionAlgorithm.Arcfour,
        SshEncryptionAlgorithm.IdeaCbc,
        SshEncryptionAlgorithm.Cast128Cbc,
        SshEncryptionAlgorithm.DesCbc,
        SshEncryptionAlgorithm._3desCtr,
        SshEncryptionAlgorithm.BlowfishCtr,
        SshEncryptionAlgorithm.Twofish128Ctr,
        SshEncryptionAlgorithm.Twofish192Ctr,
        SshEncryptionAlgorithm.Twofish256Ctr,
        SshEncryptionAlgorithm.Serpent128Ctr,
        SshEncryptionAlgorithm.Serpent192Ctr,
        SshEncryptionAlgorithm.Serpent256Ctr,
        SshEncryptionAlgorithm.IdeaCtr,
        SshEncryptionAlgorithm.Cast128Ctr,
        SshEncryptionAlgorithm.Chacha20Poly1305);

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource
    void coveredSshKeyExchangeAlgorithm(final Map<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm,
            KeyExchangeFactory> map) {
        assertEquals(List.of(), Arrays.stream(SshKeyExchangeAlgorithm.values())
            .filter(alg -> !SUPPRESSED_KEY_EXCHANGE.contains(alg))
            .filter(alg -> !map.containsKey(TransportUtils.wrap(alg)))
            .toList());
    }

    private static List<Arguments> coveredSshKeyExchangeAlgorithm() {
        return List.of(
            arguments(named("client KEXs", TransportUtils.CLIENT_KEXS)),
            arguments(named("server KEXs", TransportUtils.SERVER_KEXS)));
    }

    // Keep the same order as in iana-ssh-key-exchange-algs.yang
    private static final Set<SshKeyExchangeAlgorithm> SUPPRESSED_KEY_EXCHANGE = Set.of(
        // FIXME: provide reasons for exclusion
        SshKeyExchangeAlgorithm.EcdhSha21313201,
        SshKeyExchangeAlgorithm.EcdhSha21284010045311,
        SshKeyExchangeAlgorithm.EcdhSha213132033,
        SshKeyExchangeAlgorithm.EcdhSha213132026,
        SshKeyExchangeAlgorithm.EcdhSha213132027,
        SshKeyExchangeAlgorithm.EcdhSha213132016,
        SshKeyExchangeAlgorithm.EcdhSha213132036,
        SshKeyExchangeAlgorithm.EcdhSha213132037,
        SshKeyExchangeAlgorithm.EcdhSha213132038,
        SshKeyExchangeAlgorithm.EcmqvSha2,
        SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp256,
        SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp384,
        SshKeyExchangeAlgorithm.GssGroup1Sha1Nistp521,
        SshKeyExchangeAlgorithm.GssGroup1Sha11313201,
        SshKeyExchangeAlgorithm.GssGroup1Sha11284010045311,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132033,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132026,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132027,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132016,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132036,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132037,
        SshKeyExchangeAlgorithm.GssGroup1Sha113132038,
        SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp256,
        SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp384,
        SshKeyExchangeAlgorithm.GssGroup14Sha1Nistp521,
        SshKeyExchangeAlgorithm.GssGroup14Sha11313201,
        SshKeyExchangeAlgorithm.GssGroup14Sha11284010045311,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132033,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132026,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132027,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132016,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132036,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132037,
        SshKeyExchangeAlgorithm.GssGroup14Sha113132038,
        SshKeyExchangeAlgorithm.GssGexSha1Nistp256,
        SshKeyExchangeAlgorithm.GssGexSha1Nistp384,
        SshKeyExchangeAlgorithm.GssGexSha1Nistp521,
        SshKeyExchangeAlgorithm.GssGexSha11313201,
        SshKeyExchangeAlgorithm.GssGexSha11284010045311,
        SshKeyExchangeAlgorithm.GssGexSha113132033,
        SshKeyExchangeAlgorithm.GssGexSha113132026,
        SshKeyExchangeAlgorithm.GssGexSha113132027,
        SshKeyExchangeAlgorithm.GssGexSha113132016,
        SshKeyExchangeAlgorithm.GssGexSha113132036,
        SshKeyExchangeAlgorithm.GssGexSha113132037,
        SshKeyExchangeAlgorithm.GssGexSha113132038,
        SshKeyExchangeAlgorithm.Gss,
        SshKeyExchangeAlgorithm.Rsa1024Sha1,
        SshKeyExchangeAlgorithm.Rsa2048Sha256,
        SshKeyExchangeAlgorithm.ExtInfoS,
        SshKeyExchangeAlgorithm.ExtInfoC,
        SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp256,
        SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp384,
        SshKeyExchangeAlgorithm.GssGroup14Sha256Nistp521,
        SshKeyExchangeAlgorithm.GssGroup14Sha2561313201,
        SshKeyExchangeAlgorithm.GssGroup14Sha2561284010045311,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132033,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132026,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132027,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132016,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132036,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132037,
        SshKeyExchangeAlgorithm.GssGroup14Sha25613132038,
        SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssGroup15Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssGroup15Sha5121313201,
        SshKeyExchangeAlgorithm.GssGroup15Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132033,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132026,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132027,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132016,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132036,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132037,
        SshKeyExchangeAlgorithm.GssGroup15Sha51213132038,
        SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssGroup16Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssGroup16Sha5121313201,
        SshKeyExchangeAlgorithm.GssGroup16Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132033,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132026,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132027,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132016,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132036,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132037,
        SshKeyExchangeAlgorithm.GssGroup16Sha51213132038,
        SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssGroup17Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssGroup17Sha5121313201,
        SshKeyExchangeAlgorithm.GssGroup17Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132033,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132026,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132027,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132016,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132036,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132037,
        SshKeyExchangeAlgorithm.GssGroup17Sha51213132038,
        SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssGroup18Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssGroup18Sha5121313201,
        SshKeyExchangeAlgorithm.GssGroup18Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132033,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132026,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132027,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132016,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132036,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132037,
        SshKeyExchangeAlgorithm.GssGroup18Sha51213132038,
        SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp256,
        SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp384,
        SshKeyExchangeAlgorithm.GssNistp256Sha256Nistp521,
        SshKeyExchangeAlgorithm.GssNistp256Sha2561313201,
        SshKeyExchangeAlgorithm.GssNistp256Sha2561284010045311,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132033,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132026,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132027,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132016,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132036,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132037,
        SshKeyExchangeAlgorithm.GssNistp256Sha25613132038,
        SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp256,
        SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp384,
        SshKeyExchangeAlgorithm.GssNistp384Sha384Nistp521,
        SshKeyExchangeAlgorithm.GssNistp384Sha3841313201,
        SshKeyExchangeAlgorithm.GssNistp384Sha3841284010045311,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132033,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132026,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132027,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132016,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132036,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132037,
        SshKeyExchangeAlgorithm.GssNistp384Sha38413132038,
        SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssNistp521Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssNistp521Sha5121313201,
        SshKeyExchangeAlgorithm.GssNistp521Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132033,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132026,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132027,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132016,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132036,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132037,
        SshKeyExchangeAlgorithm.GssNistp521Sha51213132038,
        SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp256,
        SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp384,
        SshKeyExchangeAlgorithm.GssCurve25519Sha256Nistp521,
        SshKeyExchangeAlgorithm.GssCurve25519Sha2561313201,
        SshKeyExchangeAlgorithm.GssCurve25519Sha2561284010045311,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132033,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132026,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132027,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132016,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132036,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132037,
        SshKeyExchangeAlgorithm.GssCurve25519Sha25613132038,
        SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp256,
        SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp384,
        SshKeyExchangeAlgorithm.GssCurve448Sha512Nistp521,
        SshKeyExchangeAlgorithm.GssCurve448Sha5121313201,
        SshKeyExchangeAlgorithm.GssCurve448Sha5121284010045311,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132033,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132026,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132027,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132016,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132036,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132037,
        SshKeyExchangeAlgorithm.GssCurve448Sha51213132038,
        SshKeyExchangeAlgorithm.Sntrup761x25519Sha512,
        SshKeyExchangeAlgorithm.Mlkem768nistp256Sha256,
        SshKeyExchangeAlgorithm.Mlkem1024nistp384Sha384,
        SshKeyExchangeAlgorithm.Mlkem768x25519Sha256);

    @Test
    void coveredSshMacAlgorithm() {
        assertEquals(List.of(), Arrays.stream(SshMacAlgorithm.values())
            .filter(alg -> !SUPPRESSED_MAC.contains(alg))
            .filter(alg -> !TransportUtils.MACS.containsKey(TransportUtils.wrap(alg)))
            .toList());
    }

    // Keep the same order as in iana-ssh-mac-algs.yang
    private static final Set<SshMacAlgorithm> SUPPRESSED_MAC = Set.of(
        // FIXME: provide reasons for exclusion
        SshMacAlgorithm.None,
        SshMacAlgorithm.AEADAES128GCM,
        SshMacAlgorithm.AEADAES256GCM);

    @Test
    void coveredPublicKeyAlgorithm() {
        assertEquals(List.of(), Arrays.stream(SshPublicKeyAlgorithm.values())
            .filter(alg -> !SUPPRESSED_PUBLIC_KEY.contains(alg))
            .filter(alg -> !TransportUtils.SIGNATURES.containsKey(TransportUtils.wrap(alg)))
            .toList());
    }

    // Keep the same order as in iana-ssh-public-key-algs.yang
    private static final Set<SshPublicKeyAlgorithm> SUPPRESSED_PUBLIC_KEY = Set.of(
        // FIXME: provide reasons for exclusion
         SshPublicKeyAlgorithm.SpkiSignRsa,
         SshPublicKeyAlgorithm.SpkiSignDss,
         SshPublicKeyAlgorithm.PgpSignRsa,
         SshPublicKeyAlgorithm.PgpSignDss,
         SshPublicKeyAlgorithm.Null,
         SshPublicKeyAlgorithm.EcdsaSha21313201,
         SshPublicKeyAlgorithm.EcdsaSha21284010045311,
         SshPublicKeyAlgorithm.EcdsaSha213132033,
         SshPublicKeyAlgorithm.EcdsaSha213132026,
         SshPublicKeyAlgorithm.EcdsaSha213132027,
         SshPublicKeyAlgorithm.EcdsaSha213132016,
         SshPublicKeyAlgorithm.EcdsaSha213132036,
         SshPublicKeyAlgorithm.EcdsaSha213132037,
         SshPublicKeyAlgorithm.EcdsaSha213132038,
         SshPublicKeyAlgorithm.X509v3SshDss,
         SshPublicKeyAlgorithm.X509v3SshRsa,
         SshPublicKeyAlgorithm.X509v3Rsa2048Sha256,
         SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp256,
         SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp384,
         SshPublicKeyAlgorithm.X509v3EcdsaSha2Nistp521,
         SshPublicKeyAlgorithm.X509v3EcdsaSha21313201,
         SshPublicKeyAlgorithm.X509v3EcdsaSha21284010045311,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132033,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132026,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132027,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132016,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132036,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132037,
         SshPublicKeyAlgorithm.X509v3EcdsaSha213132038,
         SshPublicKeyAlgorithm.SshEd448);
}
