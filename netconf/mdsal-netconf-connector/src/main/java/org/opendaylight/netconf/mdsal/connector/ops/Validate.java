/*
 * Copyright (c) 2015 Frinx s.r.o. and others.  All rights reserved.
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
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Validate extends YangValidationNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(Validate.class);

    private static final String OPERATION_NAME = "validate";
    private static final String CONFIG_KEY = "config";
    private static final String SOURCE_KEY = "source";

    public Validate(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting, schemaContext);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final ValidateInputData inputData = extractSourceParameters(operationElement);
        if (inputData.getDatastore() != null) {
            validateSourceDatastore(inputData.getDatastore());
        } else {
            validateConfigElement(inputData.getConfigElement());
        }

        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
    }

    /**
     * Extract information from XML content.
     * @param operationElement
     * @return
     * @throws DocumentedException
     */
    private ValidateInputData extractSourceParameters(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = operationElement.getDomElement().getElementsByTagName(SOURCE_KEY);
        if (elementsByTagName.getLength() == 1) {
            final XmlElement sourceElement = XmlElement.fromDomElement((Element) elementsByTagName.item(0));

            if (sourceElement.getChildElements().size() > 1) {
                throw new DocumentedException("Too much elements in the source", ErrorType.rpc, ErrorTag.operation_not_supported, ErrorSeverity.error);
            }

            Optional<XmlElement> configElement = sourceElement.getOnlyChildElementOptionally(CONFIG_KEY);
            if (configElement.isPresent()) {
                return new ValidateInputData(null, configElement.get());
            }

            final XmlElement datasourceElement = sourceElement.getOnlyChildElement();
            if (datasourceElement != null) {
                return new ValidateInputData(Datastore.valueOf(datasourceElement.getName()), null);
            }

            throw new DocumentedException("Datasource or config element is missing", ErrorType.rpc, ErrorTag.missing_element, ErrorSeverity.error);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple source elements", ErrorType.rpc, ErrorTag.operation_not_supported, ErrorSeverity.error);
        } else {
            throw new DocumentedException("Source is missing", ErrorType.rpc, ErrorTag.missing_element, ErrorSeverity.error);
        }

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
            throw new DocumentedException("validate on running datastore is not supported",
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

class ValidateInputData {
    private Datastore datastore = null;
    private XmlElement configElement = null;

    public ValidateInputData(Datastore datastore, XmlElement configElement) {
        this.datastore = datastore;
        this.configElement = configElement;
    }

    public Datastore getDatastore() {
        return datastore;
    }

    public void setDatastore(Datastore datastore) {
        this.datastore = datastore;
    }

    public XmlElement getConfigElement() {
        return configElement;
    }

    public void setConfigElement(XmlElement configElement) {
        this.configElement = configElement;
    }
}