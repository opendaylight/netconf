/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.net.SocketAddress;
import java.security.PublicKey;
import javax.annotation.Nonnull;

/**
 *
 * Provider responsible for resolving CallHomeAuthorization
 *
 */

public interface CallHomeAuthorizationProvider {

    /**
     *
     * Provides authorization parameters for incoming call-home connection.
     *
     * @param remoteAddress Remote socket address of incoming connection
     * @param serverKey SSH key provided by SSH server on incoming connection
     *
     * @return {@link CallHomeAuthorization} with authorization information.
     *
     */
    @Nonnull
    public CallHomeAuthorization provideAuth(SocketAddress remoteAddress, PublicKey serverKey);

}
