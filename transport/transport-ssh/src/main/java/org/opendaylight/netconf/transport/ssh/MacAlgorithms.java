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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev241016.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.Mac;

/**
 * Mapping of supported MAC algorithms, mostly as maintained by IANA in
 * <a href="https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml">MAC Algorithm Names</a>.
 */
final class MacAlgorithms {
    @VisibleForTesting
    static final Map<
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm,
        // Corresponds to MAC Algorithm Names in
        // https://www.iana.org/assignments/ssh-parameters/ssh-parameters.xhtml
        MacFactory> BY_YANG = Map.ofEntries(
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

    private static final List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> DEFAULT_FACTORIES =
        List.copyOf(BaseBuilder.DEFAULT_MAC_PREFERENCE);

    private MacAlgorithms() {
        // Hidden on purpose
    }

    static List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> factoriesFor(
            final @Nullable Mac mac) throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null && !macAlg.isEmpty()) {
                return ConfigUtils.mapValues(BY_YANG, macAlg, "Unsupported MAC algorithm %s");
            }
        }
        return DEFAULT_FACTORIES;
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
}
