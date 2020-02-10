/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.callhome.mount.CallHomeMountSessionContext.CloseCallback;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeMountSessionManager implements CallHomeMountSessionContext.CloseCallback {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountSessionManager.class);
    private final ConcurrentMap<SocketAddress, CallHomeMountSessionContext> contextByAddress =
        new ConcurrentHashMap<>();
    private final Multimap<PublicKey, CallHomeMountSessionContext> contextByPublicKey = MultimapBuilder.hashKeys()
        .hashSetValues().build();

    public @Nullable CallHomeMountSessionContext getByAddress(InetSocketAddress remoteAddr) {
        return contextByAddress.get(remoteAddr);
    }


    public @NonNull Collection<CallHomeMountSessionContext> getByPublicKey(PublicKey publicKey) {
        return contextByPublicKey.get(publicKey);
    }

    CallHomeMountSessionContext createSession(CallHomeProtocolSessionContext session,
                                              CallHomeChannelActivator activator, final CloseCallback onCloseHandler) {

        String name = session.getSessionName();
        CallHomeMountSessionContext deviceContext = new CallHomeMountSessionContext(name,
                session, activator, devCtxt -> {
            CallHomeMountSessionManager.this.onClosed(devCtxt);
            onCloseHandler.onClosed(devCtxt);
        });


        /*check if the sshkey of the incoming netconf server is present.
         * If present return null,
         * else store the session.
         * The sshkey is the uniqueness of the callhome sessions not the
         * uniqueid/devicename.
         */
        if (! contextByPublicKey.containsKey(deviceContext.getRemoteServerKey())) {
            contextByPublicKey.put(deviceContext.getRemoteServerKey(), deviceContext);
            contextByAddress.put(deviceContext.getRemoteAddress(), deviceContext);
            return deviceContext;
        }
        else {
            return null;
        }
    }

    @Override
    public synchronized void onClosed(CallHomeMountSessionContext deviceContext) {
        contextByAddress.remove(deviceContext.getRemoteAddress());
        contextByPublicKey.remove(deviceContext.getRemoteServerKey(), deviceContext);
    }
}
