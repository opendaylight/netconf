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
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.signature.BuiltinSignatures;
import org.opendaylight.netconf.shaded.sshd.common.signature.Signature;

/**
 * A {@link ClientBuilder} which builds {@link NetconfSshClient} instances.
 */
@Beta
public class NetconfClientBuilder extends ClientBuilder {
    private static final Set<BuiltinSignatures> FULL_SIGNATURE_PREFERENCE = ImmutableSet.<BuiltinSignatures>builder()
            .addAll(DEFAULT_SIGNATURE_PREFERENCE)
            .add(BuiltinSignatures.rsaSHA512_cert).add(BuiltinSignatures.rsaSHA256_cert)
            .add(BuiltinSignatures.rsaSHA512).add(BuiltinSignatures.rsaSHA256)
            .build();

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
            signatureFactories = setUpFullSignatureFactories();
        }
        return super.fillWithDefaultValues();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<NamedFactory<Signature>> setUpFullSignatureFactories() {
        return (List) NamedFactory.setUpBuiltinFactories(false, FULL_SIGNATURE_PREFERENCE);
    }
}
