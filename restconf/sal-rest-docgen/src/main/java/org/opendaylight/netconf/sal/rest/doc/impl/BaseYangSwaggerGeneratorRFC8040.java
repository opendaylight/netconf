/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Optional;
import org.opendaylight.controller.sal.core.api.model.SchemaService;

/**
 * Base class for an RFC 8040 implementation.
 *
 * @author Thomas Pantelis
 */
public abstract class BaseYangSwaggerGeneratorRFC8040 extends BaseYangSwaggerGenerator {

    private static final String BASE_PATH = "rests";

    protected BaseYangSwaggerGeneratorRFC8040(Optional<SchemaService> schemaService) {
        super(schemaService);
    }

    @Override
    public String getDataStorePath(final String dataStore, final String context) {
        if ("config".contains(dataStore) || "operational".contains(dataStore)) {
            return "/" + BASE_PATH + "/data" + context;
        }
        return "/" + BASE_PATH + "/operations" + context;
    }

    @Override
    public String getContent(final String dataStore) {
        if ("operational".contains(dataStore)) {
            return "?content=nonconfig";
        } else if ("config".contains(dataStore)) {
            return "?content=config";
        } else {
            return "";
        }
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
