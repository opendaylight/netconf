/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.api;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

public interface SubscribedNotifications {

    /**
     * Return all available YANG notification streams.
     *
     * @param uriInfo
     *            - URI information of request
     * @return - list of all available YANG notifications streams
     */
    @GET
    @Path("/data/ietf-restconf-monitoring:restconf-state/streams")
    @Produces({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    Response getStreams(@Context UriInfo uriInfo);

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

    /**
     * Invoke an RPC operation.
     *
     * @param identifier
     *            module name and rpc identifier string for the desired operation
     * @param payload
     *            {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo
     *            URI info
     * @param httpServletRequest
     *            request with information about opened session
     * @return {@link NormalizedNodeContext}
     */
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier, NormalizedNodeContext payload,
            @Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest);
}
