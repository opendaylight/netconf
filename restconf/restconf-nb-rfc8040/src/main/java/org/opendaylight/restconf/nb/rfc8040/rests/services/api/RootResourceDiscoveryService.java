/*
 * Copyright (c) 2020 ZTE Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;

/**
 * Controller for determining the {@code Root Resource} of the RESTCONF API. This interface serves up a
 * {@code host-meta} document as defined in
 * <a href="https://tools.ietf.org/html/rfc8040#section-3">RFC6415 section 3</a>.
 */
// FIXME: this really should be the endpoint's job to aggregate these. Once JAX-RS (or any other wiring) can provide it,
//        integrate with that framework, so we co-exist with others.
public interface RootResourceDiscoveryService {
    /**
     * Root Resource Discovery as an XRD.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8040#section-3.1">RFC8040, section 3.1</a>
     */
    @GET
    @Path("/host-meta")
    @Produces(MediaTypes.APPLICATION_XRD_XML)
    Response readXrdData();

    /**
     * Root Resource Discovery as a JRD.
     *
     *  @see <a href="https://tools.ietf.org/html/rfc6415#appendix-A">RFC6415, appendix A</a>
     */
    @GET
    @Path("/host-meta.json")
    @Produces(MediaType.APPLICATION_JSON)
    Response readJsonData();
}
