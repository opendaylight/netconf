/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.wrapper.services;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchStatusContext;
import org.opendaylight.restconf.base.services.api.BaseServicesWrapper;
import org.opendaylight.restconf.base.services.api.RestconfOperationsService;
import org.opendaylight.restconf.base.services.api.RestconfSchemaService;
import org.opendaylight.restconf.base.services.api.RestconfService;
import org.opendaylight.restconf.base.services.impl.RestconfImpl;
import org.opendaylight.restconf.base.services.impl.RestconfOperationsServiceImpl;
import org.opendaylight.restconf.base.services.impl.RestconfSchemaServiceImpl;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.handlers.RpcServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.restful.services.api.TransactionServicesWrapper;
import org.opendaylight.restconf.restful.services.impl.RestconfDataServiceImpl;
import org.opendaylight.restconf.restful.services.impl.RestconfInvokeOperationsServiceImpl;
import org.opendaylight.restconf.restful.services.impl.RestconfStreamsSubscriptionServiceImpl;

/**
 * Wrapper for services.
 * <ul>
 * <li>{@link BaseServicesWrapper}
 * <li>{@link TransactionServicesWrapper}
 * </ul>
 *
 */
@Path("/")
public class ServicesWrapperImpl implements BaseServicesWrapper, TransactionServicesWrapper {

    private RestconfDataService delegRestconfDataService;
    private RestconfInvokeOperationsService delegRestconfInvokeOpsService;
    private RestconfStreamsSubscriptionService delegRestconfSubscrService;
    private RestconfOperationsService delegRestOpsService;
    private RestconfSchemaService delegRestSchService;
    private RestconfService delegRestService;

    private ServicesWrapperImpl() {
    }

    private static class InstanceHolder {
        public static final ServicesWrapperImpl INSTANCE = new ServicesWrapperImpl();
    }

    public static ServicesWrapperImpl getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return this.delegRestOpsService.getOperations(uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        return this.delegRestOpsService.getOperations(identifier, uriInfo);
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModuleId) {
        return this.delegRestSchService.getSchema(mountAndModuleId);
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return this.delegRestconfDataService.readData(uriInfo);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        return this.delegRestconfDataService.readData(identifier, uriInfo);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return this.delegRestconfDataService.putData(identifier, payload, uriInfo);
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return this.delegRestconfDataService.postData(identifier, payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return this.delegRestconfDataService.postData(payload, uriInfo);
    }

    @Override
    public Response deleteData(final String identifier) {
        return this.delegRestconfDataService.deleteData(identifier);
    }

    @Override
    public PatchStatusContext patchData(final String identifier, final PatchContext context, final UriInfo uriInfo) {
        return this.delegRestconfDataService.patchData(identifier, context, uriInfo);
    }

    @Override
    public PatchStatusContext patchData(final PatchContext context, final UriInfo uriInfo) {
        return this.delegRestconfDataService.patchData(context, uriInfo);
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        return this.delegRestconfInvokeOpsService.invokeRpc(identifier, payload, uriInfo);
    }

    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        return this.delegRestconfSubscrService.subscribeToStream(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getLibraryVersion() {
        return this.delegRestService.getLibraryVersion();
    }

    public void setHandlers(final SchemaContextHandler schemaCtxHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler,
            final TransactionChainHandler transactionChainHandler, final DOMDataBrokerHandler domDataBrokerHandler,
            final RpcServiceHandler rpcServiceHandler, final NotificationServiceHandler notificationServiceHandler) {
        this.delegRestOpsService = new RestconfOperationsServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestSchService = new RestconfSchemaServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestconfSubscrService = new RestconfStreamsSubscriptionServiceImpl(domDataBrokerHandler,
                notificationServiceHandler, schemaCtxHandler, transactionChainHandler);
        this.delegRestconfDataService =
                new RestconfDataServiceImpl(schemaCtxHandler, transactionChainHandler, domMountPointServiceHandler,
                        this.delegRestconfSubscrService);
        this.delegRestconfInvokeOpsService =
                new RestconfInvokeOperationsServiceImpl(rpcServiceHandler, schemaCtxHandler);
        this.delegRestService = new RestconfImpl(schemaCtxHandler);
    }
}
