/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yangtools.yang.common.YangConstants;

@NonNullByDefault
final class NettyMediaTypes {
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.1">RFC8040, section 11.3.1</a>
     */
    static final AsciiString APPLICATION_YANG_DATA_XML = AsciiString.cached(MediaTypes.APPLICATION_YANG_DATA_XML);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.2">RFC8040, section 11.3.2</a>
     */
    static final AsciiString APPLICATION_YANG_DATA_JSON = AsciiString.cached(MediaTypes.APPLICATION_YANG_DATA_JSON);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.1</a>
     */
    static final AsciiString APPLICATION_YANG_PATCH_XML = AsciiString.cached(MediaTypes.APPLICATION_YANG_PATCH_XML);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.2</a>
     */
    static final AsciiString APPLICATION_YANG_PATCH_JSON = AsciiString.cached(MediaTypes.APPLICATION_YANG_PATCH_JSON);
    /**
     * A {@link AsciiString} constant representing {@value YangConstants#RFC6020_YANG_MEDIA_TYPE} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6020#section-14.1">RFC6020, section 14.1</a>
     */
    static final AsciiString APPLICATION_YANG = AsciiString.cached(YangConstants.RFC6020_YANG_MEDIA_TYPE);
    /**
     * A {@link AsciiString} constant representing {@value YangConstants#RFC6020_YIN_MEDIA_TYPE} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6020#section-14.2">RFC6020, section 14.2</a>
     */
    static final AsciiString APPLICATION_YIN_XML = AsciiString.cached(YangConstants.RFC6020_YIN_MEDIA_TYPE);

    static final AsciiString TEXT_XML = AsciiString.cached("text/xml");

    private NettyMediaTypes() {
        // hidden on purpose
    }
}
