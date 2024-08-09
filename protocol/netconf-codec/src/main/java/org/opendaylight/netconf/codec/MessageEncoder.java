/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.handler.codec.MessageToByteEncoder;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * An encoder of {@link NetconfMessage} to a series of bytes.
 */
public abstract class MessageEncoder extends MessageToByteEncoder<NetconfMessage> {
    /**
     * The name of the handler providing message encoding.
     */
    public static final @NonNull String HANDLER_NAME = "netconfMessageEncoder";

    protected MessageEncoder() {
        super(NetconfMessage.class);
    }
}
