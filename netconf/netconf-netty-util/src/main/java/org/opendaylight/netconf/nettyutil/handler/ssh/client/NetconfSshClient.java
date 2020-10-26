/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import com.google.common.annotations.Beta;
import org.opendaylight.netconf.nettyutil.handler.ssh.sshd1028.NetconfNio2ServiceFactoryFactory;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.common.Factory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoConnector;


/**
 * An extension to {@link SshClient} which uses {@link NetconfSessionFactory} to create sessions (leading towards
 * {@link NetconfClientSessionImpl}.
 */
@Beta
public class NetconfSshClient extends SshClient {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = NetconfSshClient::new;
    private final NetconfNio2ServiceFactoryFactory nio2ServiceFactoryFactory;

    public NetconfSshClient() {
        this.nio2ServiceFactoryFactory = new NetconfNio2ServiceFactoryFactory();
    }

    @Override
    protected NetconfSessionFactory createSessionFactory() {
        return new NetconfSessionFactory(this);
    }

    @Override
    protected IoConnector createConnector() {
        setIoServiceFactoryFactory(nio2ServiceFactoryFactory);
        return getIoServiceFactory().createConnector(getSessionFactory());
    }
}
