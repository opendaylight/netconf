/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;

/**
 * Implementation of {@link RpcServiceHandler}.
 *
 */
public class RpcServiceHandler implements Handler<DOMRpcService> {

    private final DOMRpcService rpcService;

    public RpcServiceHandler(final DOMRpcService rpcService) {
        this.rpcService = rpcService;
    }

    @Override
    public DOMRpcService get() {
        return this.rpcService;
    }
}
