/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import com.google.common.collect.Collections2;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public final class NetconfMessageUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageUtil.class);

    private NetconfMessageUtil() {
        // Hidden on purpose
    }

    public static boolean isOKMessage(final NetconfMessage message) {
        return isOKMessage(message.getDocument());
    }

    public static boolean isOKMessage(final Document document) {
        return isOKMessage(XmlElement.fromDomDocument(document));
    }

    public static boolean isOKMessage(final XmlElement xmlElement) {
        final var children = xmlElement.getChildElements();
        return children.size() == 1 && children.get(0).getName().equals(XmlNetconfConstants.OK);
    }

    public static boolean isErrorMessage(final NetconfMessage message) {
        return isErrorMessage(message.getDocument());
    }

    public static boolean isErrorMessage(final Document document) {
        return isErrorMessage(XmlElement.fromDomDocument(document));
    }

    public static boolean isErrorMessage(final XmlElement xmlElement) {
        // In the case of multiple rpc-error messages, size will not be 1 but we still want to report as Error
        return xmlElement.getChildElements().stream()
            .anyMatch(result -> DocumentedException.RPC_ERROR.equals(result.getName()));
    }

    public static Collection<String> extractCapabilitiesFromHello(final Document doc) {
        XmlElement responseElement = XmlElement.fromDomDocument(doc);
        // Extract child element <capabilities> from <hello> with or without(fallback) the same namespace
        Optional<XmlElement> capabilitiesElement = responseElement
                .getOnlyChildElementWithSameNamespaceOptionally(XmlNetconfConstants.CAPABILITIES);
        if (capabilitiesElement.isEmpty()) {
            capabilitiesElement = responseElement.getOnlyChildElementOptionally(XmlNetconfConstants.CAPABILITIES);
        }

        List<XmlElement> caps = capabilitiesElement.orElseThrow().getChildElements(XmlNetconfConstants.CAPABILITY);
        return Collections2.transform(caps, input -> {
            // Trim possible leading/tailing whitespace
            try {
                return input.getTextContent().trim();
            } catch (DocumentedException e) {
                LOG.trace("Error fetching input text content",e);
                return null;
            }
        });

    }
}
