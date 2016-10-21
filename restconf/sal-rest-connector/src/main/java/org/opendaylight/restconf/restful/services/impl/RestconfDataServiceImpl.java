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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.restful.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfDataService}
 */
public class RestconfDataServiceImpl implements RestconfDataService {

    private final static Logger LOG = LoggerFactory.getLogger(RestconfDataServiceImpl.class);

    private final SchemaContextHandler schemaContextHandler;
    private final TransactionChainHandler transactionChainHandler;

    public RestconfDataServiceImpl(final SchemaContextHandler schemaContextHandler,
                                   final TransactionChainHandler transactionChainHandler) {
        this.schemaContextHandler = schemaContextHandler;
        this.transactionChainHandler = transactionChainHandler;
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());

        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextRef.get());
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final String value = uriInfo.getQueryParameters().getFirst(RestconfDataServiceConstant.CONTENT);

        final DOMTransactionChain transaction;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get();
        } else {
            transaction = transactionOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                transaction);
        final NormalizedNode<?, ?> node = ReadDataTransactionUtil.readData(value, transactionNode);
        final SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        final String etag = '"' + node.getNodeType().getModule().getFormattedRevision()
                + node.getNodeType().getLocalName() + '"';
        final Response resp;

        if ((value == null) || value.contains(RestconfDataServiceConstant.ReadData.CONFIG)) {
            resp = Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node)).header("ETag", etag)
                    .header("Last-Modified", dateFormatGmt.format(new Date())).build();
        } else {
            resp = Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node)).build();
        }
        return resp;
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload) {
        Preconditions.checkNotNull(payload);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final DOMTransactionChain transaction;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transaction = transactionOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, transaction);
        return PutDataTransactionUtil.putData(payload, ref, transactionNode);
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        Preconditions.checkNotNull(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final DOMTransactionChain transaction;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transaction = transactionOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }
        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, transaction);
        return PostDataTransactionUtil.postData(uriInfo, payload, transactionNode, ref);
    }

    @Override
    public Response deleteData(final String identifier) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextRef.get());

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final DOMTransactionChain transaction;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get();
        } else {
            transaction = transactionOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                transaction);
        return DeleteDataTransactionUtil.deleteData(transactionNode);
    }

    @Override
    public PATCHStatusContext patchData(final String identifier, final PATCHContext context, final UriInfo uriInfo) {
        return patchData(context, uriInfo);
    }

    @Override
    public PATCHStatusContext patchData(final PATCHContext context, final UriInfo uriInfo) {
        Preconditions.checkNotNull(context);
        final DOMMountPoint mountPoint = context.getInstanceIdentifierContext().getMountPoint();

        final DOMTransactionChain transaction;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transaction = transactionOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                context.getInstanceIdentifierContext(), mountPoint, transaction);

        return PatchDataTransactionUtil.patchData(context, transactionNode, ref);
    }

    /**
     * Prepare transaction to read data of mount point, if these data are
     * present.
     * @param mountPoint
     * @return {@link DOMDataReadWriteTransaction}
     */
    private static DOMTransactionChain transactionOfMountPoint(final DOMMountPoint mountPoint) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return domDataBrokerService.get().createTransactionChain(RestConnectorProvider.transactionListener);
        } else {
            final String errMsg = "DOM data broker service isn't available for mount point "
                    + mountPoint.getIdentifier();
            LOG.warn(errMsg);
            throw new RestconfDocumentedException(errMsg);
        }
    }
}
