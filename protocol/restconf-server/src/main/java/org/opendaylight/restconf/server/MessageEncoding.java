/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A RESTCONF message encoding, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-5.2">RFC8040, section 5.2</a>.
 */
@NonNullByDefault
public enum MessageEncoding {
    /**
     * JSON encoding, as specified in <a href="https://www.rfc-editor.org/rfc/rfc7951">RFC7951</a> and extended in
     * <a href="https://www.rfc-editor.org/rfc/rfc7952#section-5.2">RFC7952, section 5.2</a>.
     */
    JSON(NettyMediaTypes.APPLICATION_YANG_DATA_JSON),
    /**
     * JSON encoding, as specified in <a href="https://www.rfc-editor.org/rfc/rfc7950">RFC7950</a> and extended in
     * <a href="https://www.rfc-editor.org/rfc/rfc7952#section-5.1">RFC7952, section 5.1</a>.
     */
    XML(NettyMediaTypes.APPLICATION_YANG_DATA_XML);

    private final AsciiString mediaType;

    MessageEncoding(final AsciiString mediaType) {
        this.mediaType = requireNonNull(mediaType);
    }

    AsciiString mediaType() {
        return mediaType;
    }
}