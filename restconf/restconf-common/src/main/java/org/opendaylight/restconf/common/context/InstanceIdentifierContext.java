/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class InstanceIdentifierContext {
    private final YangInstanceIdentifier instanceIdentifier;
    private final SchemaNode schemaNode;
    private final DOMMountPoint mountPoint;
    private final EffectiveModelContext schemaContext;

    public InstanceIdentifierContext(final YangInstanceIdentifier instanceIdentifier, final SchemaNode schemaNode,
            final DOMMountPoint mountPoint, final EffectiveModelContext context) {
        this.instanceIdentifier = instanceIdentifier;
        this.schemaNode = schemaNode;
        this.mountPoint = mountPoint;
        schemaContext = context;
    }

    public YangInstanceIdentifier getInstanceIdentifier() {
        return instanceIdentifier;
    }

    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public DOMMountPoint getMountPoint() {
        return mountPoint;
    }

    public EffectiveModelContext getSchemaContext() {
        return schemaContext;
    }
}
