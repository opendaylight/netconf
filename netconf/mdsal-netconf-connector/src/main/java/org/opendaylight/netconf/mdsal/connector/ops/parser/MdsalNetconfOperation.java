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
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MdsalNetconfOperation extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalNetconfOperation.class);
    private static final String CONFIG_KEY = "config";
    private static final String URL_KEY = "url";

    private static final String SOURCE_KEY = "source";
    private static final String TARGET_KEY = "target";

    private final NetconfFileService netconfFileService;

    protected MdsalNetconfOperation(String netconfSessionIdForReporting, NetconfFileService netconfFileService) {
        super(netconfSessionIdForReporting);
        this.netconfFileService = netconfFileService;
    }

    protected XmlElement readConfigElementFromFile(File file) throws DocumentedException {
        if (netconfFileService.canRead(file)) {
            try {
                return netconfFileService.readContent(file);
            } catch (Exception e) {
                LOG.error(e.getMessage(),e);
                throw new DocumentedException(e.getMessage()+": Cannot read the files "+file.getAbsoluteFile(), DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_failed, DocumentedException.ErrorSeverity.error);
            }
        }
        throw new DocumentedException("Not access to: "+file.getAbsoluteFile(), DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_failed, DocumentedException.ErrorSeverity.error);
    }

    protected XmlElement readConfigElementFromFile(String filePath) throws DocumentedException {
        if (StringUtils.isEmpty(filePath)) {
            throw new DocumentedException("<url> is empty "+filePath, DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_failed, DocumentedException.ErrorSeverity.error);
        }
        return readConfigElementFromFile (new File(filePath));
    }


    public MdsalNetconfParameter extractSourceParameter(final XmlElement operationElement) throws DocumentedException {
        return extractParameter(operationElement, SOURCE_KEY);
    }

    public MdsalNetconfParameter extractTargetParameter(final XmlElement operationElement) throws DocumentedException {
        return extractParameter(operationElement, TARGET_KEY);
    }

    /**
     * Extract information from XML content.
     *
     * @param operationElement
     * @return
     * @throws DocumentedException
     */
    private MdsalNetconfParameter extractParameter(final XmlElement operationElement, String sourceTargetElement) throws DocumentedException {
        final XmlElement innerElement = operationElement.getOnlyChildElementWithSameNamespace(sourceTargetElement);

        if (innerElement.getChildElements().size() > 1) {
            throw new DocumentedException("Too much elements in the "+sourceTargetElement, DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_not_supported, DocumentedException.ErrorSeverity.error);
        }

        Optional<XmlElement> configElement = innerElement.getOnlyChildElementOptionally(CONFIG_KEY);
        if (configElement.isPresent()) {
            return new MdsalNetconfParameter(configElement.get());
        }

        Optional<XmlElement> urlElement = innerElement.getOnlyChildElementOptionally(URL_KEY);
        if (urlElement.isPresent()) {
            String filePath = urlElement.get().getTextContent();
            if (StringUtils.isEmpty(filePath)) {
                throw new DocumentedException("<url> is empty "+filePath, DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_failed, DocumentedException.ErrorSeverity.error);
            }
            return new MdsalNetconfParameter(new File(filePath));
        }

        final XmlElement datasourceElement = innerElement.getOnlyChildElement();
        if (datasourceElement != null) {
            try {
                return new MdsalNetconfParameter(Datastore.valueOf(datasourceElement.getName()));
            } catch (Exception e) {
                LOG.error(e.getMessage(),e);
            }
        }

        throw new DocumentedException("Cannot recognize a content of "+sourceTargetElement, DocumentedException.ErrorType.application, DocumentedException.ErrorTag.missing_element, DocumentedException.ErrorSeverity.error);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(Document document, XmlElement operationElement) throws DocumentedException {
        return null;
    }

    public NetconfFileService getNetconfFileService() {
        return netconfFileService;
    }

    @Override
    protected String getOperationName() {
        return null;
    }
}
