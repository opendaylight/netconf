/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yangtools.concepts.Builder;

public class RpcResultFactory extends FutureDataFactory<DOMRpcResult> implements Builder<DOMRpcResult> {
    @Override
    public DOMRpcResult build() {
        return this.result;
    }
}
