/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.testtool.core.impl.rpc;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.confignetconfconnector.operations.AbstractConfigNetconfOperation;
import org.opendaylight.netconf.test.tool.rpc.DataList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class SimulatedGetConfig extends AbstractConfigNetconfOperation {

    private final DataList storage;
    private static final Logger LOG = LoggerFactory
            .getLogger(SimulatedGetConfig.class);

    public SimulatedGetConfig(final String netconfSessionIdForReporting, final DataList storage) {
        super(null, netconfSessionIdForReporting);
        this.storage = storage;
    }

    public SimulatedGetConfig(final String netconfSessionIdForReporting, final DataList storage,
                              final InputStream initialConfigXMLFile) {
        super(null, netconfSessionIdForReporting);
        this.storage = loadInitialConfigXMLFile(initialConfigXMLFile);
    }

    private DataList loadInitialConfigXMLFile(final InputStream inputStream) {
        LOG.info("Loading initial config xml file");
        DataList configData = new DataList();
        List<XmlElement> xmlElementList = Collections.emptyList();
        try {
            Element element = XmlUtil.readXmlToElement(inputStream);
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
