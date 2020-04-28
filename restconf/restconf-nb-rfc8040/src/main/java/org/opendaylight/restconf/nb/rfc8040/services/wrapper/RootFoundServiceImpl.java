/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RootFoundService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

@Path("/")
public class RootFoundServiceImpl implements RootFoundService {

    private static class InstanceHolder {
        public static final RootFoundServiceImpl INSTANCE = new RootFoundServiceImpl();
    }

    public static RootFoundServiceImpl getInstance() {
        return RootFoundServiceImpl.InstanceHolder.INSTANCE;
    }

    @Override
    public Response readXrdData() {
        return Response.status(200)
                .entity("<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
                        + "     <Link rel='restconf' href='/" + RestconfConstants.BASE_URI_PATTERN + "'/>\n"
                        + "</XRD>").build();
    }
}
