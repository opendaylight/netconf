/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * Base class for a bierman draft02 implementation.
 *
 * @author Thomas Pantelis
 */
public abstract class BaseYangSwaggerGeneratorDraft02 extends BaseYangSwaggerGenerator {
    protected static final String RESTCONF_CONTEXT_ROOT = "restconf";

    protected BaseYangSwaggerGeneratorDraft02(Optional<DOMSchemaService> schemaService) {
        super(schemaService);
    }

    @Override
    public String getDataStorePath(final String dataStore, final String context) {
        return "/" + RESTCONF_CONTEXT_ROOT + "/" + dataStore + context;
    }

    @Override
    public String getContent(final String dataStore) {
        return "";
    }

    @Override
    protected ListPathBuilder newListPathBuilder() {
        return key -> "/{" + key + "}";
    }

    @Override
    protected void appendPathKeyValue(StringBuilder builder, Object value) {
        builder.append(value).append('/');
    }
}
