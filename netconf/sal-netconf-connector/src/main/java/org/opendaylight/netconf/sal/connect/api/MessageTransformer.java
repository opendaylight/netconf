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
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public interface MessageTransformer<M> {

    DOMNotification toNotification(M message);

    M toRpcRequest(QName rpc, NormalizedNode<?, ?> node);

    DOMRpcResult toRpcResult(M message, QName rpc);

    /**
     * Parse action into message for request.
     *
     * @param action - action schema path
     * @param domDataTreeIdentifier - identifier of action
     * @param payload - input of action
     * @return message
     */
    default M toActionRequest(final Absolute action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode<?, ?> payload) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parse result of invoking action into DOM result.
     *
     * @param action - action schema path
     * @param message - message to parsing
     * @return {@link DOMActionResult}
     */
    default DOMActionResult toActionResult(final Absolute action, final M message) {
        throw new UnsupportedOperationException();
    }
}
