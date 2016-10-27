/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import java.util.Date;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Custom notification which is created by slave when master gets and sends notification targeted to slave.
 */
public class NetconfRemoteDOMNotification implements DOMNotification, DOMEvent {

    private final ContainerNode containerNode;
    private final SchemaPath schemaPath;
    private final Date eventTime;

    public NetconfRemoteDOMNotification(final ContainerNode containerNode, final SchemaPath schemaPath,
                                        final Date eventTime) {
        this.containerNode = containerNode;
        this.schemaPath = schemaPath;
        this.eventTime = eventTime;
    }

    @Override
    public Date getEventTime() {
        return eventTime;
    }

    @Nonnull
    @Override
    public SchemaPath getType() {
        return schemaPath;
    }

    @Nonnull
    @Override
    public ContainerNode getBody() {
        return containerNode;
    }
}
