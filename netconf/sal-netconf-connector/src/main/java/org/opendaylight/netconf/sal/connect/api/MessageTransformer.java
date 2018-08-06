/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public interface MessageTransformer<M> {

    DOMNotification toNotification(M message);

    M toRpcRequest(SchemaPath rpc, NormalizedNode<?, ?> node);

    DOMRpcResult toRpcResult(M message, SchemaPath rpc);

    /**
     * Parse action into message for request.
     *
     * @param action - action schema path
     * @param domDataTreeIdentifier - identifier of action
     * @param payload - input of action
     * @return message
     */
    default M toActionRequest(SchemaPath action, DOMDataTreeIdentifier domDataTreeIdentifier, NormalizedNode<?,
            ?> payload) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parse result of invoking action into DOM result.
     *
     * @param action - action schema path
     * @param message - message to parsing
     * @return {@link DOMActionResult}
     */
    default DOMActionResult toActionResult(SchemaPath action, M message) {
        throw new UnsupportedOperationException();
    }
}
