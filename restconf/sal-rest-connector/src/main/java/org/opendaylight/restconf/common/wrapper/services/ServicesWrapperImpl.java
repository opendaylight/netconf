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
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.RpcServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.rest.services.api.BaseServicesWrapper;
import org.opendaylight.restconf.rest.services.api.RestconfModulesService;
import org.opendaylight.restconf.rest.services.api.RestconfOperationsService;
import org.opendaylight.restconf.rest.services.api.RestconfSchemaService;
import org.opendaylight.restconf.rest.services.api.RestconfStreamsService;
import org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceImpl;
import org.opendaylight.restconf.rest.services.impl.RestconfOperationsServiceImpl;
import org.opendaylight.restconf.rest.services.impl.RestconfSchemaServiceImpl;
import org.opendaylight.restconf.rest.services.impl.RestconfStreamsServiceImpl;
import org.opendaylight.restconf.restful.services.api.TransactionServicesWrapper;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.restful.services.impl.RestconfDataServiceImpl;
import org.opendaylight.restconf.restful.services.impl.RestconfInvokeOperationsServiceImpl;
import org.opendaylight.restconf.restful.services.impl.RestconfStreamsSubscriptionServiceImpl;

/**
 * Wrapper for services:
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
    private RestconfModulesService delegRestModService;
    private RestconfOperationsService delegRestOpsService;
    private RestconfStreamsService delegRestStrsService;
    private RestconfSchemaService delegRestSchService;

    private ServicesWrapperImpl() {
    }

    private static class InstanceHolder {
        public static final ServicesWrapperImpl INSTANCE = new ServicesWrapperImpl();
    }

    public static ServicesWrapperImpl getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        return this.delegRestModService.getModules(uriInfo);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModService.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModService.getModule(identifier, uriInfo);
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
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        return this.delegRestStrsService.getAvailableStreams(uriInfo);
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModuleId) {
        return this.delegRestSchService.getSchema(mountAndModuleId);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        return this.delegRestconfDataService.readData(identifier, uriInfo);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload) {
        return this.delegRestconfDataService.putData(identifier, payload);
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
    public PATCHStatusContext patchData(final String identifier, final PATCHContext context, final UriInfo uriInfo) {
        return this.delegRestconfDataService.patchData(identifier, context, uriInfo);
    }

    @Override
    public PATCHStatusContext patchData(final PATCHContext context, final UriInfo uriInfo) {
        return this.delegRestconfDataService.patchData(context, uriInfo);
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
                                           final UriInfo uriInfo) {
        return this.delegRestconfInvokeOpsService.invokeRpc(identifier, payload, uriInfo);
    }

    @Override
    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
        return this.delegRestconfSubscrService.subscribeToStream(identifier, uriInfo);
    }

    public void setHandlers(final SchemaContextHandler schemaCtxHandler,
                            final DOMMountPointServiceHandler domMountPointServiceHandler,
                            final TransactionChainHandler transactionChainHandler,
                            final DOMDataBrokerHandler domDataBrokerHandler,
                            final RpcServiceHandler rpcServiceHandler) {
        this.delegRestModService = new RestconfModulesServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestOpsService = new RestconfOperationsServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestSchService = new RestconfSchemaServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestStrsService = new RestconfStreamsServiceImpl(schemaCtxHandler);
        this.delegRestconfDataService = new RestconfDataServiceImpl(schemaCtxHandler, transactionChainHandler,
                domMountPointServiceHandler);
        this.delegRestconfInvokeOpsService = new RestconfInvokeOperationsServiceImpl(rpcServiceHandler,
                schemaCtxHandler);
        this.delegRestconfSubscrService = new RestconfStreamsSubscriptionServiceImpl(domDataBrokerHandler);
    }
}
