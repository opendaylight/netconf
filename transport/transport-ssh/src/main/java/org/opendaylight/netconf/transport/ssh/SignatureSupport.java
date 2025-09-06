/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;
import org.opendaylight.netconf.shaded.sshd.common.signature.SignatureFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh._public.key.algs.rev241016.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;

/**
 * Mapping of supported public key algorithms, mostly as maintained by IANA in
 * <a href="https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml">Public Key Algorithm Names</a>.
 */
final class SignatureSupport extends AlgorithmSupport<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm,
        NamedFactory<Signature>> {
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm,
        NamedFactory<Signature>> BY_YANG = Map.ofEntries(
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

    private SignatureSupport(final List<NamedFactory<Signature>> defaultFactories) {
        super(BY_YANG, defaultFactories);
    }

    @Override
    List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm>
            algsOf(final TransportParamsGrouping params) {
        final var hostKey = params.getHostKey();
        return hostKey == null ? null : hostKey.getHostKeyAlg();
    }

    @Override
    void setFactories(final BaseBuilder<?, ?> builder, final List<NamedFactory<Signature>> factories) {
        builder.signatureFactories(factories);
    }
//
//    static List<NamedFactory<Signature>> factoriesFor(final @Nullable HostKey hostKey)
//            throws UnsupportedConfigurationException {
//        if (hostKey != null) {
//            final var hostKeyAlg = hostKey.getHostKeyAlg();
//            if (hostKeyAlg != null && hostKeyAlg.isEmpty()) {
//                return ConfigUtils.mapValues(BY_YANG, hostKeyAlg, "Unsupported Host Key algorithm %s");
//            }
//        }
//        return DEFAULT_SIGNATURES;
//    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm,
            SignatureFactory> entry(final SshPublicKeyAlgorithm alg, final SignatureFactory factory) {
        return Map.entry(keyOf(alg), factory);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshPublicKeyAlgorithm keyOf(final SshPublicKeyAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshPublicKeyAlgorithm(alg);
    }
}
