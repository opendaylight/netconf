/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import com.google.common.util.concurrent.CheckedFuture;
import java.net.URI;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.handlers.api.RpcServiceHandler;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.restful.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.restconf.restful.utils.streams.CreateStreamUtil;
import org.opendaylight.restconf.restful.utils.streams.RestconfStreamsConstants;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Implementation of {@link RestconfInvokeOperationsService}
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
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        final SchemaContextRef refSchemaCtx = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final SchemaPath schemaPath = payload.getInstanceIdentifierContext().getSchemaNode().getPath();
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final URI namespace = payload.getInstanceIdentifierContext().getSchemaNode().getQName().getNamespace();

        CheckedFuture<DOMRpcResult, DOMRpcException> response;
        SchemaContextRef schemaContextRef;

        if (mountPoint == null) {
            response = RestconfInvokeOperationsUtil.invokeRpc(payload.getData(), schemaPath, this.rpcServiceHandler);
            schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        } else {
            if (namespace.toString().equals(RestconfStreamsConstants.SAL_REMOTE_NAMESPACE)) {
                response = CreateStreamUtil.createStream(payload, refSchemaCtx);
            } else {
                response = RestconfInvokeOperationsUtil.invokeRpcViaMountPoint(mountPoint, payload.getData(), schemaPath);
            }
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
