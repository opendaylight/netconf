/*
 * Copyright (c) 2018 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;

public interface RestconfDataStreamService {
    /**
     * Get target data resource.
     *
     * @param identifier
     *            path to target
     * @param uriInfo
     *            URI info
     * @return {@link EventOutput}
     */
    @GET
    @Path("/{identifier:.+}")
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    EventOutput getSSE(@Encoded @PathParam("identifier") String identifier, @Context UriInfo uriInfo);

}
