/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import com.google.common.annotations.Beta;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;

/**
 * An extension to {@link SshClient} which uses {@link NetconfSessionFactory} to create sessions (leading towards
 * {@link NetconfClientSessionImpl}.
 */
@Beta
public class NetconfSshClient extends SshClient {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = NetconfSshClient::new;

    /*
     * This is a workaround for sshd-core's instantiation of Proxies. AbstractFactoryManager (which is our superclass)
     * is calling Proxy.newProxyInstance() with getClass().getClassLoader(), i.e. our class loader.
     *
     * Since we are not using PortForwardingEventListener, our classloader does not see it (because we do not import
     * that package), which leads to an instantiation failure.
     *
     * Having these dumb fields alleviates the problem, as it forces the packages to be imported by our bundle.
     *
     * FIXME: Remove this once we have an SSHD version with  https://issues.apache.org/jira/browse/SSHD-975 fixed
     */
    static final class Sshd975Workarounds {
        static final PortForwardingEventListener PFEL = null;
        static final SshdSocketAddress SSA = null;
    }

    @Override
    protected NetconfSessionFactory createSessionFactory() {
        return new NetconfSessionFactory(this);
    }
}
