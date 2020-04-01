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

    private static final String PATH_VERSION = "draft02";
    private static final String DEFAULT_BASE_PATH = "restconf";
    private final String basePath;

    protected BaseYangSwaggerGeneratorDraft02(final Optional<DOMSchemaService> schemaService) {
        super(schemaService);
        this.basePath = DEFAULT_BASE_PATH;
    }

    protected BaseYangSwaggerGeneratorDraft02(final Optional<DOMSchemaService> schemaService, final String basePath) {
        super(schemaService);
        this.basePath = basePath;
    }

    @Override
    protected String getPathVersion() {
        return PATH_VERSION;
    }

    @Override
    public String getResourcePath(final String resourceType, final String context) {
        return "/" + basePath + "/" + resourceType + context;
    }

    @Override
    public String getResourcePathPart(final String resourceType) {
        return resourceType;
    }

    @Override
    protected ListPathBuilder newListPathBuilder() {
        return key -> "/{" + key + "}";
    }

    @Override
    protected void appendPathKeyValue(final StringBuilder builder, final Object value) {
        builder.append(value).append('/');
    }
}
