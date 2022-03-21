/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class InstanceIdentifierContext {
    private final YangInstanceIdentifier instanceIdentifier;
    private final SchemaNode schemaNode;
    private final DOMMountPoint mountPoint;
    private final EffectiveModelContext schemaContext;

    private InstanceIdentifierContext(final EffectiveModelContext context, final DOMMountPoint mountPoint) {
        instanceIdentifier = YangInstanceIdentifier.empty();
        schemaContext = requireNonNull(context);
        schemaNode = schemaContext;
        this.mountPoint = mountPoint;
    }

    private InstanceIdentifierContext(final EffectiveModelContext context, final RpcDefinition rpc,
            final DOMMountPoint mountPoint) {
        instanceIdentifier = null;
        schemaContext = requireNonNull(context);
        schemaNode = requireNonNull(rpc);
        this.mountPoint = mountPoint;
    }

    private InstanceIdentifierContext(final EffectiveModelContext context, final ContainerLike rpcInputOutput,
            final DOMMountPoint mountPoint) {
        instanceIdentifier = null;
        schemaContext = requireNonNull(context);
        schemaNode = requireNonNull(rpcInputOutput);
        this.mountPoint = mountPoint;
    }

    @Deprecated(forRemoval = true)
    public InstanceIdentifierContext(final YangInstanceIdentifier instanceIdentifier, final SchemaNode schemaNode,
            final DOMMountPoint mountPoint, final EffectiveModelContext context) {
        this.instanceIdentifier = instanceIdentifier;
        this.schemaNode = schemaNode;
        this.mountPoint = mountPoint;
        schemaContext = context;
    }

    public static @NonNull InstanceIdentifierContext ofLocalRoot(final EffectiveModelContext context) {
        return new InstanceIdentifierContext(context, null);
    }

    public static @NonNull InstanceIdentifierContext ofLocalRpcInput(final EffectiveModelContext context,
            // FIXME: this this method really needed?
            final RpcDefinition rpc) {
        return new InstanceIdentifierContext(context, rpc.getInput(), null);
    }

    public static @NonNull InstanceIdentifierContext ofLocalRpcOutput(final EffectiveModelContext context,
            // FIXME: we want to re-validate this, so might as well take a QName
            final RpcDefinition rpc) {
        return new InstanceIdentifierContext(context, rpc, null);
    }

    public static @NonNull InstanceIdentifierContext ofMountPointRoot(final DOMMountPoint mountPoint,
            final EffectiveModelContext mountContext) {
        return new InstanceIdentifierContext(mountContext, requireNonNull(mountPoint));
    }

    public static @NonNull InstanceIdentifierContext ofMountPointRpcOutput(final DOMMountPoint mountPoint,
            final EffectiveModelContext mountContext, final RpcDefinition rpc) {
        return new InstanceIdentifierContext(mountContext, rpc, requireNonNull(mountPoint));
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
