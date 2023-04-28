/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpc;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.operations.AbstractLastNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class SimulatedGetConfig extends AbstractLastNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(SimulatedGetConfig.class);

    private final DataList storage;

    public SimulatedGetConfig(final SessionIdType sessionId, final DataList storage,
                              final Optional<File> initialConfigXMLFile) {
        super(sessionId);

        if (initialConfigXMLFile.isPresent()) {
            final var file = initialConfigXMLFile.orElseThrow();
            LOG.info("File is present: {}", file.getName());
            this.storage = loadInitialConfigXMLFile(file);
        } else {
            this.storage = storage;
        }
    }

    private static DataList loadInitialConfigXMLFile(final File file) {
        LOG.info("Loading initial config xml file: {}", file.getName());
        DataList configData = new DataList();
        try {
            Element element = XmlUtil.readXmlToElement(file);
            XmlElement xmlElement = XmlElement.fromDomElement(element);
            List<XmlElement> xmlElementList = xmlElement.getChildElements();
            configData.setConfigList(xmlElementList);
        } catch (IOException e) {
            LOG.info("IO exception loading xml file: {} ", e.getMessage());

        } catch (SAXException e) {
            LOG.info("SAXException {}", e.getMessage());
        }
        return configData;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) {
        final Element element = document.createElement(XmlNetconfConstants.DATA_KEY);

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
