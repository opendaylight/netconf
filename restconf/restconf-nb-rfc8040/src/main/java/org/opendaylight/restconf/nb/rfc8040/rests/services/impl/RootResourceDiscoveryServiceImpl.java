/*
 * Copyright (c) 2020 ZTE Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RootResourceDiscoveryService;

@Path("/")
public final class RootResourceDiscoveryServiceImpl implements RootResourceDiscoveryService {
    private final String restconfRoot;

    public RootResourceDiscoveryServiceImpl(final String restconfRoot) {
        this.restconfRoot = requireNonNull(restconfRoot);
    }

    @Override
    public Response readXrdData() {
        return Response.status(Status.OK)
            .entity("<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
                + "     <Link rel='restconf' href='/" + restconfRoot + "'/>\n"
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
                + "     \"href\" : \"/" + restconfRoot + "\"\n"
                + " }\n"
                + "}")
            .build();
    }
}

