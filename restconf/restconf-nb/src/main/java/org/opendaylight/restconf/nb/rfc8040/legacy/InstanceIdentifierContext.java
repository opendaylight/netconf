/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public abstract class InstanceIdentifierContext {
    private static final class Root extends InstanceIdentifierContext {
        Root(final DatabindContext databind, final DOMMountPoint mountPoint) {
            super(databind, databind.modelContext(), mountPoint);
        }

        @Override
        public YangInstanceIdentifier getInstanceIdentifier() {
            return YangInstanceIdentifier.of();
        }

        @Override
        public Inference inference() {
            return SchemaInferenceStack.of(databind().modelContext()).toInference();
        }
    }

    private static final class DataPath extends InstanceIdentifierContext {
        private final @NonNull YangInstanceIdentifier path;
        private final @NonNull SchemaInferenceStack stack;

        private DataPath(final DatabindContext databind, final SchemaNode schemaNode, final DOMMountPoint mountPoint,
                final SchemaInferenceStack stack, final YangInstanceIdentifier path) {
            super(databind, schemaNode, mountPoint);
            this.stack = requireNonNull(stack);
            this.path = requireNonNull(path);
        }

        static @NonNull DataPath of(final DatabindContext databind, final YangInstanceIdentifier path,
                final DOMMountPoint mountPoint) {
            final var nodeAndStack = databind.schemaTree().enterPath(path).orElseThrow();
            return new DataPath(databind, nodeAndStack.node().dataSchemaNode(), mountPoint, nodeAndStack.stack(), path);
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

        private WithoutDataPath(final DatabindContext databind, final SchemaNode schemaNode,
                final DOMMountPoint mountPoint, final SchemaInferenceStack stack) {
            super(databind, schemaNode, mountPoint);
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

    private final @NonNull DatabindContext databind;
    private final @NonNull SchemaNode schemaNode;
    private final @Nullable DOMMountPoint mountPoint;

    InstanceIdentifierContext(final DatabindContext databind, final SchemaNode schemaNode,
            final DOMMountPoint mountPoint) {
        this.databind = requireNonNull(databind);
        this.schemaNode = requireNonNull(schemaNode);
        this.mountPoint = mountPoint;
    }

    public static @NonNull InstanceIdentifierContext ofLocalRoot(final DatabindContext databind) {
        return new Root(databind, null);
    }

    @VisibleForTesting
    public static @NonNull InstanceIdentifierContext ofLocalPath(final DatabindContext databind,
            final YangInstanceIdentifier path) {
        return DataPath.of(databind, path, null);
    }

    // Invocations of various identifier-less details
    public static @NonNull InstanceIdentifierContext ofStack(final DatabindContext databind,
            final SchemaInferenceStack stack) {
        return ofStack(databind, stack, null);
    }

    // Invocations of various identifier-less details, potentially having a mount point
    public static @NonNull InstanceIdentifierContext ofStack(final DatabindContext databind,
            final SchemaInferenceStack stack, final @Nullable DOMMountPoint mountPoint) {
        final SchemaNode schemaNode;
        if (!stack.isEmpty()) {
            final var stmt = stack.currentStatement();
            verify(stmt instanceof SchemaNode, "Unexpected statement %s", stmt);
            schemaNode = (SchemaNode) stmt;
        } else {
            schemaNode = stack.getEffectiveModelContext();
        }

        return new WithoutDataPath(databind, schemaNode, mountPoint, stack);
    }

    public static @NonNull InstanceIdentifierContext ofPath(final DatabindContext databind,
            final SchemaInferenceStack stack, final SchemaNode schemaNode, final YangInstanceIdentifier path,
            final @Nullable DOMMountPoint mountPoint) {
        return new DataPath(databind, schemaNode, mountPoint, stack, path);
    }

    public final @NonNull DatabindContext databind() {
        return databind;
    }

    public final @NonNull SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public final @Nullable DOMMountPoint getMountPoint() {
        return mountPoint;
    }

    public abstract @NonNull Inference inference();

    public abstract @Nullable YangInstanceIdentifier getInstanceIdentifier();
}
