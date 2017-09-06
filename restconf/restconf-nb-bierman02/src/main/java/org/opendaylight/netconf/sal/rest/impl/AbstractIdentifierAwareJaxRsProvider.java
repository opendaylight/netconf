/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.api.RestconfConstants;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;

/**
 * JAX-RS Provider.
 *
 * @deprecated This class will be replaced by
 *             {@link org.opendaylight.restconf.jersey.providers.AbstractIdentifierAwareJaxRsProvider}
 */
@Deprecated
public class AbstractIdentifierAwareJaxRsProvider {

    private static final String POST = "POST";

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    protected final String getIdentifier() {
        return this.uriInfo.getPathParameters(false).getFirst(RestconfConstants.IDENTIFIER);
    }

    protected InstanceIdentifierContext<?> getInstanceIdentifierContext() {
        return ControllerContext.getInstance().toInstanceIdentifier(getIdentifier());
    }

    protected UriInfo getUriInfo() {
        return this.uriInfo;
    }

    protected boolean isPost() {
        return POST.equals(this.request.getMethod());
    }

    Request getRequest() {
        return this.request;
    }
}
