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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.YangConstants;

/**
 * Retrieval of the YANG modules which server supports.
 */
@Path("/")
public class RestconfSchemaServiceImpl {
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

    /**
     * Get schema of specific module.
     *
     * @param identifier path parameter
     * @return {@link SchemaExportContext}
     */
    @GET
    @Produces({ YangConstants.RFC6020_YIN_MEDIA_TYPE, YangConstants.RFC6020_YANG_MEDIA_TYPE })
    @Path("modules/{identifier:.+}")
    public SchemaExportContext getSchema(@PathParam("identifier") final String identifier) {
        return ParserIdentifier.toSchemaExportContextFromIdentifier(schemaService.getGlobalContext(), identifier,
            mountPointService, sourceProvider);
    }
}
