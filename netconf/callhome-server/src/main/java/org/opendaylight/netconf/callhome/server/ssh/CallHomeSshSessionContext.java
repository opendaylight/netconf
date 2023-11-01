/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.SettableFuture;
import java.net.SocketAddress;
import org.opendaylight.netconf.callhome.server.CallHomeSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

public record CallHomeSshSessionContext(String id, SocketAddress remoteAddress, ClientSession sshSession,
        NetconfClientSessionListener netconfSessionListener, SettableFuture<NetconfClientSession> settableFuture)
        implements CallHomeSessionContext {

    public CallHomeSshSessionContext {
        requireNonNull(id);
        requireNonNull(remoteAddress);
        requireNonNull(sshSession);
        requireNonNull(netconfSessionListener);
        requireNonNull(settableFuture);
    }

    @Override
    public void close() {
        if (sshSession.isOpen()) {
            sshSession.close(true);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("protocol", "SSH")
            .add("id", id)
            .add("address", remoteAddress)
            .toString();
    }
}
