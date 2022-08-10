/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A transport-level session. This concept is bound to a {@link Channel} for now, so as to enforce type-safety. It acts
 * as a meeting point between a logical NETCONF session and the underlying transport.
 */
public abstract class TransportChannel {
    /**
     * Return the underlying Netty channel.
     *
     * @return Netty channel
     */
    public abstract @NonNull Channel channel();

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("channel", channel());
    }
}
