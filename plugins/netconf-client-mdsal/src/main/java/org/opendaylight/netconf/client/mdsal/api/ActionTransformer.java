/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * Interface for transforming NETCONF device action request/response messages.
 */
public interface ActionTransformer {
    /**
     * Parse action into message for request.
     *
     * @param action - action schema path
     * @param domDataTreeIdentifier - identifier of action
     * @param payload - input of action
     * @return message
     */
    NetconfMessage toActionRequest(Absolute action, DOMDataTreeIdentifier domDataTreeIdentifier,
            NormalizedNode payload);

    /**
     * Parse result of invoking action into DOM result.
     *
     * @param action - action schema path
     * @param message - message to parsing
     * @return {@link DOMActionResult}
     */
    DOMActionResult toActionResult(Absolute action, NetconfMessage message);
}
