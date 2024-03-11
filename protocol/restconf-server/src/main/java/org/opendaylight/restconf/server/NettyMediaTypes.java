/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import java.util.Set;
import org.opendaylight.restconf.api.MediaTypes;

final class NettyMediaTypes {
    public static final AsciiString TEXT_XML = AsciiString.cached("text/xml");
    public static final AsciiString APPLICATION_XML = HttpHeaderValues.APPLICATION_XML;
    public static final AsciiString APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON;

    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_XRD_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-2">RFC6415, section 2</a>
     */
    public static final AsciiString APPLICATION_XRD_XML = AsciiString.cached(MediaTypes.APPLICATION_XRD_XML);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.1">RFC8040, section 11.3.1</a>
     */
    public static final AsciiString APPLICATION_YANG_DATA_XML =
        AsciiString.cached(MediaTypes.APPLICATION_YANG_DATA_XML);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.2">RFC8040, section 11.3.2</a>
     */
    public static final AsciiString APPLICATION_YANG_DATA_JSON =
        AsciiString.cached(MediaTypes.APPLICATION_YANG_DATA_JSON);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.1</a>
     */
    public static final AsciiString APPLICATION_YANG_PATCH_XML =
        AsciiString.cached(MediaTypes.APPLICATION_YANG_PATCH_XML);
    /**
     * A {@link AsciiString} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.2</a>
     */
    public static final AsciiString APPLICATION_YANG_PATCH_JSON =
        AsciiString.cached(MediaTypes.APPLICATION_YANG_PATCH_JSON);

    public static Set<AsciiString> RESTCONF_TYPES = Set.of(TEXT_XML, APPLICATION_XML, APPLICATION_JSON,
        APPLICATION_YANG_DATA_XML, APPLICATION_YANG_DATA_JSON);
    public static Set<AsciiString> JSON_TYPES = Set.of(APPLICATION_JSON, APPLICATION_YANG_DATA_JSON);
    public static Set<AsciiString> XML_TYPES = Set.of(TEXT_XML, APPLICATION_XML, APPLICATION_YANG_DATA_XML);
    public static Set<AsciiString> YANG_PATCH_TYPES = Set.of(APPLICATION_YANG_PATCH_XML, APPLICATION_YANG_PATCH_JSON);

    private NettyMediaTypes() {
        // hidden on purpose
    }
}
