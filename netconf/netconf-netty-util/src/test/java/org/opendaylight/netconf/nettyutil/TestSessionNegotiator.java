/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.HelloMessage;

final class TestSessionNegotiator
        extends AbstractNetconfSessionNegotiator<TestingNetconfSession, NetconfSessionListener<TestingNetconfSession>> {
    TestSessionNegotiator(final HelloMessage hello, final Promise<TestingNetconfSession> promise,
            final Channel channel, final Timer timer,
            final NetconfSessionListener<TestingNetconfSession> sessionListener, final long connectionTimeoutMillis) {
        super(hello, promise, channel, timer, sessionListener, connectionTimeoutMillis, 16384);
    }

    @Override
    protected TestingNetconfSession getSession(final NetconfSessionListener<TestingNetconfSession> sessionListener,
            final Channel channel, final HelloMessage message) {
        return new TestingNetconfSession(sessionListener, channel, 0L);
    }

    @Override
    protected void handleMessage(final HelloMessage netconfHelloMessage) {
        // No-op
    }
}