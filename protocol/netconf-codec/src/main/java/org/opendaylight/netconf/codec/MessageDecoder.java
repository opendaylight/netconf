/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.handler.codec.ByteToMessageDecoder;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * A decoder from a series of bytes to a {@link NetconfMessage}.
 */
public abstract class MessageDecoder extends ByteToMessageDecoder {
    /**
     * The name of the handler providing message decoding.
     */
    public static final @NonNull String HANDLER_NAME = "netconfMessageDecoder";
}
