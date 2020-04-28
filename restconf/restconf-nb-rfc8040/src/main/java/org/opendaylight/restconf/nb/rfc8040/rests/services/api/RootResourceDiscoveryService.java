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
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

/**
 * Controller for determining the {@code Root Resource} of the RESTCONF API. This interface serves up a
 * {@code host-meta} document as defined in
 * <a href="https://tools.ietf.org/html/rfc8040#section-3">RFC6415 section 3<a>.
 */
// FIXME: this really should be the endpoint's job to aggregate these. Once JAX-RS (or any other wiring) can provide it,
//        integrate with that framework, so we co-exist with others.
public interface RootResourceDiscoveryService {
    /**
     * Root Resource Discovery. See: https://tools.ietf.org/html/rfc8040#section-3.1
     */
    @GET
    @Path("/host-meta")
    @Produces({Rfc8040.MediaTypes.XRD + RestconfConstants.XML})
    Response readXrdData();

    /**
     * Root Resource Discovery as a <a href="https://tools.ietf.org/html/rfc6415#appendix-A">JRD</a>.
     */
    @GET
    @Path("/host-meta.json")
    @Produces({Rfc8040.MediaTypes.XRD + RestconfConstants.JSON})
    Response readJsonData();
}
