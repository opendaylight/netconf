/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public interface MessageTransformer
        extends ActionTransformer, NotificationTransformer, RpcTransformer<NormalizedNode, DOMRpcResult> {
    @Override
    default NetconfMessage toActionRequest(final Absolute action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode payload) {
        throw new UnsupportedOperationException();
    }

    @Override
    default DOMActionResult toActionResult(final Absolute action, final NetconfMessage message) {
        throw new UnsupportedOperationException();
    }
}
