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
import com.google.common.collect.ImmutableList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierDeserializer;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
    }

    private final @NonNull SchemaNode schemaNode;
    private final @Nullable DOMMountPoint mountPoint;

    InstanceIdentifierContext(final SchemaNode schemaNode, final DOMMountPoint mountPoint) {
        this.schemaNode = requireNonNull(schemaNode);
        this.mountPoint = mountPoint;
    }

    // FIXME: NETCONF-773: this recursion should really live in MdsalRestconfServer
    public static @NonNull InstanceIdentifierContext ofApiPath(final ApiPath path,
            final EffectiveModelContext modelContext, final DOMMountPointService mountPointService) {
        final var steps = path.steps();
        final var limit = steps.size() - 1;

        var prefix = 0;
        var currentModelContext = modelContext;
        DOMMountPoint currentMountPoint = null;
        var currentMountService = mountPointService;
        while (prefix <= limit) {
            final var mount = indexOfMount(steps, limit, prefix);
            if (mount == -1) {
                break;
            }

            final var mountPath = IdentifierCodec.deserialize(path.subPath(prefix, mount - 1), modelContext);
            if (mountPointService == null) {
                throw new RestconfDocumentedException("No mount points available in " + path.subPath(0, mount - 1),
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT);
            }
            final var userPath = path.subPath(0, i);
            final var nextMountPoint = mountPointService.getMountPoint(mountPath)
                .orElseThrow(() -> new RestconfDocumentedException("Mount point " + userPath + " does not exist",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT));
            final var nextModuleContext = nextMountPoint.getService(DOMSchemaService.class)
                .orElseThrow(() -> new RestconfDocumentedException(
                    "Mount point " + userPath + " does not expose DOMSchemaService",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT))
                .getGlobalContext();

        }

        final var result = YangInstanceIdentifierDeserializer.create(currentModelContext, path.subPath(prefix));
        return InstanceIdentifierContext.ofPath(result.stack, result.node, result.path, currentMountPoint);
//
//        for (int i = prefix, size = steps.size(); i < size; ++i) {
//            final var step = steps.get(i);
//            if ("yang-ext".equals(step.module()) && "mount".equals(step.identifier().getLocalName())) {
//
//                final var userPath = path.subPath(0, i);
//                return ofApiPath(path, i + 1, mount, schemaService.getGlobalContext(),
//                    mount.getService(DOMMountPointService.class).orElse(null));
//            }
//        }
//
//        final var result = YangInstanceIdentifierDeserializer.create(modelContext, path.subPath(prefix));
//        return InstanceIdentifierContext.ofPath(result.stack, result.node, result.path, mountPoint);
    }

    private static int indexOfMount(final ImmutableList<Step> steps, final int fromIndex, final int limit) {
        for (int i = fromIndex; i <= limit; ++ i) {
            final var step = steps.get(i);
            if ("yang-ext".equals(step.module()) && "mount".equals(step.identifier().getLocalName())) {
                return i;
            }
        }
        return -1;
    }

    public static @NonNull InstanceIdentifierContext ofLocalRoot(final EffectiveModelContext context) {
        return new Root(context, null);
    }

    @VisibleForTesting
    public static @NonNull InstanceIdentifierContext ofLocalPath(final EffectiveModelContext context,
            final YangInstanceIdentifier path) {
        return DataPath.of(context, path, null);
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
