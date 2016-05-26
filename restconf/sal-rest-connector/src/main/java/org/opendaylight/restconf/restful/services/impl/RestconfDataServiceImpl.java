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
import org.opendaylight.restconf.common.handlers.api.DOMDataBrokerHandler;
import org.opendaylight.restconf.common.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.common.references.DOMDataBrokerRef;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.transaction.TransactionNode;
import org.opendaylight.restconf.restful.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.restful.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class RestconfDataServiceImpl implements RestconfDataService {

    private static final String CONTENT = "content";
    private SchemaContextHandler schemaContextHandler;
    private DOMDataBrokerHandler domDataBrokerHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;

    @Override
    public NormalizedNodeContext readData(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final DOMDataBrokerRef domDataBrokerRef = new DOMDataBrokerRef(this.domDataBrokerHandler.getDomDataBroker());

        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier, schemaContextRef.get());
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final String value = uriInfo.getQueryParameters().getFirst(CONTENT);
        final TransactionNode transactionNode = new TransactionNode(instanceIdentifier, mountPoint,
                domDataBrokerRef.get(), this.domMountPointServiceHandler.getDOMMountPointService(),
                schemaContextRef.get());
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

        return null;
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Response deleteData(final String identifier) {
        // TODO Auto-generated method stub
        return null;
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
