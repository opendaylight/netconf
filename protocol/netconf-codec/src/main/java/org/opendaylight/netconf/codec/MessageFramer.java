/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.FramingMechanism;

/**
 * A simple class which deals with framing a message according to {@link FramingMechanism}.
 */
@NonNullByDefault
public enum MessageFramer {
    EOM(FramingMechanism.EOM) {
        @Override
        public OutputStream newOuputStream(final ChannelHandlerContext ctx, final ChannelPromise promise) {
            // TODO Auto-generated method stub
            return null;
        }
    },
    CHUNK(FramingMechanism.CHUNK) {
        @Override
        public OutputStream newOuputStream(final ChannelHandlerContext ctx, final ChannelPromise promise) {
            // TODO Auto-generated method stub
            return null;
        }
    };

    private final FramingMechanism mechanism;

    MessageFramer(final FramingMechanism mechanism) {
        this.mechanism = requireNonNull(mechanism);
    }

    public static MessageFramer ofMechanism(final FramingMechanism mechanism) {
        return switch (mechanism) {
            case CHUNK -> CHUNK;
            case EOM -> EOM;
        };
    }

    public final FramingMechanism mechanism() {
        return mechanism;
    }

    public abstract OutputStream newOuputStream(ChannelHandlerContext ctx, ChannelPromise promise);
}
