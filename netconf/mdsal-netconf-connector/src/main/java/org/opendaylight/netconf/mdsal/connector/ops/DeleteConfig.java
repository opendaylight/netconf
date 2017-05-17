/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import java.io.File;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfOperation;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameterType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DeleteConfig extends MdsalNetconfOperation {
    private static final String OPERATION_NAME = "delete-config";

    private final TransactionProvider transactionProvider;

    public DeleteConfig(final String netconfSessionIdForReporting, final TransactionProvider transactionProvider, final NetconfFileService netconfFileService) {
        super(netconfSessionIdForReporting, netconfFileService);
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
        } else if (MdsalNetconfParameterType.FILE == targetParameter.getType()) {
            return deleteFile(targetParameter.getFile(), document);

        }
        throw new DocumentedException("unsupported input parameters",
                ErrorType.protocol,
                ErrorTag.operation_not_supported,
                ErrorSeverity.error);
    }

    private Element deleteFile(File file, Document document) throws DocumentedException {
        if (getNetconfFileService().canWrite(file)) {
            boolean result = getNetconfFileService().deleteFile(file);
            if (result) {
                return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
            }
            throw new DocumentedException("Cannot delete files: "+file.getAbsoluteFile(), ErrorType.application, ErrorTag.operation_failed, ErrorSeverity.error);
        }
        throw new DocumentedException("Not access to: "+file.getAbsoluteFile(), ErrorType.application, ErrorTag.operation_failed, ErrorSeverity.error);
    }


    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}