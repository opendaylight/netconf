/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.parser;

import java.io.InputStream;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Transforms restconf server reply to binding independent form.
 */
public interface Parser {
    /**
     * This is called to parse response message from a remote node to normalized nodes
     *
     * @param path - data identifier
     * @param body - response from remote node
     * @return response in xml parsed to normalized nodes
     */
    NormalizedNode<?, ?> parse(final YangInstanceIdentifier path, final InputStream body);

    /**
     * This is called to parse a rpc response message to normalized node. If rpc message
     * is empty, null will be returned
     *
     * @param path - data identifier
     * @param body - response from remote node
     * @return rpc output received from remote node parsed to normalized nodes
     */
    @Nullable
    NormalizedNode<?, ?> parseRpcOutput(final SchemaPath path, final InputStream body);

    /**
     * This is called to parse a notification.
     *
     * @param notificationString - notification
     * @return dom notification
     */
    DOMNotification parseNotification(final String notificationString);
}
