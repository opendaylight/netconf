/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import io.netty.channel.ChannelFuture;
import java.io.Closeable;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

/**
 * Protocol Session represents the finite state machine in underlying protocol, including timers and its purpose is to
 * create a connection between server and client. Session is automatically started, when TCP connection is created, but
 * can be stopped manually. If the session is up, it has to redirect messages to/from user. Handles also malformed
 * messages and unknown requests.
 *
 * <p>
 * This interface should be implemented by a final class representing a protocol specific session.
 */
@NonNullByDefault
public interface NetconfSession extends Closeable {
    /**
     * Return the {@code session-id} of this session.
     *
     * @return the {@code session-id} of this session
     */
    SessionIdType sessionId();

    // FIXME: This is overly generalized leading to potential bad use:
    //        - HelloMessage cannot be sent as it is used only before NetconfSession is established
    //        - RpcMessage can only be sent via ClientSession
    //        - RpcReply can only be sent on a ServerSession
    //        - NotificationMessage can only be sent on a ServerSession which has seen a subscription request
    //        There are further state management issues: without interleave capability the client gives up the right
    //        to execute most operations
    ChannelFuture sendMessage(NetconfMessage message);

    // FIXME: this is ambiguous w.r.t.:
    //        - protocol interactions:
    //          - is it <kill-session>, https://www.rfc-editor.org/rfc/rfc6241#section-7.9, available to both parties?
    //          - is it <close-session>, https://www.rfc-editor.org/rfc/rfc6241#section-7.8, available to client?
    //        - state transitions:
    //          - what happens sendMessage()'s still in the output TCP buffer?
    //          - what happens to those that are on the wire?
    //        - synchronicity: is this a non-blocking operation?
    //        - if it is blocking: what is its non-blocking equivalent?
    @Override
    void close();
}
