/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

abstract class AbstractConfigOperation extends AbstractSingletonNetconfOperation {
    private static final String URL_KEY = "url";
    private static final String CONFIG_KEY = "config";

    AbstractConfigOperation(final String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting);
    }

    static NodeList getElementsByTagName(final XmlElement parent, final String key) throws
        DocumentedException {
        final Element domParent = parent.getDomElement();
        final NodeList elementsByTagName;

        if (Strings.isNullOrEmpty(domParent.getPrefix())) {
            elementsByTagName = domParent.getElementsByTagName(key);
        } else {
            elementsByTagName = domParent.getElementsByTagNameNS(parent.getNamespace(), key);
        }

        return elementsByTagName;
    }

    static XmlElement getElement(final XmlElement parent, final String elementName)
        throws DocumentedException {
        final Optional<XmlElement> childNode = parent.getOnlyChildElementOptionally(elementName);
        if (!childNode.isPresent()) {
            throw new DocumentedException(elementName + " element is missing",
                DocumentedException.ErrorType.PROTOCOL,
                DocumentedException.ErrorTag.MISSING_ELEMENT,
                DocumentedException.ErrorSeverity.ERROR);
        }

        return childNode.get();
    }

    static XmlElement getConfigElement(final XmlElement parent) throws DocumentedException {
        final Optional<XmlElement> configElement = parent.getOnlyChildElementOptionally(CONFIG_KEY);
        if (configElement.isPresent()) {
            return configElement.get();
        } else {
            final Optional<XmlElement> urlElement = parent.getOnlyChildElementOptionally(URL_KEY);
            if (!urlElement.isPresent()) {
                throw new DocumentedException("Invalid RPC, neither <config> not <url> element is present",
                    DocumentedException.ErrorType.PROTOCOL,
                    DocumentedException.ErrorTag.MISSING_ELEMENT,
                    DocumentedException.ErrorSeverity.ERROR);
            }

            final Document document = getDocument(urlElement.get());
            return XmlElement.fromDomElementWithExpected(document.getDocumentElement(), CONFIG_KEY,
                XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        }
    }

    private static Document getDocument(final XmlElement url) throws DocumentedException {
        final String urlString = url.getTextContent();
        if (urlString.startsWith("file:")) {
            return getDocumentFromFile(urlString);
        } else {
            return getDocumentFromUrl(urlString);
        }
    }

    private static Document getDocumentFromFile(final String urlString) throws DocumentedException {
        try (FileInputStream input = new FileInputStream(new File(new URI(urlString)))) {
            return XmlUtil.readXmlToDocument(input);
        } catch (URISyntaxException e) {
            throw new DocumentedException(urlString + " is not valid URI", e,
                DocumentedException.ErrorType.APPLICATION,
                DocumentedException.ErrorTag.INVALID_VALUE,
                DocumentedException.ErrorSeverity.ERROR);
        } catch (IOException | SAXException e) {
            throw new DocumentedException("Could not open URI:" + urlString, e,
                DocumentedException.ErrorType.APPLICATION,
                DocumentedException.ErrorTag.INVALID_VALUE,
                DocumentedException.ErrorSeverity.ERROR);
        }
    }

    private static Document getDocumentFromUrl(final String urlString) throws DocumentedException {
        try (InputStream input = new URL(urlString).openStream()) {
            return XmlUtil.readXmlToDocument(input);
        } catch (MalformedURLException e) {
            throw new DocumentedException(urlString + " is not valid URL", e,
                DocumentedException.ErrorType.APPLICATION,
                DocumentedException.ErrorTag.INVALID_VALUE,
                DocumentedException.ErrorSeverity.ERROR);
        } catch (IOException | SAXException e) {
            throw new DocumentedException("Could not open URL:" + urlString, e,
                DocumentedException.ErrorType.APPLICATION,
                DocumentedException.ErrorTag.INVALID_VALUE,
                DocumentedException.ErrorSeverity.ERROR);
        }
    }
}
