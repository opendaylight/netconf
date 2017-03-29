/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.common;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Represents notification received from restconf device.
 */
public class RestconfDeviceNotification implements DOMNotification {
    @Nullable
    private final String eventTime;
    private final SchemaPath schemaPath;
    private final ContainerNode content;

    public RestconfDeviceNotification(final ContainerNode content, @Nullable final String eventTime) {
        this.content = content;
        this.eventTime = eventTime;
        this.schemaPath = toPath(content.getNodeType());
    }

    @Nonnull
    @Override
    public SchemaPath getType() {
        return schemaPath;

    }

    @Nonnull
    @Override
    public ContainerNode getBody() {
        return content;
    }

    @Nullable
    public String getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final RestconfDeviceNotification that = (RestconfDeviceNotification) o;

        if (eventTime != null ? !eventTime.equals(that.eventTime) : that.eventTime != null) {
            return false;
        }
        if (schemaPath != null ? !schemaPath.equals(that.schemaPath) : that.schemaPath != null) {
            return false;
        }
        return content != null ? content.equals(that.content) : that.content == null;
    }

    @Override
    public int hashCode() {
        int result = eventTime != null ? eventTime.hashCode() : 0;
        result = 31 * result + (schemaPath != null ? schemaPath.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        return result;
    }
}
