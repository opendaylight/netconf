/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import com.google.common.annotations.Beta;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;

/**
 * An extension to {@link SshClient} which uses {@link NetconfSessionFactory} to create sessions (leading towards
 * {@link NetconfClientSessionImpl}.
 */
@Beta
public class NetconfSshClient extends SshClient {
    @Override
    protected NetconfSessionFactory createSessionFactory() {
        return new NetconfSessionFactory(this);
    }
}
