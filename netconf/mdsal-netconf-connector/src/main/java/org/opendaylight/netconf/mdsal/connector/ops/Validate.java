/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Validate extends ValidateNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(Validate.class);

    private static final String OPERATION_NAME = "validate";

    public Validate(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext, final NetconfFileService netconfFileService) {
        super(netconfSessionIdForReporting, schemaContext, netconfFileService);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter inputParameter = extractSourceParameter(operationElement);

        switch (inputParameter.getType()) {
            case DATASTORE:
                validateSourceDatastore(inputParameter.getDatastore());
                break;
            case CONFIG:
                validateConfigElement(inputParameter.getConfigElement());
                break;
            case FILE:
                XmlElement configElement = readConfigElementFromFile(inputParameter.getFile());
                validateConfigElement(configElement);
                break;
        }
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
    }


    /**
     * Validate XML element <config>. It validates syntax error and particularly also semantic errors
     *
     * @param configElement
     * @throws DocumentedException
     */
    private void validateConfigElement(XmlElement configElement) throws DocumentedException {
        final ModifyAction defaultAction = ModifyAction.CREATE;

        for (final XmlElement element : configElement.getChildElements()) {
            try {
                createDataTreeChecker(defaultAction, element);
            } catch (Exception e) {
                LOG.error(e.getMessage(),e);
                throw new DocumentedException(e.getMessage(),
                        ErrorType.application,
                        ErrorTag.operation_failed,
                        ErrorSeverity.error);
            }

        }
    }

    /**
     * Here is not required implementation. Because the data has been already validated in get-config/get operation.
     * We just check whether validation applying in Candidate datastore
     * @param datastore
     * @throws DocumentedException
     */
    private void validateSourceDatastore(Datastore datastore) throws DocumentedException {
        if (datastore == Datastore.running) {
            throw new DocumentedException("validate on running getDatastore is not supported",
                    ErrorType.protocol,
                    ErrorTag.operation_not_supported,
                    ErrorSeverity.error);
        }
    }


    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}