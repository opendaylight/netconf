/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Get extends AbstractGet {
    private static final Logger LOG = LoggerFactory.getLogger(Get.class);
    private static final String OPERATION_NAME = "get";

    private final TransactionProvider transactionProvider;

    public Get(final SessionIdType sessionId, final CurrentSchemaContext schemaContext,
            final TransactionProvider transactionProvider) {
        super(sessionId, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {

        final Optional<YangInstanceIdentifier> dataRootOptional = getDataRootFromFilter(operationElement);
        if (dataRootOptional.isEmpty()) {
            return document.createElement(XmlNetconfConstants.DATA_KEY);
        }

        final YangInstanceIdentifier dataRoot = dataRootOptional.orElseThrow();

        final DOMDataTreeReadWriteTransaction rwTx = transactionProvider.createRunningTransaction();
        try {
            final Optional<NormalizedNode> normalizedNodeOptional = rwTx.read(
                    LogicalDatastoreType.OPERATIONAL, dataRoot).get();
            transactionProvider.abortRunningTransaction(rwTx);

            if (normalizedNodeOptional.isEmpty()) {
                return document.createElement(XmlNetconfConstants.DATA_KEY);
            }

            return serializeNodeWithParentStructure(document, dataRoot, normalizedNodeOptional.orElseThrow());
        } catch (final InterruptedException | ExecutionException e) {
            LOG.warn("Unable to read data: {}", dataRoot, e);
            throw new IllegalStateException("Unable to read data " + dataRoot, e);
        }
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
