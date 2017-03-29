/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.messages;

import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Since {@link ContainerNode} is not serializable, this class must be used to send notifications in cluster.
 */
public class NotificationMessage extends AbstractMessage<ContainerNode> {

    public NotificationMessage() {
        super();
    }

    public NotificationMessage(final DOMNotification notification) {
        super(notification.getType(), notification.getBody());
    }

}
