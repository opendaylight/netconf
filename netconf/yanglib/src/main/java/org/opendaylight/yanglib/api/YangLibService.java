/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * Service provides YANG schema sources for modules from yang library.
 */
@Path("/")
public interface YangLibService {

    /**
     * Get module's source for each module from yang library.
     * @param name Module's name
     * @param revision Module's revision
     * @return Module's source
     */
    @GET
    @Produces("text/plain")
    @Path("/schemas/{modelName}/{revision:([0-9\\-]*)}")
    String getSchema(@PathParam("modelName") String name, @PathParam("revision") String revision);
}
