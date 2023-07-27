/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;

/**
 * Subscribing to streams.
 */
public interface RestconfStreamsSubscriptionService {
    /**
     * Subscribing to receive notification from stream support.
     *
     * @param identifier name of stream
     * @param uriInfo URI info
     * @return {@link NormalizedNodePayload}
     */
    // FIXME: this is a REST violation: GET does not transfer state! This should work in terms of
    //        https://www.rfc-editor.org/rfc/rfc8639#section-2.4, i.e. when we have that, aggressively deprecate
    //        and remove this special case. Besides it routes to a very bad thing in RestconfDataServiceImpl
    @GET
    @Path("data/" + RestconfStreamsConstants.STREAMS_PATH  + "/{identifier:.+}")
    NormalizedNodePayload subscribeToStream(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);
}
