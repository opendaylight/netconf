/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
public class Meta {

    private static final Logger LOG = LoggerFactory.getLogger(Meta.class);
    private static final String XRD_XML = "application/xrd+xml";
    private static final String RESPONSE = "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n" +
            "    <Link rel='restconf' href='/restconf'/>\n" +
            "</XRD>";

    @GET
    @Path("/.well-known/host-meta")
    @Produces(XRD_XML)
    public synchronized String getData() {
        LOG.info("response received :\n" + RESPONSE);
        return RESPONSE;
    }

}