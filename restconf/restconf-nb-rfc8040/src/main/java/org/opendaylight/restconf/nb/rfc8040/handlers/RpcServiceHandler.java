/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMRpcService;

/**
 * Implementation of {@link RpcServiceHandler}.
 */
// FIXME: remove this class
@Singleton
public class RpcServiceHandler {
    private final DOMRpcService rpcService;

    @Inject
    public RpcServiceHandler(final @Reference DOMRpcService rpcService) {
        this.rpcService = rpcService;
    }

    public DOMRpcService get() {
        return this.rpcService;
    }
}
