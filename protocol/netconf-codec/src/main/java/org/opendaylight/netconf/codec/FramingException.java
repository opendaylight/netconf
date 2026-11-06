/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.handler.codec.DecoderException;
import java.io.Serial;

/**
 * A {@link DecoderException} reporting that the input does not match the RFC 6242 chunked framing.
 *
 * <p>RFC 6242 section 4.2 requires the session to be closed after such an error. The decoder does not close the
 * channel itself, so that a handler can send an error reply first. Whoever handles this exception must close the
 * channel.
 */
public final class FramingException extends DecoderException {
    @Serial
    private static final long serialVersionUID = 1L;

    FramingException(final String message) {
        super(message);
    }
}
