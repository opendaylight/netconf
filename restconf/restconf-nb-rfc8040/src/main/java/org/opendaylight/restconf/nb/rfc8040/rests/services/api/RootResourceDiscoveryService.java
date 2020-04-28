/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
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
 * Controller for determining the root of the RESTCONF API
 * See: https://tools.ietf.org/html/rfc8040#section-3.1
 */
public interface RootResourceDiscoveryService {
    @GET
    @Path("/host-meta")
    @Produces({Rfc8040.MediaTypes.XRD + RestconfConstants.XML})
    Response readXrdData();

    @GET
    @Path("/host-meta.json")
    @Produces({Rfc8040.MediaTypes.XRD + RestconfConstants.JSON})
    Response readJsonData();
}
