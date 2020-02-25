/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * Base class for an RFC 8040 implementation.
 *
 * @author Thomas Pantelis
 */
public abstract class BaseYangSwaggerGeneratorRFC8040 extends BaseYangSwaggerGenerator {

    private static final String DEFAULT_BASE_PATH = "rests";
    private final String basePath;

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService) {
        super(schemaService);
        this.basePath = DEFAULT_BASE_PATH;
    }

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService, final String basePath) {
        super(schemaService);
        this.basePath = basePath;
    }

    @Override
    public String getDataStorePath(final String dataStore, final String context) {
        if ("config".contains(dataStore) || "operational".contains(dataStore)) {
            return "/" + basePath + "/data" + context;
        }
        return "/" + basePath + "/operations" + context;
    }

    @Override
    protected ListPathBuilder newListPathBuilder() {
        return new ListPathBuilder() {
            private String prefix = "=";

            @Override
            public String nextParamIdentifier(String key) {
                String str = prefix + "{" + key + "}";
                prefix = ",";
                return str;
            }
        };
    }

    @Override
    protected void appendPathKeyValue(StringBuilder builder, Object value) {
        builder.deleteCharAt(builder.length() - 1).append("=").append(value).append('/');
    }
}
