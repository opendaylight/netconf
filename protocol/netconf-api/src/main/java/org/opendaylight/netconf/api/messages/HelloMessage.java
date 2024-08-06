/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * NetconfMessage that can carry additional header with session metadata.
 *
 * @see NetconfHelloMessageAdditionalHeader
 */
public final class HelloMessage extends NetconfMessage {
    public static final @NonNull String ELEMENT_NAME = "hello";

    private final NetconfHelloMessageAdditionalHeader additionalHeader;

    private HelloMessage(final NetconfHelloMessageAdditionalHeader additionalHeader, final Document doc) {
        super(doc);
        this.additionalHeader = additionalHeader;
    }

    public HelloMessage(final Document doc) {
        this(doc, null);
    }

    public HelloMessage(final Document doc, final NetconfHelloMessageAdditionalHeader additionalHeader) {
        this(additionalHeader, doc);
        checkHelloMessage(doc);
    }

    public Optional<NetconfHelloMessageAdditionalHeader> getAdditionalHeader() {
        return Optional.ofNullable(additionalHeader);
    }

    private static void checkHelloMessage(final Document doc) {
        if (!isHelloMessage(doc)) {
            throw new IllegalArgumentException(String.format(
                "Hello message invalid format, should contain %s tag from namespace %s, but is: %s", ELEMENT_NAME,
                NamespaceURN.BASE, XmlUtil.toString(doc)));
        }
    }

    public static HelloMessage createClientHello(final Iterable<String> capabilities) {
        return new HelloMessage(createHelloMessageDoc(capabilities));
    }

    @Deprecated(since = "8.0.0", forRemoval = true)
    public static HelloMessage createClientHello(final Iterable<String> capabilities,
            final Optional<NetconfHelloMessageAdditionalHeader> additionalHeaderOptional) {
        return new HelloMessage(createHelloMessageDoc(capabilities), additionalHeaderOptional.orElse(null));
    }

    private static Document createHelloMessageDoc(final Iterable<String> capabilities) {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element helloElement = doc.createElementNS(NamespaceURN.BASE, ELEMENT_NAME);
        Element capabilitiesElement = doc.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.CAPABILITIES);

        for (String capability : Sets.newHashSet(capabilities)) {
            Element capElement = doc.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.CAPABILITY);
            capElement.setTextContent(capability);
            capabilitiesElement.appendChild(capElement);
        }

        helloElement.appendChild(capabilitiesElement);

        doc.appendChild(helloElement);
        return doc;
    }

    public static HelloMessage createServerHello(final Set<String> capabilities, final SessionIdType sessionId) {
        Document doc = createHelloMessageDoc(capabilities);
        Element sessionIdElement = doc.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.SESSION_ID);
        sessionIdElement.setTextContent(sessionId.getValue().toCanonicalString());
        doc.getDocumentElement().appendChild(sessionIdElement);
        return new HelloMessage(doc);
    }

    public static boolean isHelloMessage(final NetconfMessage msg) {
        return isHelloMessage(msg.getDocument());
    }

    public static boolean isHelloMessage(final Document document) {
        final XmlElement element = XmlElement.fromDomElement(document.getDocumentElement());
        if (!ELEMENT_NAME.equals(element.getName())) {
            return false;
        }

        final var namespace = element.namespace();
        // accept even if hello has no namespace
        return namespace == null || NamespaceURN.BASE.equals(namespace);
    }
}
