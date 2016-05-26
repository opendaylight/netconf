/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import com.google.common.base.Preconditions;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.restful.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

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
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());

        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier, schemaContextRef.get());
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final String value = uriInfo.getQueryParameters().getFirst(RestconfDataServiceConstant.CONTENT);

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                this.transactionChainHandler.get(), this.domMountPointServiceHandler.get(), schemaContextRef.get());
        final NormalizedNode<?, ?> node = ReadDataTransactionUtil.readData(value, transactionNode);

        return new NormalizedNodeContext(instanceIdentifier, node);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload) {
        throw new UnsupportedOperationException("Not yet implemented.");
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
}
