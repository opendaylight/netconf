/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.TransactionServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfInvokeOperationsServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.BaseServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfSchemaService;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfService;
import org.opendaylight.restconf.nb.rfc8040.services.simple.impl.RestconfImpl;
import org.opendaylight.restconf.nb.rfc8040.services.simple.impl.RestconfOperationsServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.services.simple.impl.RestconfSchemaServiceImpl;

/**
 * Wrapper for services.
 * <ul>
 * <li>{@link BaseServicesWrapper}
 * <li>{@link TransactionServicesWrapper}
 * </ul>
 */
@Path("/")
public final class ServicesWrapper implements BaseServicesWrapper, TransactionServicesWrapper {

    private final RestconfDataService delegRestconfDataService;
    private final RestconfInvokeOperationsService delegRestconfInvokeOpsService;
    private final RestconfStreamsSubscriptionService delegRestconfSubscrService;
    private final RestconfOperationsService delegRestOpsService;
    private final RestconfSchemaService delegRestSchService;
    private final RestconfService delegRestService;

    private ServicesWrapper(RestconfDataService delegRestconfDataService,
            RestconfInvokeOperationsService delegRestconfInvokeOpsService,
            RestconfStreamsSubscriptionService delegRestconfSubscrService,
            RestconfOperationsService delegRestOpsService, RestconfSchemaService delegRestSchService,
            RestconfService delegRestService) {
        this.delegRestconfDataService = delegRestconfDataService;
        this.delegRestconfInvokeOpsService = delegRestconfInvokeOpsService;
        this.delegRestconfSubscrService = delegRestconfSubscrService;
        this.delegRestOpsService = delegRestOpsService;
        this.delegRestSchService = delegRestSchService;
        this.delegRestService = delegRestService;
    }

    public static ServicesWrapper newInstance(final SchemaContextHandler schemaCtxHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler,
            final TransactionChainHandler transactionChainHandler, final DOMDataBrokerHandler domDataBrokerHandler,
            final RpcServiceHandler rpcServiceHandler, final NotificationServiceHandler notificationServiceHandler,
            final DOMSchemaService domSchemaService) {
        RestconfOperationsService restconfOpsService =
                new RestconfOperationsServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        final DOMYangTextSourceProvider yangTextSourceProvider = domSchemaService.getExtensions()
                .getInstance(DOMYangTextSourceProvider.class);
        RestconfSchemaService restconfSchemaService =
                new RestconfSchemaServiceImpl(schemaCtxHandler, domMountPointServiceHandler,
                yangTextSourceProvider);
        RestconfStreamsSubscriptionService restconfSubscrService =
                new RestconfStreamsSubscriptionServiceImpl(domDataBrokerHandler,
                notificationServiceHandler, schemaCtxHandler, transactionChainHandler);
        RestconfDataService restconfDataService =
                new RestconfDataServiceImpl(schemaCtxHandler, transactionChainHandler, domMountPointServiceHandler,
                        restconfSubscrService);
        RestconfInvokeOperationsService restconfInvokeOpsService =
                new RestconfInvokeOperationsServiceImpl(rpcServiceHandler, schemaCtxHandler);
        RestconfService restconfService = new RestconfImpl(schemaCtxHandler);
        return new ServicesWrapper(restconfDataService, restconfInvokeOpsService,
                restconfSubscrService, restconfOpsService, restconfSchemaService, restconfService);
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
}
