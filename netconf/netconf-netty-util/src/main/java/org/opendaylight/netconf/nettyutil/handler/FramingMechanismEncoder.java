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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An channel handler framing outbound messages into specified framing.
 */
public abstract sealed class FramingMechanismEncoder extends MessageToByteEncoder<ByteBuf>
        permits ChunkedFramingMechanismEncoder, EOMFramingMechanismEncoder {
    private static final Logger LOG = LoggerFactory.getLogger(FramingMechanismEncoder.class);

    FramingMechanismEncoder() {
        // Hidden on purpose
    }

    /**
     * Return a {@link FramingMechanismEncoder} for specified {@link FramingMechanism}.
     *
     * @param framingMechanism Desired {@link FramingMechanism}
     * @return A {@link FramingMechanismEncoder}
     */
    public static final @NonNull FramingMechanismEncoder of(final FramingMechanism framingMechanism) {
        LOG.debug("{} framing mechanism was selected.", framingMechanism);
        return switch (framingMechanism) {
            case CHUNK -> new ChunkedFramingMechanismEncoder();
            case EOM -> new EOMFramingMechanismEncoder();
        };
    }
}
