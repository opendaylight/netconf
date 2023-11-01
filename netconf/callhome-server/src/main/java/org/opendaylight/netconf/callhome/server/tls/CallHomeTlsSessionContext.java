/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import io.netty.channel.Channel;
import org.opendaylight.netconf.callhome.server.CallHomeSessionContext;
import org.opendaylight.netconf.client.NetconfClientSessionListener;

public record CallHomeTlsSessionContext(String id, Channel nettyChannel,
        NetconfClientSessionListener netconfSessionListener) implements CallHomeSessionContext {

    public CallHomeTlsSessionContext {
        requireNonNull(id);
        requireNonNull(nettyChannel);
        requireNonNull(netconfSessionListener);
    }

    @Override
    public void close() {
        if (nettyChannel.isOpen()) {
            nettyChannel.close();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("protocol", "TLS")
            .add("id", id)
            .add("address", nettyChannel.remoteAddress())
            .toString();
    }
}
