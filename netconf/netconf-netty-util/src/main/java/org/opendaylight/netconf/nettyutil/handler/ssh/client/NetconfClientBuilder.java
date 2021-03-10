/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static com.google.common.base.Verify.verify;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;

/**
 * A {@link ClientBuilder} which builds {@link NetconfSshClient} instances.
 */
@Beta
public class NetconfClientBuilder extends ClientBuilder {
    // RFC8332 rsa-sha2-256/rsa-sha2-512 are not a part of Mina's default set of signatures for clients as of 2.5.1.
    // Add them to ensure interop with modern highly-secured devices.
    private static final ImmutableList<NamedFactory<Signature>> FULL_SIGNATURE_PREFERENCE =
            Streams.concat(DEFAULT_SIGNATURE_PREFERENCE.stream(), Arrays.asList(
                BuiltinSignatures.rsaSHA512, BuiltinSignatures.rsaSHA256).stream())
            .filter(BuiltinSignatures::isSupported)
            .distinct()
            .collect(ImmutableList.<NamedFactory<Signature>>toImmutableList());

    // The SHA1 algorithm is disabled by default in Mina SSHD since 2.6.0.
    // More details available here: https://issues.apache.org/jira/browse/SSHD-1004
    // This block adds diffie-hellman-group14-sha1 back to the list of supported algorithms.
    private static final ImmutableList<BuiltinDHFactories> FULL_DH_FACTORIES_LIST =
        Streams.concat(DEFAULT_KEX_PREFERENCE.stream(), Stream.of(BuiltinDHFactories.dhg14))
            .collect(ImmutableList.toImmutableList());
    private static final List<KeyExchangeFactory> FULL_KEX_PREFERENCE =
        NamedFactory.setUpTransformedFactories(true, FULL_DH_FACTORIES_LIST, DH2KEX);

    @Override
    public NetconfSshClient build() {
        final SshClient client = super.build();
        verify(client instanceof NetconfSshClient, "Unexpected client %s", client);
        return (NetconfSshClient) client;
    }

    @Override
    protected ClientBuilder fillWithDefaultValues() {
        if (factory == null) {
            factory = NetconfSshClient.DEFAULT_NETCONF_SSH_CLIENT_FACTORY;
        }
        if (signatureFactories == null) {
            signatureFactories = FULL_SIGNATURE_PREFERENCE;
        }
        if (keyExchangeFactories == null) {
            keyExchangeFactories = FULL_KEX_PREFERENCE;
        }
        return super.fillWithDefaultValues();
    }
}
