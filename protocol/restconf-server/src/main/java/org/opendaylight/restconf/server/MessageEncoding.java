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
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;

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
    JSON(NettyMediaTypes.APPLICATION_YANG_DATA_JSON, NettyMediaTypes.APPLICATION_YANG_PATCH_JSON,
        EncodingName.RFC8040_JSON, NettyMediaTypes.APPLICATION_JSON),
    /**
     * JSON encoding, as specified in <a href="https://www.rfc-editor.org/rfc/rfc7950">RFC7950</a> and extended in
     * <a href="https://www.rfc-editor.org/rfc/rfc7952#section-5.1">RFC7952, section 5.1</a>.
     */
    XML(NettyMediaTypes.APPLICATION_YANG_DATA_XML, NettyMediaTypes.APPLICATION_YANG_PATCH_XML,
        EncodingName.RFC8040_XML, NettyMediaTypes.APPLICATION_XML , NettyMediaTypes.TEXT_XML);

    private final AsciiString dataMediaType;
    private final AsciiString patchMediaType;
    private final EncodingName streamEncodingName;
    private final Set<AsciiString> compatibleDataMediaTypes;

    MessageEncoding(final AsciiString dataMediaType, final AsciiString patchMediaType,
            final EncodingName streamEncodingName, final AsciiString... compatibleMediaTypes) {
        this.dataMediaType = requireNonNull(dataMediaType);
        this.patchMediaType = requireNonNull(patchMediaType);
        this.streamEncodingName = requireNonNull(streamEncodingName);
        compatibleDataMediaTypes = Set.of(compatibleMediaTypes);
    }

    /**
     * Return the media type this message encoding assigns to YANG Data.
     *
     * @return A media type
     */
    AsciiString dataMediaType() {
        return dataMediaType;
    }

    /**
     * Return the media type this message encoding assigns to YANG Patch.
     *
     * @return A media type
     */
    AsciiString patchMediaType() {
        return patchMediaType;
    }

    /**
     * Return the stream encoding name {@code ietf-restconf.yang} assigns to this encoding.
     *
     * @return A stream {@link EncodingName}
     */
    EncodingName streamEncodingName() {
        return streamEncodingName;
    }

    /**
     * Returns {@code true} if YANG Data encoded with this encoding is known to <b>produce</b> content compatible with
     * specified media type.
     *
     * <p>
     * WARNING: do not use this method to determine if we can <b>consume</b> content with a particular media type.
     *
     * @param mediaType requested media type
     * @return {@code true} if this encoding can be used to produce specified media type
     */
    boolean producesDataCompatibleWith(final AsciiString mediaType) {
        return dataMediaType.equals(mediaType) || compatibleDataMediaTypes.contains(mediaType);
    }
}