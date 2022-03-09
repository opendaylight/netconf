/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;

public class InstanceIdentifierContext<T extends SchemaNode> {

    private final YangInstanceIdentifier instanceIdentifier;
    private final @Nullable SchemaNodeIdentifier schemaNodeIdentifier;
    private final T schemaNode;
    private final DOMMountPoint mountPoint;
    private final EffectiveModelContext schemaContext;

    public InstanceIdentifierContext(final YangInstanceIdentifier instanceIdentifier,
                                     final T schemaNode, final DOMMountPoint mountPoint,
                                     final EffectiveModelContext context) {
        this(instanceIdentifier, null, schemaNode, mountPoint, context);
    }

    public InstanceIdentifierContext(final YangInstanceIdentifier instanceIdentifier,
                                     final SchemaNodeIdentifier schemaNodeIdentifier,
                                     final T schemaNode, final DOMMountPoint mountPoint,
                                     final EffectiveModelContext context) {
        this.instanceIdentifier = instanceIdentifier;
        this.schemaNodeIdentifier = schemaNodeIdentifier;
        this.schemaNode = schemaNode;
        this.mountPoint = mountPoint;
        this.schemaContext = context;
    }

    public YangInstanceIdentifier getInstanceIdentifier() {
        return instanceIdentifier;
    }

    public SchemaNodeIdentifier getSchemaNodeIdentifier() {
        return schemaNodeIdentifier;
    }

    public T getSchemaNode() {
        return schemaNode;
    }

    public DOMMountPoint getMountPoint() {
        return mountPoint;
    }

    public EffectiveModelContext getSchemaContext() {
        return schemaContext;
    }
}
