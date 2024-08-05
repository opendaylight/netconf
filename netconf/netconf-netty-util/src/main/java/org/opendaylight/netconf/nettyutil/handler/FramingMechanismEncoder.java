/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * An channel handler framing outbound messages into specified framing.
 */
public abstract sealed class FramingMechanismEncoder extends MessageToByteEncoder<ByteBuf>
        permits ChunkedFramingMechanismEncoder, EOMFramingMechanismEncoder {
    // Nothing else
}
