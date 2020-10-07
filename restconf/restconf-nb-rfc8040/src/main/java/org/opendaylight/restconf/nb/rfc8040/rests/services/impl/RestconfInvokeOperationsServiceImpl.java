/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.net.URI;
import java.util.Optional;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

/**
 * Implementation of {@link RestconfInvokeOperationsService}.
 *
 */
@Path("/")
public class RestconfInvokeOperationsServiceImpl implements RestconfInvokeOperationsService {

    private volatile RpcServiceHandler rpcServiceHandler;
    private volatile SchemaContextHandler schemaContextHandler;

    public RestconfInvokeOperationsServiceImpl(final RpcServiceHandler rpcServiceHandler,
            final SchemaContextHandler schemaContextHandler) {
        this.rpcServiceHandler = rpcServiceHandler;
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public synchronized void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            } else if (object instanceof RpcServiceHandler) {
                rpcServiceHandler = (RpcServiceHandler) object;
            }
        }
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        final EffectiveModelContext refSchemaCtx = this.schemaContextHandler.get();
        final QName schemaPath = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final URI namespace = payload.getInstanceIdentifierContext().getSchemaNode().getQName().getNamespace();

        final DOMRpcResult response;
        final EffectiveModelContext schemaContextRef;
        if (mountPoint == null) {
            if (namespace.equals(RestconfStreamsConstants.SAL_REMOTE_NAMESPACE.getNamespace())) {
                if (identifier.contains(RestconfStreamsConstants.CREATE_DATA_SUBSCRIPTION)) {
                    response = CreateStreamUtil.createDataChangeNotifiStream(payload, refSchemaCtx);
                } else {
                    throw new RestconfDocumentedException("Not supported operation", ErrorType.RPC,
                            ErrorTag.OPERATION_NOT_SUPPORTED);
                }
            } else {
                response = RestconfInvokeOperationsUtil.invokeRpc(payload.getData(), schemaPath,
                        this.rpcServiceHandler);
            }
            schemaContextRef = this.schemaContextHandler.get();
        } else {
            response = RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(mountPoint, payload.getData(), schemaPath);
            schemaContextRef = modelContext(mountPoint);
        }

        final DOMRpcResult result = RestconfInvokeOperationsUtil.checkResponse(response);

        RpcDefinition resultNodeSchema = null;
        NormalizedNode<?, ?> resultData = null;
        if (result != null && result.getResult() != null) {
            resultData = result.getResult();
            resultNodeSchema = (RpcDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        }

        if (resultData != null && ((ContainerNode) resultData).getValue().isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        } else {
            return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, resultNodeSchema, mountPoint,
                    schemaContextRef), resultData);
        }
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
