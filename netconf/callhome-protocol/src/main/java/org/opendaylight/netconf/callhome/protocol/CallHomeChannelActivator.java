/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import io.netty.util.concurrent.Promise;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;

/**
 * Activator of NETCONF channel on incoming SSH Call Home session.
 *
 */
public interface CallHomeChannelActivator {
    /**
     *
     * Activates Netconf Client Channel with supplied client session listener.
     *
     * Activation of channel will result in start of NETCONF client
     * session negotiation on underlying ssh channel.
     *
     * @param listener Client Session Listener to be attached to NETCONF session.
     * @return Promise with negotiated NETCONF session
     */
    @Nonnull
    Promise<NetconfClientSession> activate(@Nonnull NetconfClientSessionListener listener);
}
