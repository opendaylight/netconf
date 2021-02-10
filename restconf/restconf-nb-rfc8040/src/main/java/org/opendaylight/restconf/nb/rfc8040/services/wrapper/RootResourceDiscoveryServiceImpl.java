/*
 * Copyright (c) 2020 ZTE Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RootResourceDiscoveryService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

@Path("/")
public final class RootResourceDiscoveryServiceImpl implements RootResourceDiscoveryService {

    @Override
    public Response readXrdData() {
        return Response.status(Status.OK)
                .entity("<?xml version='1.0' encoding='UTF-8'?>\n"
                        + "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
                        + "     <Link rel='restconf' href='/" + RestconfConstants.BASE_URI_PATTERN + "'/>\n"
                        + "</XRD>")
                .build();
    }

    @Override
    public Response readJsonData() {
        return Response.status(Status.OK)
                .entity("{\n"
                        + " \"links\" :\n"
                        + " {\n"
                        + "     \"rel\" : \"restconf\",\n"
                        + "     \"href\" : \"/" + RestconfConstants.BASE_URI_PATTERN + "/\"\n"
                        + " }\n"
                        + "}")
                .build();
    }
}

