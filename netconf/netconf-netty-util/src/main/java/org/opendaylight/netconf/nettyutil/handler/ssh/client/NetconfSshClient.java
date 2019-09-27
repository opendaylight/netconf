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

/**
 * An extension to {@link SshClient} which uses {@link NetconfSessionFactory} to create sessions (leading towards
 * {@link NetconfClientSessionImpl}.
 */
@Beta
public class NetconfSshClient extends SshClient {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = NetconfSshClient::new;

    /*
     * This is a workaround for sshd-core's absurd use of Proxies. AbstractFactoryManager (which is our superclass)
     * is calling Proxy.newProxyInstance() with getClass().getClassLoader(), i.e. our class loader.
     *
     * Since we are not using PortForwardingEventListener, our classloader does not see it (because we do not import
     * that package), which leads to a Proxy instance failure.
     *
     * Having this dumb reference alleviates the problem, as it forces org.apache.sshd.common.forward to be imported
     * and hence resolvable by our class loader.
     *
     * FIXME: Remove this once we have an SSHD version with  https://issues.apache.org/jira/browse/SSHD-975 fixed
     */
    static final PortForwardingEventListener OSGI_CLASSLOADER_WORKAROUND = null;

    @Override
    protected NetconfSessionFactory createSessionFactory() {
        return new NetconfSessionFactory(this);
    }
}
