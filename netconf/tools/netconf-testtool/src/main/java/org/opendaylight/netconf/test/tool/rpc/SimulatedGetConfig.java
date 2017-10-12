/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.rpc;

import com.google.common.base.Optional;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.confignetconfconnector.operations.AbstractConfigNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class SimulatedGetConfig extends AbstractConfigNetconfOperation {

    private final DataList storage;
    private static final Logger LOG = LoggerFactory
            .getLogger(SimulatedGetConfig.class);

    public SimulatedGetConfig(final String netconfSessionIdForReporting, final DataList storage,
                              final Optional<File> initialConfigXMLFile) {
        super(null, netconfSessionIdForReporting);

        if (initialConfigXMLFile.isPresent()) {
            LOG.info("File is present: {}", initialConfigXMLFile.get()
                    .getName());
            this.storage = loadInitialConfigXMLFile(initialConfigXMLFile.get());
        } else {
            this.storage = storage;
        }
    }

    private static DataList loadInitialConfigXMLFile(final File file) {
        LOG.info("Loading initial config xml file: {}", file.getName());
        DataList configData = new DataList();
        List<XmlElement> xmlElementList = Collections.emptyList();
        try {
            Element element = XmlUtil.readXmlToElement(file);
            XmlElement xmlElement = XmlElement.fromDomElement(element);
            xmlElementList = xmlElement.getChildElements();
            configData.setConfigList(xmlElementList);
        } catch (IOException e) {
            LOG.info("IO exception loading xml file: {} ", e.getMessage());

        } catch (SAXException e) {
            LOG.info("SAXException {}", e.getMessage());
        }
        return configData;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Element element = XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.absent());

        for (final XmlElement e : storage.getConfigList()) {
            final Element domElement = e.getDomElement();
            element.appendChild(element.getOwnerDocument().importNode(domElement, true));
        }

        return element;
    }

    @Override
    protected String getOperationName() {
        return XmlNetconfConstants.GET_CONFIG;
    }
}
