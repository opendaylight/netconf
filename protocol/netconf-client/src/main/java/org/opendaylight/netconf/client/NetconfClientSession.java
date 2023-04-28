/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.Channel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.Collection;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSession;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfClientSession extends AbstractNetconfSession<NetconfClientSession, NetconfClientSessionListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientSession.class);
    private final Collection<String> capabilities;

    /**
     * Construct a new session.
     *
     * @param sessionListener    Netconf client session listener.
     * @param channel    Channel.
     * @param sessionId    Session Id.
     * @param capabilities    Set of advertised capabilities. Expected to be immutable.
     */
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "'this' passed to logger")
    public NetconfClientSession(final NetconfClientSessionListener sessionListener, final Channel channel,
                                final SessionIdType sessionId, final Collection<String> capabilities) {
        super(sessionListener, channel, sessionId);
        this.capabilities = capabilities;
        LOG.debug("Client Session {} created", this);
    }

    public Collection<String> getServerCapabilities() {
        return capabilities;
    }

    @Override
    protected NetconfClientSession thisInstance() {
        return this;
    }

    @Override
    protected void addExiHandlers(final ByteToMessageDecoder decoder,
                                  final MessageToByteEncoder<NetconfMessage> encoder) {
        // TODO used only in negotiator, client supports only auto start-exi
        replaceMessageDecoder(decoder);
        replaceMessageEncoder(encoder);
    }

    @Override
    public void stopExiCommunication() {
        // TODO never used, Netconf client does not support stop-exi
        replaceMessageDecoder(new NetconfXMLToMessageDecoder());
        replaceMessageEncoder(new NetconfMessageToXMLEncoder());
    }
}
