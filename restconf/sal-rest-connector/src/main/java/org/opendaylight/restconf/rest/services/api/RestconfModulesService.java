/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Draft17;
import org.opendaylight.restconf.utils.RestconfConstants;

/**
 * Service provides information about the YANG modules and submodules.
 */
public interface RestconfModulesService {

    /**
     * Get identifiers for the YANG data model modules supported by the server.
     *
     * @param uriInfo
     *            - URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("data/ietf-yang-library:modules-state")
    @Produces({ Draft17.MediaTypes.DATA + RestconfConstants.JSON, Draft17.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModules(@Context UriInfo uriInfo);

    /**
     * Valid only for mount points. Get identifiers for the YANG data model
     * modules supported by the specific mount point.
     *
     * @param identifier
     *            - path parameter
     * @param uriInfo
     *            - URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("data/ietf-yang-library:modules-state/{identifier:.+}")
    @Produces({ Draft17.MediaTypes.DATA + RestconfConstants.JSON, Draft17.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModules(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * Get entry for each YANG data model module supported by the server. There
     * must be an instance of this list for every YANG module that is used by
     * the server.
     *
     * @param identifier
     *            - path parameter
     * @param uriInfo
     *            - URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("data/ietf-yang-library:modules-state/module/{identifier:.+}")
    @Produces({ Draft17.MediaTypes.DATA + RestconfConstants.JSON, Draft17.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModule(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);
}