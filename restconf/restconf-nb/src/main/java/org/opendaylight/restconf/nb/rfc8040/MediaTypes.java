/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Media types we use in this implementation, in both {@link String} and {@link MediaType} form.
 */
@NonNullByDefault
public final class MediaTypes {
    /**
     * A {@code String} constant representing {@value #APPLICATION_XRD_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-2">RFC6415, section 2</a>
     */
    public static final String APPLICATION_XRD_XML = "application/xrd+xml";
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_XRD_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-2">RFC6415, section 2</a>
     */
    public static final MediaType APPLICATION_XRD_XML_TYPE = MediaType.valueOf(APPLICATION_XRD_XML);
    /**
     * A {@code String} constant representing {@value #APPLICATION_YANG_DATA_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.1">RFC8040, section 11.3.1</a>
     */
    public static final String APPLICATION_YANG_DATA_XML = "application/yang-data+xml";
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_YANG_DATA_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.1">RFC8040, section 11.3.1</a>
     */
    public static final MediaType APPLICATION_YANG_DATA_XML_TYPE = MediaType.valueOf(APPLICATION_YANG_DATA_XML);
    /**
     * A {@code String} constant representing {@value #APPLICATION_YANG_DATA_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.2">RFC8040, section 11.3.2</a>
     */
    public static final String APPLICATION_YANG_DATA_JSON = "application/yang-data+json";
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_YANG_DATA_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.2">RFC8040, section 11.3.2</a>
     */
    public static final MediaType APPLICATION_YANG_DATA_JSON_TYPE = MediaType.valueOf(APPLICATION_YANG_DATA_JSON);
    /**
     * A {@code String} constant representing {@value #APPLICATION_YANG_PATCH_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.1</a>
     */
    public static final String APPLICATION_YANG_PATCH_XML = "application/yang-patch+xml";
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_YANG_PATCH_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.1</a>
     */
    public static final MediaType APPLICATION_YANG_PATCH_XML_TYPE = MediaType.valueOf(APPLICATION_YANG_PATCH_XML);
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_YANG_PATCH_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.2</a>
     */
    public static final String APPLICATION_YANG_PATCH_JSON = "application/yang-patch+json";
    /**
     * A {@code MediaType} constant representing {@value #APPLICATION_YANG_PATCH_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.2</a>
     */
    public static final MediaType APPLICATION_YANG_PATCH_JSON_TYPE = MediaType.valueOf(APPLICATION_YANG_PATCH_JSON);

    private MediaTypes() {
        // Hidden on purpose
    }
}
