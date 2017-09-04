/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.api;

import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;

/**
 * Subscribing to streams.
 *
 */
public interface RestconfStreamsSubscriptionService {

    /**
     * Subscribing to receive notification from stream support.
     *
     * @param identifier
     *             name of stream
     * @param uriInfo
     *             URI info
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("data/ietf-restconf-monitoring:restconf-state/streams/stream/{identifier:.+}")
    NormalizedNodeContext subscribeToStream(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);
}
