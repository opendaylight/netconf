/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.callhome.mount.CallHomeMountSessionContext.CloseCallback;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for testing
class CallHomeMountSessionManager implements CallHomeMountSessionContext.CloseCallback {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountSessionManager.class);

    private final ConcurrentMap<SocketAddress, CallHomeMountSessionContext> contextByAddress =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<PublicKey, CallHomeMountSessionContext> contextByPublicKey = new ConcurrentHashMap<>();

    @Nullable CallHomeMountSessionContext getByAddress(final InetSocketAddress remoteAddr) {
        return contextByAddress.get(remoteAddr);
    }

    CallHomeMountSessionContext createSession(final CallHomeProtocolSessionContext session,
            final CallHomeChannelActivator activator, final CloseCallback onCloseHandler) {
        final CallHomeMountSessionContext deviceContext = new CallHomeMountSessionContext(session.getSessionId(),
            session, activator, devCtxt -> onClosed(devCtxt, onCloseHandler));

        final PublicKey remoteKey = session.getRemoteServerKey();
        final CallHomeMountSessionContext existing = contextByPublicKey.putIfAbsent(remoteKey, deviceContext);
        if (existing != null) {
            // Check if the sshkey or certificate of the incoming netconf server is present. If present return null,
            // else store the session. The sshkey/certificate is the uniqueness of the callhome sessions not the
            // uniqueid/devicename
            LOG.error("Server Host Key/Certificate {} is associated with existing session {}, closing session {}",
                remoteKey, existing, session);
            session.terminate();
            return null;
        }

        final SocketAddress remoteAddress = session.getRemoteAddress();
        final CallHomeMountSessionContext prev = contextByAddress.put(remoteAddress, deviceContext);
        if (prev != null) {
            LOG.warn("Remote {} replaced context {} with {}", remoteAddress, prev, deviceContext);
        }
        return deviceContext;
    }

    @Override
    public void onClosed(final CallHomeMountSessionContext deviceContext) {
        final CallHomeProtocolSessionContext session = deviceContext.getProtocol();
        contextByAddress.remove(session.getRemoteAddress(), deviceContext);
        contextByPublicKey.remove(session.getRemoteServerKey(), deviceContext);
    }

    private void onClosed(final CallHomeMountSessionContext deviceContext, final CloseCallback onCloseHandler) {
        onClosed(deviceContext);
        onCloseHandler.onClosed(deviceContext);
    }
}
