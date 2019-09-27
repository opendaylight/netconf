/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import com.google.common.annotations.Beta;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.SessionFactory;
import org.apache.sshd.common.Factory;

@Beta
public class NetconfSshClient extends SshClient {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = NetconfSshClient::new;

    @Override
    protected SessionFactory createSessionFactory() {
        return new NetconfSessionFactory(this);
    }
}
