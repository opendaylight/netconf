/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.handlers.impl;

import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.handlers.api.RpcServiceHandler;

/**
 * Implementation of {@link RpcServiceHandler}
 *
 */
public class RpcServiceHandlerImpl implements RpcServiceHandler {

    private DOMRpcService rpcService;

    @Override
    public DOMRpcService getRpcService() {
        return this.rpcService;
    }

    @Override
    public void setRpcService(final DOMRpcService rpcService) {
        this.rpcService = rpcService;
    }

}
