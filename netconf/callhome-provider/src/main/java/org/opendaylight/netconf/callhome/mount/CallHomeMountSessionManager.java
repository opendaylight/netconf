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
import javax.annotation.Nullable;
import org.opendaylight.netconf.callhome.mount.CallHomeMountSessionContext.CloseCallback;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;

public class CallHomeMountSessionManager implements CallHomeMountSessionContext.CloseCallback {

    private final ConcurrentMap<SocketAddress, CallHomeMountSessionContext> contextByAddress = new ConcurrentHashMap<>();
    private final Multimap<PublicKey, CallHomeMountSessionContext> contextByPublicKey = MultimapBuilder.hashKeys().hashSetValues().build();
    private final CallHomeSessionAliasProvider aliasProvider = SocketAddressAsAlias.INSTANCE;

    @Nullable
    public CallHomeMountSessionContext getByAddress(InetSocketAddress remoteAddr) {
        return contextByAddress.get(remoteAddr);
    }

    @Nullable
    public Collection<CallHomeMountSessionContext> getByPublicKey(PublicKey publicKey) {
        return contextByPublicKey.get(publicKey);
    }

    CallHomeMountSessionContext createSession(CallHomeProtocolSessionContext session,
            CallHomeChannelActivator activator, final CloseCallback onCloseHandler) {

        String name = aliasProvider.getAlias(session);
        CallHomeMountSessionContext deviceContext = new CallHomeMountSessionContext(name, session, activator, new CloseCallback() {

            @Override
            public void onClosed(CallHomeMountSessionContext deviceContext) {
                CallHomeMountSessionManager.this.onClosed(deviceContext);
                onCloseHandler.onClosed(deviceContext);
            }

        });
        contextByAddress.put(deviceContext.getRemoteAddress(), deviceContext);
        contextByPublicKey.put(deviceContext.getRemoteServerKey(), deviceContext);

        return deviceContext;
    }

    @Override
    public synchronized void onClosed(CallHomeMountSessionContext deviceContext) {
        contextByAddress.remove(deviceContext);
        contextByPublicKey.remove(deviceContext.getRemoteServerKey(),deviceContext);
    }

}
