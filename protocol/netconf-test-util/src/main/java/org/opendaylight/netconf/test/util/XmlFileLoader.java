/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.util;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class XmlFileLoader {
    private XmlFileLoader() {
        // Hidden on purpose
    }

    public static NetconfMessage xmlFileToNetconfMessage(final String fileName) throws IOException, SAXException,
            ParserConfigurationException {
        return new NetconfMessage(xmlFileToDocument(fileName));
    }

    public static Element xmlFileToElement(final String fileName) throws IOException, SAXException,
            ParserConfigurationException {
        return xmlFileToDocument(fileName).getDocumentElement();
    }

    public static String xmlFileToString(final String fileName) throws IOException, SAXException,
            ParserConfigurationException {
        return XmlUtil.toString(xmlFileToDocument(fileName));
    }

    public static Document xmlFileToDocument(final String fileName) throws IOException, SAXException,
            ParserConfigurationException {
        try (InputStream resource = getResourceAsStream(fileName)) {
            return XmlUtil.readXmlToDocument(resource);
        }
    }

    public static String fileToString(final String fileName) throws IOException {
        try (InputStream resource = getResourceAsStream(fileName)) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static @NonNull InputStream getResourceAsStream(final String fileName) {
        final String resourceName = requireNonNull(fileName);
        return requireNonNull(XmlFileLoader.class.getClassLoader().getResourceAsStream(resourceName), resourceName);
    }
}
