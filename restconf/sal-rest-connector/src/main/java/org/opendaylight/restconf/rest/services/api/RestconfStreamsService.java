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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Draft17;
import org.opendaylight.restconf.utils.RestconfConstants;

/**
 * Container that provides access to the event notification streams supported by
 * the server.
 *
 */
public interface RestconfStreamsService {

    /**
     * List of streams supported by the server.
     *
     * @param uriInfo
     *            - URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("data/ietf-restconf-monitoring:restconf-state/streams")
    @Produces({ Draft17.MediaTypes.DATA + RestconfConstants.JSON, Draft17.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getAvailableStreams(@Context UriInfo uriInfo);
}