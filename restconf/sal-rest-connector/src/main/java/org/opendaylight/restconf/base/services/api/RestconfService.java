/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;

/**
 * Service for getting yang library version.
 *
 */
public interface RestconfService extends UpdateHandlers {

    /**
     * Get yang library version.
     *
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("/yang-library-version")
    @Produces({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getLibraryVersion();
}
