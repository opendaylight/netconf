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
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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

    // FIXME: NETCONF-773: this recursion should really live in MdsalRestconfServer
    public static @NonNull InstanceIdentifierContext ofApiPath(final ApiPath path, final DatabindContext databind,
            final DOMMountPointService mountPointService) {
        final var steps = path.steps();
        final var limit = steps.size() - 1;

        var prefix = 0;
        DOMMountPoint currentMountPoint = null;
        var currentDatabind = databind;
        while (prefix <= limit) {
            final var mount = indexOfMount(steps, prefix, limit);
            if (mount == -1) {
                break;
            }

            final var mountService = currentMountPoint == null ? mountPointService
                : currentMountPoint.getService(DOMMountPointService.class).orElse(null);
            if (mountService == null) {
                throw new RestconfDocumentedException("Mount point service is not available",
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
            }

            final var mountPath = IdentifierCodec.deserialize(path.subPath(prefix, mount), databind);
            final var userPath = path.subPath(0, mount);
            final var nextMountPoint = mountService.getMountPoint(mountPath)
                .orElseThrow(() -> new RestconfDocumentedException("Mount point '" + userPath + "' does not exist",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT));
            final var nextModelContext = nextMountPoint.getService(DOMSchemaService.class)
                .orElseThrow(() -> new RestconfDocumentedException(
                    "Mount point '" + userPath + "' does not expose DOMSchemaService",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT))
                .getGlobalContext();
            if (nextModelContext == null) {
                throw new RestconfDocumentedException("Mount point '" + userPath + "' does not have any models",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT);
            }

            prefix = mount + 1;
            currentDatabind = DatabindContext.ofModel(nextModelContext);
            currentMountPoint = nextMountPoint;
        }

        final var result = YangInstanceIdentifierDeserializer.create(currentDatabind, path.subPath(prefix));
        return InstanceIdentifierContext.ofPath(currentDatabind, result.stack, result.node, result.path,
            currentMountPoint);
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

    public static @NonNull InstanceIdentifierContext ofMountPointRoot(final DatabindContext databind,
            final DOMMountPoint mountPoint) {
        return new Root(databind, requireNonNull(mountPoint));
    }

    @VisibleForTesting
    public static @NonNull InstanceIdentifierContext ofMountPointPath(final DatabindContext databind,
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        return DataPath.of(databind, path, requireNonNull(mountPoint));
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
