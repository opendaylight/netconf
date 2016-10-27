/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serializable;
import java.util.Date;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;


/**
 * Master sends this message to slave with DOMNotification parameters, which will obtained by publishing
 * notification by device.
 */
public class InvokeNotificationListener implements Serializable {
    private static final long serialVersionUID = 1L;

    private final SchemaPathMessage schemaPathMessage;
    private final NormalizedNodeMessage normalizedNodeMessage;
    private final Date eventTime;

    public InvokeNotificationListener(final SchemaPath type, final ContainerNode body, final Date eventTime) {
        schemaPathMessage = new SchemaPathMessage(type);
        normalizedNodeMessage = new NormalizedNodeMessage(YangInstanceIdentifier.EMPTY, body);
        this.eventTime = eventTime;
    }

    public SchemaPath getSchemaPath() {
        return schemaPathMessage.getSchemaPath();
    }

    public ContainerNode getContainerNode() {
        return (ContainerNode) normalizedNodeMessage.getNode();
    }

    public Date getEventTime() {
        return eventTime;
    }
}
