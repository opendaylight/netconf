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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
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

    private SchemaContextHandler schemaContextHandler;
    private TransactionChainHandler transactionChainHandler;

    @Override
    public NormalizedNodeContext readData(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());

        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier, schemaContextRef.get());
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final String value = uriInfo.getQueryParameters().getFirst(RestconfDataServiceConstant.CONTENT);

        DOMDataReadWriteTransaction transaction = null;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get().newReadWriteTransaction();
        } else {
            transaction = transactionOfMountPoint(mountPoint);
        }
        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                transaction);
        final NormalizedNode<?, ?> node = ReadDataTransactionUtil.readData(value, transactionNode);

        return new NormalizedNodeContext(instanceIdentifier, node);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload) {
        Preconditions.checkNotNull(identifier);
        Preconditions.checkNotNull(payload);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        DOMDataReadWriteTransaction transaction = null;
        SchemaContextRef ref = null;
        if (mountPoint == null) {
            transaction = this.transactionChainHandler.get().newReadWriteTransaction();
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
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Response deleteData(final String identifier) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public PATCHStatusContext patchData(final String identifier, final PATCHContext context, final UriInfo uriInfo) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public PATCHStatusContext patchData(final PATCHContext context, final UriInfo uriInfo) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Prepare transaction to read data of mount point, if these data are
     * present.
     * @param mountPoint
     *
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static DOMDataReadWriteTransaction transactionOfMountPoint(final DOMMountPoint mountPoint) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return domDataBrokerService.get().newReadWriteTransaction();
        } else {
            final String errMsg = "DOM data broker service isn't available for mount point "
                    + mountPoint.getIdentifier();
            LOG.warn(errMsg);
            throw new RestconfDocumentedException(errMsg);
        }
    }
}
