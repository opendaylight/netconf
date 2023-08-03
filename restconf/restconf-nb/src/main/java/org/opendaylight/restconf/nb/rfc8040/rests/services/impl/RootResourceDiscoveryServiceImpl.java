/*
 * Copyright (c) 2020 ZTE Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;

/**
 * Controller for determining the {@code Root Resource} of the RESTCONF API. This interface serves up a
 * {@code host-meta} document as defined in
 * <a href="https://tools.ietf.org/html/rfc8040#section-3">RFC6415 section 3</a>.
 */
// FIXME: this really should be the endpoint's job to aggregate these. Once JAX-RS (or any other wiring) can provide it,
//        integrate with that framework, so we co-exist with others.
@Path("/")
public final class RootResourceDiscoveryServiceImpl {
    private final String restconfRoot;

    public RootResourceDiscoveryServiceImpl(final String restconfRoot) {
        this.restconfRoot = requireNonNull(restconfRoot);
    }

    /**
     * Root Resource Discovery as an XRD.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8040#section-3.1">RFC8040, section 3.1</a>
     */
    @GET
    @Path("/host-meta")
    @Produces(MediaTypes.APPLICATION_XRD_XML)
    public Response readXrdData() {
        return Response.status(Status.OK)
            .entity("<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
                + "  <Link rel='restconf' href='/" + restconfRoot + "'/>\n"
                + "</XRD>")
            .build();
    }

    /**
     * Root Resource Discovery as a JRD.
     *
     *  @see <a href="https://tools.ietf.org/html/rfc6415#appendix-A">RFC6415, appendix A</a>
     */
    @GET
    @Path("/host-meta.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response readJsonData() {
        return Response.status(Status.OK)
            .entity("{\n"
                + "  \"links\" : {\n"
                + "    \"rel\" : \"restconf\",\n"
                + "    \"href\" : \"/" + restconfRoot + "\"\n"
                + "  }\n"
                + "}")
            .build();
    }
}

