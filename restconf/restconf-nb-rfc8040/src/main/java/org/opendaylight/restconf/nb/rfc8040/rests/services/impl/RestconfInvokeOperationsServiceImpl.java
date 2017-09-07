/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.net.URI;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.CreateStreamUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Implementation of {@link RestconfInvokeOperationsService}.
 *
 */
public class RestconfInvokeOperationsServiceImpl implements RestconfInvokeOperationsService {

    private final RpcServiceHandler rpcServiceHandler;
    private final SchemaContextHandler schemaContextHandler;

    public RestconfInvokeOperationsServiceImpl(final RpcServiceHandler rpcServiceHandler,
            final SchemaContextHandler schemaContextHandler) {
        this.rpcServiceHandler = rpcServiceHandler;
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
                                           final UriInfo uriInfo) {
        final SchemaContextRef refSchemaCtx = new SchemaContextRef(this.schemaContextHandler.get());
        final SchemaPath schemaPath = payload.getInstanceIdentifierContext().getSchemaNode().getPath();
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final URI namespace = payload.getInstanceIdentifierContext().getSchemaNode().getQName().getNamespace();
        DOMRpcResult response;

        SchemaContextRef schemaContextRef;

        if (mountPoint == null) {
            if (namespace.toString().equals(RestconfStreamsConstants.SAL_REMOTE_NAMESPACE)) {
                if (identifier.contains(RestconfStreamsConstants.CREATE_DATA_SUBSCR)) {
                    response = CreateStreamUtil.createDataChangeNotifiStream(payload, refSchemaCtx);
                } else {
                    throw new RestconfDocumentedException("Not supported operation", ErrorType.RPC,
                            ErrorTag.OPERATION_NOT_SUPPORTED);
                }
            } else {
                response = RestconfInvokeOperationsUtil.invokeRpc(payload.getData(), schemaPath,
                        this.rpcServiceHandler);
            }
            schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            response = RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(mountPoint, payload.getData(), schemaPath);
            schemaContextRef = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final DOMRpcResult result = RestconfInvokeOperationsUtil.checkResponse(response);

        RpcDefinition resultNodeSchema = null;
        final NormalizedNode<?, ?> resultData = result.getResult();
        if ((result != null) && (result.getResult() != null)) {
            resultNodeSchema = (RpcDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        }
        return new NormalizedNodeContext(new InstanceIdentifierContext<RpcDefinition>(null, resultNodeSchema,
                mountPoint, schemaContextRef.get()), resultData);
    }
}
