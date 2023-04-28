/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import io.netty.channel.Channel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

class TestingNetconfSession
        extends AbstractNetconfSession<TestingNetconfSession, NetconfSessionListener<TestingNetconfSession>> {
    TestingNetconfSession(final NetconfSessionListener<TestingNetconfSession> sessionListener,
                          final Channel channel, final SessionIdType sessionId) {
        super(sessionListener, channel, sessionId);
    }

    @Override
    protected TestingNetconfSession thisInstance() {
        return this;
    }

    @Override
    protected void addExiHandlers(final ByteToMessageDecoder decoder,
                                  final MessageToByteEncoder<NetconfMessage> encoder) {
    }

    @Override
    public void stopExiCommunication() {
    }
}
