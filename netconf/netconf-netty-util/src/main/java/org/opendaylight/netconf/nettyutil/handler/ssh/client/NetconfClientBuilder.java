/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static com.google.common.base.Verify.verify;

import com.google.common.annotations.Beta;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;

/**
 * A {@link ClientBuilder} which builds {@link NetconfSshClient} instances.
 */
@Beta
public class NetconfClientBuilder extends ClientBuilder {
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
        return super.fillWithDefaultValues();
    }
}
