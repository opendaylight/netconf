/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import io.netty.handler.codec.ByteToMessageDecoder;
import org.eclipse.jdt.annotation.NonNull;

/**
 * An channel handler assembling inbound messages from specified framing.
 */
public abstract sealed class FramingMechanismDecoder extends ByteToMessageDecoder
        permits ChunkedFramingMechanismDecoder, EOMFramingMechanismDecoder {
    /**
     * The name of the handler providing frame decoding.
     */
    public static final @NonNull String HANDLER_NAME = "frameDecoder";
}
