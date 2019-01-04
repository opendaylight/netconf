/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

/**
 * A factory class creating SessionNegotiators.
 *
 * @param <S> session type
 */
@Deprecated
public interface NetconfSessionNegotiatorFactory<S extends NetconfSession, L extends NetconfSessionListener<?>> {
    /**
     * Create a new negotiator attached to a channel, which will notify
     * a promise once the negotiation completes.
     *
     * @param channel Underlying channel
     * @param promise Promise to be notified
     * @return new negotiator instance
     */
    NetconfSessionNegotiator<S> getSessionNegotiator(NetconfSessionListenerFactory<L> factory, Channel channel,
            Promise<S> promise);
}
