/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public abstract class InstanceIdentifierContext {
    private static final class Root extends InstanceIdentifierContext {
        private final @NonNull EffectiveModelContext context;

        Root(final EffectiveModelContext context, final DOMMountPoint mountPoint) {
            super(context, mountPoint);
            this.context = requireNonNull(context);
        }

        @Override
        public EffectiveModelContext getSchemaContext() {
            return context;
        }

        @Override
        public YangInstanceIdentifier getInstanceIdentifier() {
            return YangInstanceIdentifier.empty();
        }

        @Override
        public Inference inference() {
            return SchemaInferenceStack.of(context).toInference();
        }
    }

    private static final class DataPath extends InstanceIdentifierContext {
        private final @NonNull YangInstanceIdentifier path;
        private final @NonNull SchemaInferenceStack stack;

        private DataPath(final DataSchemaNode schemaNode, final DOMMountPoint mountPoint,
                final SchemaInferenceStack stack, final YangInstanceIdentifier path) {
            super(schemaNode, mountPoint);
            this.stack = requireNonNull(stack);
            this.path = requireNonNull(path);
        }

        static @NonNull DataPath of(final EffectiveModelContext context, final YangInstanceIdentifier path,
                final DOMMountPoint mountPoint) {
            final var nodeAndStack = DataSchemaContextTree.from(context).enterPath(path).orElseThrow();
            return new DataPath(nodeAndStack.node().getDataSchemaNode(), mountPoint, nodeAndStack.stack(), path);
        }

        @Override
        public YangInstanceIdentifier getInstanceIdentifier() {
            return path;
        }

        @Override
        public Inference inference() {
            return stack.toInference();
        }
    }

    private static final class WithoutDataPath extends InstanceIdentifierContext {
        private final @NonNull SchemaInferenceStack stack;

        private WithoutDataPath(final DataSchemaNode schemaNode, final DOMMountPoint mountPoint,
                final SchemaInferenceStack stack) {
            super(schemaNode, mountPoint);
            this.stack = requireNonNull(stack);
        }

        @Override
        public Inference inference() {
            return stack.toInference();
        }

        @Override
        public @Nullable YangInstanceIdentifier getInstanceIdentifier() {
            return null;
        }
    }

    private final @NonNull SchemaNode schemaNode;
    private final @Nullable DOMMountPoint mountPoint;

    InstanceIdentifierContext(final SchemaNode schemaNode, final DOMMountPoint mountPoint) {
        this.schemaNode = requireNonNull(schemaNode);
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

    public static @NonNull InstanceIdentifierContext ofLocalRoot(final EffectiveModelContext context) {
        return new Root(context, null);
    }

    @VisibleForTesting
    public static @NonNull InstanceIdentifierContext ofLocalPath(final EffectiveModelContext context,
            final YangInstanceIdentifier path) {
        return DataPath.of(context, path, null);
    }

    // Legacy bierman02 invokeRpc()
    public static @NonNull InstanceIdentifierContext ofLocalRpc(final EffectiveModelContext context,
        // FIXME: this this method really needed?
            final RpcDefinition rpc) {
        return new InstanceIdentifierContext(context, rpc, null);
    }

    // Invocations of various identifier-less details
    public static @NonNull InstanceIdentifierContext ofDataSchemaNode(final EffectiveModelContext context,
            final DataSchemaNode schemaNode) {
        return new WithoutDataPath(schemaNode, null,
            SchemaInferenceStack.ofInstantiatedPath(context, schemaNode.getPath()));
    }

    // Invocations of various identifier-less details, potentially having a mount point
    public static @NonNull InstanceIdentifierContext ofDataSchemaNode(final EffectiveModelContext context,
            final DataSchemaNode schemaNode, final @Nullable DOMMountPoint mountPoint) {
        return new InstanceIdentifierContext(null, requireNonNull(schemaNode), mountPoint, requireNonNull(context));
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
        return new Root(mountContext, requireNonNull(mountPoint));
    }

    @VisibleForTesting
    public static @NonNull InstanceIdentifierContext ofMountPointPath(final DOMMountPoint mountPoint,
            final EffectiveModelContext context, final YangInstanceIdentifier path) {
        return DataPath.of(context, path, requireNonNull(mountPoint));
    }

    public static @NonNull InstanceIdentifierContext ofMountPointRpc(final DOMMountPoint mountPoint,
            final EffectiveModelContext mountContext, final RpcDefinition rpc) {
        return new InstanceIdentifierContext(mountContext, rpc, requireNonNull(mountPoint));
    }

    public static @NonNull InstanceIdentifierContext ofMountPointRpcOutput(final DOMMountPoint mountPoint,
            final EffectiveModelContext mountContext, final RpcDefinition rpc) {
        return new InstanceIdentifierContext(mountContext, rpc, requireNonNull(mountPoint));
    }

    // FIXME: what the heck are the callers of this doing?!
    public @NonNull InstanceIdentifierContext withConcatenatedArgs(final List<PathArgument> concatArgs) {
        final var path = getInstanceIdentifier();
        if (path == null || concatArgs.isEmpty()) {
            return this;
        }
        final var newInstanceIdentifier = YangInstanceIdentifier.create(
            Iterables.concat(path.getPathArguments(), concatArgs));


        return new InstanceIdentifierContext(newInstanceIdentifier, schemaNode, mountPoint, schemaContext);
    }

    public final @NonNull SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public final @Nullable DOMMountPoint getMountPoint() {
        return mountPoint;
    }

    public @NonNull EffectiveModelContext getSchemaContext() {
        return inference().getEffectiveModelContext();
    }

    public abstract @NonNull Inference inference();

    public abstract @Nullable YangInstanceIdentifier getInstanceIdentifier();
}
