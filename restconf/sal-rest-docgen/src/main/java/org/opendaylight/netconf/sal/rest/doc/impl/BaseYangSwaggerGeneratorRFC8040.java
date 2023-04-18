/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * Base class for an RFC 8040 implementation.
 *
 * @author Thomas Pantelis
 */
public abstract class BaseYangSwaggerGeneratorRFC8040 extends BaseYangSwaggerGenerator {
    private final String basePath;

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService) {
        this(schemaService, "rests");
    }

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService, final String basePath) {
        super(schemaService);
        this.basePath = requireNonNull(basePath);
    }

    @Override
    public String getResourcePath(final String resourceType, final String context) {
        if ("data".equals(resourceType)) {
            return "/" + basePath + "/data" + context;
        }
        return "/" + basePath + "/operations" + context;
    }

    @Override
    protected ListPathBuilder newListPathBuilder() {
        return new ListPathBuilder() {
            private String prefix = "=";

            @Override
            public String nextParamIdentifier(final String key) {
                final String str = prefix + "{" + key + "}";
                prefix = ",";
                return str;
            }
        };
    }

    @Override
    protected void appendPathKeyValue(final StringBuilder builder, final Object value) {
        builder.deleteCharAt(builder.length() - 1).append("=").append(value).append('/');
    }
}
