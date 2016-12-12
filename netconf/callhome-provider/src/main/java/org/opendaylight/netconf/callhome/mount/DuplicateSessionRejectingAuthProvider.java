/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import java.net.SocketAddress;
import java.security.PublicKey;

import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;

public class DuplicateSessionRejectingAuthProvider implements CallHomeAuthorizationProvider {

    private final CallHomeMountSessionManager session;
    private final CallHomeAuthorizationProvider delegate;

    public DuplicateSessionRejectingAuthProvider(CallHomeMountSessionManager session,
            CallHomeAuthorizationProvider delegate) {
        this.session = session;
        this.delegate = delegate;
    }



    @Override
    public CallHomeAuthorization provideAuth(SocketAddress remoteAddress, PublicKey serverKey) {
        if(session.getByPublicKey(serverKey).isEmpty()) {
            return delegate.provideAuth(remoteAddress, serverKey);
        }
        return CallHomeAuthorization.rejected();
    }


}
