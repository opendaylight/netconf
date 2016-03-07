/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.services.ServicesWrapper;

public class ServicesWrapperImpl implements ServicesWrapper {

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

}
