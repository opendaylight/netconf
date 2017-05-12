/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.parser;

import com.google.common.base.Optional;
import java.io.File;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class MdsalNetconfOperation extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalNetconfOperation.class);
    private static final String CONFIG_KEY = "config";
    private static final String URL_KEY = "url";

    private static final String SOURCE_KEY = "source";
    private static final String TARGET_KEY = "target";

    protected MdsalNetconfOperation(String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting);
    }


    public MdsalNetconfParameter extractSourceParameter(final XmlElement operationElement) throws DocumentedException {
        return extractParameter(operationElement, SOURCE_KEY);
    }

    public MdsalNetconfParameter extractTargetParameter(final XmlElement operationElement) throws DocumentedException {
        return extractParameter(operationElement, TARGET_KEY);
    }

    private MdsalNetconfParameter extractParameter(final XmlElement operationElement, String sourceTargetElement) throws DocumentedException {
        final XmlElement innerElement = operationElement.getOnlyChildElementWithSameNamespace(sourceTargetElement);

        if (innerElement.getChildElements().size() > 1) {
            throw new DocumentedException("Too many elements in the " + sourceTargetElement,
                    DocumentedException.ErrorType.application,
                    DocumentedException.ErrorTag.operation_not_supported,
                    DocumentedException.ErrorSeverity.error);
        }

        Optional<XmlElement> configElement = innerElement.getOnlyChildElementOptionally(CONFIG_KEY);
        if (configElement.isPresent()) {
            return new MdsalNetconfParameter(configElement.get());
        }

        Optional<XmlElement> urlElement = innerElement.getOnlyChildElementOptionally(URL_KEY);
        if (urlElement.isPresent()) {
            return new MdsalNetconfParameter(new File(urlElement.get().getTextContent()));
        }

        final XmlElement datasourceElement = innerElement.getOnlyChildElement();
        if (datasourceElement != null) {
            try {
                return new MdsalNetconfParameter(Datastore.valueOf(datasourceElement.getName()));
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }

        throw new DocumentedException("Cannot recognize a content of " + sourceTargetElement,
                DocumentedException.ErrorType.application,
                DocumentedException.ErrorTag.missing_element,
                DocumentedException.ErrorSeverity.error);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(Document document, XmlElement operationElement) throws DocumentedException {
        return null;
    }

    @Override
    protected String getOperationName() {
        return null;
    }
}
