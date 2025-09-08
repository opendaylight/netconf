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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.cipher.Cipher;
import org.opendaylight.netconf.shaded.sshd.common.cipher.CipherFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev241016.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;

/**
 * Mapping of supported encryption algorithms, mostly as maintained by IANA in
 * <a href="https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml">Encryption Algorithm Names</a>.
 */
final class EncryptionPolicy extends AlgorithmPolicy<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm,
        NamedFactory<Cipher>> {

    static final @NonNull EncryptionPolicy INSTANCE = new EncryptionPolicy(Map.ofEntries(
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
        // FIXME: consistency: we DO provide this, but DO NOT provide SshMacAlgorithm.None
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
        // negotiated as per https://datatracker.ietf.org/doc/draft-miller-sshm-aes-gcm/
        entry(SshEncryptionAlgorithm.AEADAES128GCM, BuiltinCiphers.aes128gcm),
        entry(SshEncryptionAlgorithm.AEADAES256GCM, BuiltinCiphers.aes256gcm),

        // defined in https://datatracker.ietf.org/doc/draft-ietf-sshm-chacha20-poly1305/
        entry(SshEncryptionAlgorithm.Chacha20Poly1305, BuiltinCiphers.cc20p1305_openssh)),
        BaseBuilder.DEFAULT_CIPHERS_PREFERENCE);

    private EncryptionPolicy(final Map<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm,
            CipherFactory> typeToFactory, final List<? extends CipherFactory> defaultFactories) {
        super(typeToFactory, defaultFactories);
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm,
            CipherFactory> entry(final SshEncryptionAlgorithm alg, final CipherFactory factory) {
        return Map.entry(keyOf(alg), factory);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshEncryptionAlgorithm keyOf(final SshEncryptionAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshEncryptionAlgorithm(alg);
    }

    @Override
    List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm>
            algsOf(final TransportParamsGrouping params) {
        final var encryption = params.getEncryption();
        return encryption == null ? null : encryption.getEncryptionAlg();
    }

    @Override
    void setFactories(final BaseBuilder<?, ?> builder, final List<NamedFactory<Cipher>> factories) {
        builder.cipherFactories(factories);
    }
}
