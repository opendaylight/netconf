/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;

public class RestconfInvokeOperationsServiceImpl implements RestconfInvokeOperationsService {

    @Override
    public NormalizedNodeContext invokeRpc(String identifier, NormalizedNodeContext payload, UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }
}
