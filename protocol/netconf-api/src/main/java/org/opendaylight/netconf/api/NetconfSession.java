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
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * Protocol Session represents the finite state machine in underlying protocol, including timers and its purpose is to
 * create a connection between server and client. Session is automatically started, when TCP connection is created, but
 * can be stopped manually. If the session is up, it has to redirect messages to/from user. Handles also malformed
 * messages and unknown requests.
 *
 * <p>
 * This interface should be implemented by a final class representing a protocol specific session.
 */
public interface NetconfSession extends Closeable {

    ChannelFuture sendMessage(NetconfMessage message);

    @Override
    void close();
}
