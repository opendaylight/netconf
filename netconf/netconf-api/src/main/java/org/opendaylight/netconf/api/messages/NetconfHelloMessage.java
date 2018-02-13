/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.messages;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Set;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * NetconfMessage that can carry additional header with session metadata.
 * See {@link NetconfHelloMessageAdditionalHeader}
 */
public final class NetconfHelloMessage extends NetconfMessage {

    public static final String HELLO_TAG = "hello";

    private final NetconfHelloMessageAdditionalHeader additionalHeader;

    public NetconfHelloMessage(final Document doc, final NetconfHelloMessageAdditionalHeader additionalHeader)
            throws NetconfDocumentedException {
        super(doc);
        checkHelloMessage(doc);
        this.additionalHeader = additionalHeader;
    }

    public NetconfHelloMessage(final Document doc) throws NetconfDocumentedException {
        this(doc, null);
    }

    public Optional<NetconfHelloMessageAdditionalHeader> getAdditionalHeader() {
        return Optional.fromNullable(additionalHeader);
    }

    private static void checkHelloMessage(final Document doc) {
        if (!isHelloMessage(doc)) {
            throw new IllegalArgumentException(String.format(
                "Hello message invalid format, should contain %s tag from namespace %s, but is: %s", HELLO_TAG,
                XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, XmlUtil.toString(doc)));
        }
    }

    public static NetconfHelloMessage createClientHello(final Iterable<String> capabilities,
            final Optional<NetconfHelloMessageAdditionalHeader> additionalHeaderOptional)
                    throws NetconfDocumentedException {
        return new NetconfHelloMessage(createHelloMessageDoc(capabilities), additionalHeaderOptional.orNull());
    }

    private static Document createHelloMessageDoc(final Iterable<String> capabilities) {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element helloElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                HELLO_TAG);
        Element capabilitiesElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                XmlNetconfConstants.CAPABILITIES);

        for (String capability : Sets.newHashSet(capabilities)) {
            Element capElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                    XmlNetconfConstants.CAPABILITY);
            capElement.setTextContent(capability);
            capabilitiesElement.appendChild(capElement);
        }

        helloElement.appendChild(capabilitiesElement);

        doc.appendChild(helloElement);
        return doc;
    }

    public static NetconfHelloMessage createServerHello(final Set<String> capabilities, final long sessionId)
            throws NetconfDocumentedException {
        Document doc = createHelloMessageDoc(capabilities);
        Element sessionIdElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                XmlNetconfConstants.SESSION_ID);
        sessionIdElement.setTextContent(Long.toString(sessionId));
        doc.getDocumentElement().appendChild(sessionIdElement);
        return new NetconfHelloMessage(doc);
    }

    public static boolean isHelloMessage(final NetconfMessage msg) {
        return isHelloMessage(msg.getDocument());
    }

    private static boolean isHelloMessage(final Document document) {
        final XmlElement element = XmlElement.fromDomElement(document.getDocumentElement());
        if (!HELLO_TAG.equals(element.getName())) {
            return false;
        }

        final Optional<String> optNamespace = element.getNamespaceOptionally();
        // accept even if hello has no namespace
        return !optNamespace.isPresent()
                || XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0.equals(optNamespace.get());
    }
}
