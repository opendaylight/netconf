/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import static com.google.common.base.Verify.verify;
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
            return YangInstanceIdentifier.of();
        }

        @Override
        public Inference inference() {
            return SchemaInferenceStack.of(context).toInference();
        }

        @Override
        InstanceIdentifierContext createWithConcapt(final List<PathArgument> concatArgs) {
            return new DataPath(context, getMountPoint(), SchemaInferenceStack.of(context),
                YangInstanceIdentifier.of(concatArgs));
        }
    }

    private static final class DataPath extends InstanceIdentifierContext {
        private final @NonNull YangInstanceIdentifier path;
        private final @NonNull SchemaInferenceStack stack;

        private DataPath(final SchemaNode schemaNode, final DOMMountPoint mountPoint,
                final SchemaInferenceStack stack, final YangInstanceIdentifier path) {
            super(schemaNode, mountPoint);
            this.stack = requireNonNull(stack);
            this.path = requireNonNull(path);
        }

        static @NonNull DataPath of(final EffectiveModelContext context, final YangInstanceIdentifier path,
                final DOMMountPoint mountPoint) {
            final var nodeAndStack = DataSchemaContextTree.from(context).enterPath(path).orElseThrow();
            return new DataPath(nodeAndStack.node().dataSchemaNode(), mountPoint, nodeAndStack.stack(), path);
        }

        @Override
        public YangInstanceIdentifier getInstanceIdentifier() {
            return path;
        }

        @Override
        public Inference inference() {
            return stack.toInference();
        }

        @Override
        @NonNull
        InstanceIdentifierContext createWithConcapt(final List<PathArgument> concatArgs) {
            final var newInstanceIdentifier = YangInstanceIdentifier.of(
                Iterables.concat(path.getPathArguments(), concatArgs));
            return new DataPath(getSchemaNode(), getMountPoint(), stack, newInstanceIdentifier);
        }
    }

    private static final class WithoutDataPath extends InstanceIdentifierContext {
        private final @NonNull SchemaInferenceStack stack;

        private WithoutDataPath(final SchemaNode schemaNode, final DOMMountPoint mountPoint,
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

        @Override
        InstanceIdentifierContext createWithConcapt(final List<PathArgument> concatArgs) {
            return this;
        }
    }

    private final @NonNull SchemaNode schemaNode;
    private final @Nullable DOMMountPoint mountPoint;

    InstanceIdentifierContext(final SchemaNode schemaNode, final DOMMountPoint mountPoint) {
        this.schemaNode = requireNonNull(schemaNode);
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
    public static @NonNull InstanceIdentifierContext ofRpcInput(final EffectiveModelContext context,
            // FIXME: this this method really needed?
            final RpcDefinition rpc, final @Nullable DOMMountPoint mountPoint) {
        final var stack = SchemaInferenceStack.of(context);
        stack.enterSchemaTree(rpc.getQName());
        stack.enterSchemaTree(rpc.getInput().getQName());
        return new WithoutDataPath(rpc, mountPoint, stack);
    }

    public static @NonNull InstanceIdentifierContext ofRpcOutput(final EffectiveModelContext context,
            // FIXME: this this method really needed?
            final RpcDefinition rpc, final @Nullable DOMMountPoint mountPoint) {
        final var stack = SchemaInferenceStack.of(context);
        stack.enterSchemaTree(rpc.getQName());
        stack.enterSchemaTree(rpc.getOutput().getQName());
        return new WithoutDataPath(rpc, mountPoint, stack);
    }

    // Invocations of various identifier-less details
    public static @NonNull InstanceIdentifierContext ofStack(final SchemaInferenceStack stack) {
        return ofStack(stack, null);
    }

    // Invocations of various identifier-less details, potentially having a mount point
    public static @NonNull InstanceIdentifierContext ofStack(final SchemaInferenceStack stack,
            final @Nullable DOMMountPoint mountPoint) {
        final SchemaNode schemaNode;
        if (!stack.isEmpty()) {
            final var stmt = stack.currentStatement();
            verify(stmt instanceof SchemaNode, "Unexpected statement %s", stmt);
            schemaNode = (SchemaNode) stmt;
        } else {
            schemaNode = stack.getEffectiveModelContext();
        }

        return new WithoutDataPath(schemaNode, mountPoint, stack);
    }

    public static @NonNull InstanceIdentifierContext ofLocalRpcInput(final EffectiveModelContext context,
            // FIXME: this this method really needed?
            final RpcDefinition rpc) {
        final var stack = SchemaInferenceStack.of(context);
        stack.enterSchemaTree(rpc.getQName());
        stack.enterSchemaTree(rpc.getInput().getQName());
        return new WithoutDataPath(rpc.getInput(), null, stack);
    }

    public static @NonNull InstanceIdentifierContext ofLocalRpcOutput(final EffectiveModelContext context,
            // FIXME: we want to re-validate this, so might as well take a QName
            final RpcDefinition rpc) {
        final var stack = SchemaInferenceStack.of(context);
        stack.enterSchemaTree(rpc.getQName());
        stack.enterSchemaTree(rpc.getOutput().getQName());
        return new WithoutDataPath(rpc, null, stack);
    }

    public static @NonNull InstanceIdentifierContext ofPath(final SchemaInferenceStack stack,
            final SchemaNode schemaNode, final YangInstanceIdentifier path,
            final @Nullable DOMMountPoint mountPoint) {
        return new DataPath(schemaNode, mountPoint, stack, path);
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

    public static @NonNull InstanceIdentifierContext ofMountPointRpcOutput(final DOMMountPoint mountPoint,
            final EffectiveModelContext mountContext, final RpcDefinition rpc) {
        final var stack = SchemaInferenceStack.of(mountContext);
        stack.enterSchemaTree(rpc.getQName());
        stack.enterSchemaTree(rpc.getOutput().getQName());
        return new WithoutDataPath(rpc, requireNonNull(mountPoint), stack);
    }

    // FIXME: what the heck are the callers of this doing?!
    public final @NonNull InstanceIdentifierContext withConcatenatedArgs(final List<PathArgument> concatArgs) {
        return concatArgs.isEmpty() ? this : createWithConcapt(concatArgs);
    }

    abstract @NonNull InstanceIdentifierContext createWithConcapt(List<PathArgument> concatArgs);

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
