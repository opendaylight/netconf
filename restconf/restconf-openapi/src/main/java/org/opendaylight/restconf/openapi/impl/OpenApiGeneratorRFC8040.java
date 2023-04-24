/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * This class gathers all YANG-defined {@link org.opendaylight.yangtools.yang.model.api.Module}s and
 * generates Swagger compliant documentation for the RFC 8040 version.
 *
 * @author Thomas Pantelis
 */
public class OpenApiGeneratorRFC8040 extends BaseYangOpenApiGeneratorRFC8040 {
    public OpenApiGeneratorRFC8040(final @NonNull DOMSchemaService schemaService) {
        super(schemaService);
    }

    public OpenApiGeneratorRFC8040(final @NonNull DOMSchemaService schemaService, final @NonNull String basePath) {
        super(schemaService, basePath);
    }
}
