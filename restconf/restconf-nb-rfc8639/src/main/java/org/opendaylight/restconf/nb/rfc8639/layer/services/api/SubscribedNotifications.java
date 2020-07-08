/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.api;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;

public interface SubscribedNotifications {

    /**
     * Start listening on stream with specific id and type.
     *
     * @param streamName
     *            - name of stream
     * @param subscriptionId
     *            - ID of subscription
     * @param httpServletRequest
     *            - request with information about opened session
     *
     * @return event output which enable write notifications to the client on session
     */
    @GET
    @Path("/notification/{streamName}/{subscriptionId}/")
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    EventOutput listen(@PathParam("streamName") String streamName,
            @PathParam("subscriptionId") String subscriptionId, @Context HttpServletRequest httpServletRequest);
}
