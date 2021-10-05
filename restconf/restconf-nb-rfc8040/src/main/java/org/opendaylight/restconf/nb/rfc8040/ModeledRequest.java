/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierDeserializer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * This is a placeholder utility class for now, but it has a bright future ahead. We currently host only a
 * InstanceIdentifierContext bridge via {@link #createIdentifierContext(String, EffectiveModelContext, Optional)}.
 *
 * <p>
 * FIXME: in a galaxy far, far away:
 *        A request context binds a particular JAX-RS request to an EffectiveModelContext when considering the payload.
 *        As such, we can have have nested {@code yang-ext:mount} invocations and this class provides the abstraction
 *        for dealing with the local part -- with the next part being available, if the user can resolve the next
 *        EffectiveModelContext. Local execution context is reached when there is no next part.
 */
public final class ModeledRequest {
    private ModeledRequest() {
        // Hidden on purpose
    }

    public static InstanceIdentifierContext<?> createIdentifierContext(final String identifier,
            final EffectiveModelContext schemaContext, final Optional<DOMMountPointService> mountPointService) {
        final var path = ApiPath.valueOf(identifier);
        final var mountOffset = path.indexOf("yang-ext", "mount");
        if (mountOffset == -1) {
            // FIXME: we should receiving the schema as well
            final YangInstanceIdentifier inst = YangInstanceIdentifierDeserializer.bindPath(schemaContext, path);
            return new InstanceIdentifierContext<>(inst, getPathSchema(schemaContext, inst), null, schemaContext);
        }

        // We cannot lookup mount points, there is not point to go any further
        final var mps = mountPointService.orElseThrow(
            () ->  new RestconfDocumentedException("Mount point service is not available"));

        // Mountpoint
        final YangInstanceIdentifier mountPath = YangInstanceIdentifierDeserializer.bindPath(schemaContext,
            path.subPath(0, mountOffset));
        final DOMMountPoint mountPoint = mountPointService.get().getMountPoint(mountPath)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point does not exist.",
                ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT));

        final EffectiveModelContext mountSchemaContext = coerceModelContext(mountPoint);
        final YangInstanceIdentifier inst = YangInstanceIdentifierDeserializer.bindPath(mountSchemaContext,
            path.subPath(mountOffset + 1));

        // FIXME: we should receiving the schema as well
        return new InstanceIdentifierContext<>(inst, getPathSchema(mountSchemaContext, inst),
            mountPoint, mountSchemaContext);
    }

    // FIXME: hide this
    public static SchemaNode getPathSchema(final EffectiveModelContext schemaContext,
            final YangInstanceIdentifier urlPath) {
        // First things first: an empty path means data invocation on SchemaContext
        if (urlPath.isEmpty()) {
            return schemaContext;
        }

        // Peel the last component and locate the parent data node, empty path resolves to SchemaContext
        final DataSchemaContextNode<?> parent = DataSchemaContextTree.from(schemaContext)
                .findChild(verifyNotNull(urlPath.getParent()))
                .orElseThrow(
                    // Parent data node is not present, this is not a valid location.
                    () -> new RestconfDocumentedException("Parent of " + urlPath + " not found",
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));

        // Now try to resolve the last component as a data item...
        final DataSchemaContextNode<?> data = parent.getChild(urlPath.getLastPathArgument());
        if (data != null) {
            return data.getDataSchemaNode();
        }

        // ... otherwise this has to be an operation invocation. RPCs cannot be defined anywhere but schema root,
        // actions can reside everywhere else (and SchemaContext reports them empty)
        final QName qname = urlPath.getLastPathArgument().getNodeType();
        final DataSchemaNode parentSchema = parent.getDataSchemaNode();
        if (parentSchema instanceof SchemaContext) {
            for (final RpcDefinition rpc : ((SchemaContext) parentSchema).getOperations()) {
                if (qname.equals(rpc.getQName())) {
                    return rpc;
                }
            }
        }
        if (parentSchema instanceof ActionNodeContainer) {
            for (final ActionDefinition action : ((ActionNodeContainer) parentSchema).getActions()) {
                if (qname.equals(action.getQName())) {
                    return action;
                }
            }
        }

        // No luck: even if we found the parent, we did not locate a data, nor RPC, nor action node, hence the URL
        //          is deemed invalid
        throw new RestconfDocumentedException("Context for " + urlPath + " not found", ErrorType.PROTOCOL,
            ErrorTag.INVALID_VALUE);
    }

    private static EffectiveModelContext coerceModelContext(final DOMMountPoint mountPoint) {
        final EffectiveModelContext context = modelContext(mountPoint);
        // FIXME: RestconfDocumentedException
        checkState(context != null, "Mount point %s does not have a model context", mountPoint);
        return context;
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
