/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfSchemaService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;

/**
 * Implementation of {@link RestconfSchemaService}.
 */
@Path("/")
public class RestconfSchemaServiceImpl implements RestconfSchemaService {
    private final DOMSchemaService schemaService;
    private final DOMMountPointService mountPointService;
    private final DOMYangTextSourceProvider sourceProvider;

    /**
     * Default constructor.
     *
     * @param schemaService a {@link DOMSchemaService}
     * @param mountPointService a {@link DOMMountPointService}
     */
    public RestconfSchemaServiceImpl(final DOMSchemaService schemaService,
            final DOMMountPointService mountPointService) {
        this.schemaService = requireNonNull(schemaService);
        this.mountPointService = requireNonNull(mountPointService);
        sourceProvider = schemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);
        checkArgument(sourceProvider != null, "No DOMYangTextSourceProvider available in %s", schemaService);
    }

    @Override
    public SchemaExportContext getSchema(final String identifier) {
        return ParserIdentifier.toSchemaExportContextFromIdentifier(schemaService.getGlobalContext(), identifier,
            mountPointService, sourceProvider);
    }
}
