/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DiscardChanges extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(DiscardChanges.class);
    private static final String OPERATION_NAME = "discard-changes";

    private final TransactionProvider transactionProvider;

    public DiscardChanges(final SessionIdType sessionId, final TransactionProvider transactionProvider) {
        super(sessionId);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {

        try {
            transactionProvider.abortTransaction();
        } catch (final IllegalStateException e) {
            LOG.warn("Abort failed ", e);
            final Map<String, String> errorInfo = new HashMap<>();
            errorInfo.put(ErrorTag.OPERATION_FAILED.elementBody(),
                "Operation failed. Use 'get-config' or 'edit-config' before triggering " + OPERATION_NAME
                + " operation");
            throw new DocumentedException(e.getMessage(), e, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                    ErrorSeverity.ERROR, errorInfo);
        }
        return document.createElement(XmlNetconfConstants.OK);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
