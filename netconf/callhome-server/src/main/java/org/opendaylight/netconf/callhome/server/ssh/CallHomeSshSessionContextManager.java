/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import java.net.SocketAddress;
import java.util.Map;
import org.opendaylight.netconf.callhome.server.AbstractCallHomeSessionContextManager;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

public class CallHomeSshSessionContextManager extends AbstractCallHomeSessionContextManager<CallHomeSshSessionContext> {

    @Override
    public CallHomeSshSessionContext findByChannel(final Channel channel) {
        requireNonNull(channel);
        return channel.isOpen() ? findByAddress(channel.remoteAddress()) : null;
    }

    private CallHomeSshSessionContext findByAddress(final SocketAddress address) {
        return contexts.entrySet().stream()
            .filter(entry -> address.equals(entry.getValue().remoteAddress()))
            .findFirst().map(Map.Entry::getValue).orElse(null);
    }

    public CallHomeSshSessionContext findBySshSession(final ClientSession clientSession) {
        requireNonNull(clientSession);
        return contexts.entrySet().stream()
            .filter(entry -> clientSession == entry.getValue().sshSession())
            .findFirst().map(Map.Entry::getValue).orElse(null);
    }

    public CallHomeSshSessionContext createContext(final String id, final ClientSession clientSession) {
        return new CallHomeSshSessionContext(id, clientSession.getRemoteAddress(), clientSession,
            new SimpleNetconfClientSessionListener());
    }
}
