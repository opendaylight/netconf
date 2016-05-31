/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import java.net.URI;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.common.handlers.api.TransactionChainHandler;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.transaction.TransactionNode;
import org.opendaylight.restconf.restful.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModifiedNodeDoesNotExistException;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Implementation of {@link RestconfDataService}
 */
public class RestconfDataServiceImpl implements RestconfDataService {

    private SchemaContextHandler schemaContextHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;
    private TransactionChainHandler transactionChainHandler;

    @Override
    public NormalizedNodeContext readData(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());

        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier, schemaContextRef.get());
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final String value = uriInfo.getQueryParameters().getFirst(RestconfDataServiceConstant.CONTENT);

        final TransactionNode transactionNode = new TransactionNode(instanceIdentifier, mountPoint,
                this.transactionChainHandler.getTransactionChain(),
                this.domMountPointServiceHandler.getDOMMountPointService(),
                schemaContextRef.get());
        final NormalizedNode<?, ?> node = ReadDataTransactionUtil.readData(value, transactionNode);

        return new NormalizedNodeContext(instanceIdentifier, node);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload) {
        Preconditions.checkNotNull(identifier);
        Preconditions.checkNotNull(payload);

        final SchemaContextRef ref = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();
        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);
        try {
            PutDataTransactionUtil.putData(this.transactionChainHandler.getTransactionChain(), payload, ref)
                    .checkedGet();
        } catch (final TransactionCommitFailedException e) {
            throw new RestconfDocumentedException(e.getMessage(), e, e.getErrorList());
        }
        return Response.status(Status.OK).build();
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        Preconditions.checkNotNull(payload);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        try {
            PostDataTransactionUtil.postData(payload, uriInfo, this.transactionChainHandler, schemaContextRef)
                    .checkedGet();
        } catch (final TransactionCommitFailedException e) {
            final String errMsg = "Error creating data ";
            throw new RestconfDocumentedException(errMsg, e);
        } catch (final RestconfDocumentedException e) {
            throw e;
        }
        final URI location = PostDataTransactionUtil.resolveLocation(uriInfo,
                payload.getInstanceIdentifierContext().getMountPoint(),
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), schemaContextRef);

        final ResponseBuilder responseBuilder = Response.status(Status.NO_CONTENT);
        responseBuilder.location(location);
        return responseBuilder.build();
    }

    @Override
    public Response deleteData(final String identifier) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextRef.get());
        try {
            DeleteDataTransactionUtil.deleteData(instanceIdentifier, this.transactionChainHandler).checkedGet();
        } catch (final Exception e) {
            final Optional<Throwable> searchedException = Iterables.tryFind(Throwables.getCausalChain(e),
                    Predicates.instanceOf(ModifiedNodeDoesNotExistException.class));
            if (searchedException.isPresent()) {
                throw new RestconfDocumentedException("Data specified for deleting doesn't exist.",
                        ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
            }
            final String errMsg = "Error while deleting data";
            throw new RestconfDocumentedException(errMsg, e);
        }
        return Response.status(Status.OK).build();
    }

    @Override
    public PATCHStatusContext patchData(final String identifier, final PATCHContext context, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PATCHStatusContext patchData(final PATCHContext context, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }
}
