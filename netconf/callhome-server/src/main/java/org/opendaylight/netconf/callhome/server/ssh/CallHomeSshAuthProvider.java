/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import java.net.SocketAddress;
import java.security.PublicKey;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Provider responsible for resolving Call-Home SSH authorization.
 */
public interface CallHomeSshAuthProvider {
    /**
     * Provides auth parameters for incoming call-home connection.
     *
     * @param remoteAddress Remote socket address of incoming connection
     * @param serverKey     SSH key provided by SSH server on incoming connection
     * @return {@link CallHomeSshAuthSettings} instance if there are settings associated with incoming connection,
     *     {@code null} otherwise.
     */
    @Nullable CallHomeSshAuthSettings provideAuth(@NonNull SocketAddress remoteAddress, @NonNull PublicKey serverKey);
}
