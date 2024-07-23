/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.MediaTypes;

/**
 * RESTCONF {@link MediaTypes}.expressed as JAX-RS {@link MediaType}.
 */
@NonNullByDefault
public final class JaxRsMediaTypes {
    /**
     * A {@code MediaType} constant representing {@value MediaTypes#APPLICATION_XRD_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-2">RFC6415, section 2</a>
     */
    public static final MediaType APPLICATION_XRD_XML = MediaType.valueOf(MediaTypes.APPLICATION_XRD_XML);
    /**
     * A {@code MediaType} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.1">RFC8040, section 11.3.1</a>
     */
    public static final MediaType APPLICATION_YANG_DATA_XML = MediaType.valueOf(MediaTypes.APPLICATION_YANG_DATA_XML);
    /**
     * A {@code MediaType} constant representing {@value MediaTypes#APPLICATION_YANG_DATA_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.3.2">RFC8040, section 11.3.2</a>
     */
    public static final MediaType APPLICATION_YANG_DATA_JSON = MediaType.valueOf(MediaTypes.APPLICATION_YANG_DATA_JSON);
    /**
     * A {@code MediaType} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_XML} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.1</a>
     */
    public static final MediaType APPLICATION_YANG_PATCH_XML = MediaType.valueOf(MediaTypes.APPLICATION_YANG_PATCH_XML);
    /**
     * A {@code MediaType} constant representing {@value MediaTypes#APPLICATION_YANG_PATCH_JSON} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8072#section-4.2.1">RFC8072, section 4.2.2</a>
     */
    public static final MediaType APPLICATION_YANG_PATCH_JSON =
        MediaType.valueOf(MediaTypes.APPLICATION_YANG_PATCH_JSON);

    private JaxRsMediaTypes() {
        // Hidden on purpose
    }
}
