/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameterType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DeleteConfig extends YangValidationNetconfOperation {
    private static final String OPERATION_NAME = "delete-config";

    private final TransactionProvider transactionProvider;

    public DeleteConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter targetParameter = extractTargetParameter(operationElement);

        if (MdsalNetconfParameterType.DATASTORE == targetParameter.getType()) {
            if (Datastore.candidate == targetParameter.getDatastore()) {
                return (new DiscardChanges(getNetconfSessionIdForReporting(),transactionProvider)).handleWithNoSubsequentOperations(document,null);
            } else {
                throw new DocumentedException("delete-config on running datastore is not supported",
                        ErrorType.protocol,
                        ErrorTag.operation_not_supported,
                        ErrorSeverity.error);
            }
        }
        throw new DocumentedException("unsupported input parameters",
                ErrorType.protocol,
                ErrorTag.operation_not_supported,
                ErrorSeverity.error);
    }



    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}