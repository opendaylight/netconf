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
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;

/**
 * Mapping of supported MAC algorithms, mostly as maintained by IANA in
 * <a href="https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml">MAC Algorithm Names</a>.
 */
final class MacPolicy extends AlgorithmPolicy<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
        NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> {
    static final @NonNull MacPolicy INSTANCE = new MacPolicy(Map.ofEntries(
        // Keep the same order as in iana-ssh-mac-algs.yang

        // FIXME: audit commented-out algorithms missing in BuiltinMacs or provide justification for exclusion

        // required in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
        entry(SshMacAlgorithm.HmacSha1, BuiltinMacs.hmacsha1),
        // recommended in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
        entry(SshMacAlgorithm.HmacSha196, BuiltinMacs.hmacsha196),
        // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
        entry(SshMacAlgorithm.HmacMd5, BuiltinMacs.hmacmd5),
        // optional in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
        entry(SshMacAlgorithm.HmacMd596, BuiltinMacs.hmacmd596),

        // not recommended in https://www.rfc-editor.org/rfc/rfc4253#section-6.4
        // FIXME: consistency: we DO NOT provide this, but DO provide provide SshEncryptionAlgorithm.None
        // SshMacAlgorithm.None

        // defined in https://www.rfc-editor.org/rfc/rfc5647
        // forbidden in https://www.ietf.org/archive/id/draft-miller-sshm-aes-gcm-00.html#section-2
        // SshMacAlgorithm.AEADAES128GCM
        // SshMacAlgorithm.AEADAES256GCM

        // recommended in https://www.rfc-editor.org/rfc/rfc6668#section-2
        entry(SshMacAlgorithm.HmacSha2256, BuiltinMacs.hmacsha256),
        // recommended in https://www.rfc-editor.org/rfc/rfc6668#section-2
        entry(SshMacAlgorithm.HmacSha2512, BuiltinMacs.hmacsha512)),
        BaseBuilder.DEFAULT_MAC_PREFERENCE);

    private MacPolicy(final Map<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
            MacFactory> typeToFactory, final List<? extends MacFactory> defaultFactories) {
        super(typeToFactory, defaultFactories);
    }

    private static Entry<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
            MacFactory> entry(final SshMacAlgorithm alg, final MacFactory factory) {
        return Map.entry(keyOf(alg), factory);
    }

    @VisibleForTesting
    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshMacAlgorithm keyOf(final SshMacAlgorithm alg) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010
            .SshMacAlgorithm(alg);
    }

    @Override
    List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm> algsOf(
            final TransportParamsGrouping params) {
        final var mac = params.getMac();
        return mac == null ? null : mac.getMacAlg();
    }

    @Override
    void setFactories(final BaseBuilder<?, ?> builder,
            final List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> factories) {
        builder.macFactories(factories);
    }
}
