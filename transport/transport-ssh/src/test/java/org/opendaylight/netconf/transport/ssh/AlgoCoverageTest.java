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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm;
import org.opendaylight.yangtools.binding.TypeObject;

/**
 * This is a test for the four aspects of SSH crypto covered by YANG models:
 * <ul>
 *   <li>{@link SshEncryptionAlgorithm}</li>
 *   <li>{@link SshKeyExchangeAlgorithm}</li>
 *   <li>{@link SshMacExchangeAlgorithm}</li>
 *   <li>{@link SshPublicKeyAlgorithm}</li>
 * </ul>
 * and our coverage in {@link TransportUtils}' lookup maps, so that all known enumeration values are either covered in
 * the lookup map or explictly suppressed. This provides a guard against new entries appearing and us failing to
 * implement them.
 *
 * <p>Note that there are two-way assertions to suppressions, so that we are forced to keep them consistent.
*/
class AlgoCoverageTest {
    @Test
    void coveredSshEncryptionAlgorithm() {
        assertAll(alg -> EncryptionAlgorithms.BY_YANG.containsKey(EncryptionAlgorithms.keyOf(alg)),
            SshEncryptionAlgorithm.values(),
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
            SshEncryptionAlgorithm.Cast128Ctr);
    }

    @Test
    void coveredSshMacAlgorithm() {
        assertAll(alg -> MacAlgorithms.BY_YANG.containsKey(MacAlgorithms.keyOf(alg)), SshMacAlgorithm.values(),
            SshMacAlgorithm.None,
            // forbidden in https://www.ietf.org/archive/id/draft-miller-sshm-aes-gcm-00.html#section-2
            SshMacAlgorithm.AEADAES128GCM,
            SshMacAlgorithm.AEADAES256GCM);
    }

    @Test
    void coveredPublicKeyAlgorithm() {
        assertAll(alg -> PublicKeyAlgorithms.BY_YANG.containsKey(PublicKeyAlgorithms.keyOf(alg)),
                SshPublicKeyAlgorithm.values(),
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

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource
    void coveredSshKeyExchangeAlgorithm(final BuiltinKeyExchangePolicy policy) {
        assertAll(alg -> policy.factoryFor(alg) != null, SshKeyExchangeAlgorithm.values(),
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
            SshKeyExchangeAlgorithm.GssCurve448Sha51213132038);
    }

    private static List<Arguments> coveredSshKeyExchangeAlgorithm() {
        return List.of(
            arguments(named("client KEXs", BuiltinKeyExchangePolicy.CLIENT)),
            arguments(named("server KEXs", BuiltinKeyExchangePolicy.SERVER)));
    }

    // The meat of assertions. Yes we could use Parameterized tests, but this way is less meta.
    @SafeVarargs
    private static <T extends TypeObject> void assertAll(final Predicate<T> predicate, final T[] values,
            final T... suppressions) {
        final var unsuppressed = new HashSet<>(List.of(suppressions));
        final var unmatched = new ArrayList<T>();

        for (var alg : values) {
            if (!predicate.test(alg) && !unsuppressed.remove(alg)) {
                unmatched.add(alg);
            }
        }

        assertEquals(List.of(), unmatched, "Unimplemented algorithms");
        assertEquals(Set.of(), unsuppressed, "Unused algorithm suppressions");
    }
}
