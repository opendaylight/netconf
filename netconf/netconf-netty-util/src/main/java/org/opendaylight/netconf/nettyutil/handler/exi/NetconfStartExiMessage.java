/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.exi;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Start-exi netconf message.
 */
public final class NetconfStartExiMessage extends NetconfMessage {
    @VisibleForTesting
    public static final String START_EXI = "start-exi";


    private NetconfStartExiMessage(final Document doc) {
        super(doc);
    }

    public static NetconfStartExiMessage create(final EXIParameters exiOptions, final String messageId) {
        final Document doc = XmlUtil.newDocument();
        final Element rpcElement = doc.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_KEY);
        rpcElement.setAttributeNS(NamespaceURN.BASE, XmlNetconfConstants.MESSAGE_ID, messageId);

        // TODO draft http://tools.ietf.org/html/draft-varga-netconf-exi-capability-02#section-3.5.1 has no namespace
        // for start-exi element in xml
        final Element startExiElement = doc.createElementNS(NamespaceURN.EXI, START_EXI);

        addAlignment(exiOptions, doc, startExiElement);
        addFidelity(exiOptions, doc, startExiElement);
        addSchema(exiOptions, doc, startExiElement);

        rpcElement.appendChild(startExiElement);

        doc.appendChild(rpcElement);
        return new NetconfStartExiMessage(doc);
    }

    private static void addAlignment(final EXIParameters exiOptions, final Document doc,
            final Element startExiElement) {
        final Element alignmentElement = doc.createElementNS(NamespaceURN.EXI, EXIParameters.EXI_PARAMETER_ALIGNMENT);

        alignmentElement.setTextContent(exiOptions.getAlignment());
        startExiElement.appendChild(alignmentElement);
    }

    private static void addFidelity(final EXIParameters exiOptions, final Document doc, final Element startExiElement) {
        final List<Element> fidelityElements = new ArrayList<>(5);
        createFidelityElement(doc, fidelityElements, exiOptions.getPreserveComments());
        createFidelityElement(doc, fidelityElements, exiOptions.getPreserveDTD());
        createFidelityElement(doc, fidelityElements, exiOptions.getPreserveLexicalValues());
        createFidelityElement(doc, fidelityElements, exiOptions.getPreservePIs());
        createFidelityElement(doc, fidelityElements, exiOptions.getPreservePrefixes());

        if (!fidelityElements.isEmpty()) {
            final Element fidelityElement = doc.createElementNS(NamespaceURN.EXI, EXIParameters.EXI_PARAMETER_FIDELITY);
            for (final Element element : fidelityElements) {
                fidelityElement.appendChild(element);
            }
            startExiElement.appendChild(fidelityElement);
        }
    }

    private static void addSchema(final EXIParameters exiOptions, final Document doc, final Element startExiElement) {
        final String schema = exiOptions.getSchema();
        if (schema != null) {
            final Element child = doc.createElementNS(NamespaceURN.EXI, EXIParameters.EXI_PARAMETER_SCHEMAS);
            child.setTextContent(schema);
            startExiElement.appendChild(child);
        }
    }

    private static void createFidelityElement(final Document doc, final List<Element> elements,
                                              final String fidelity) {
        if (fidelity != null) {
            elements.add(doc.createElementNS(NamespaceURN.EXI, fidelity));
        }
    }
}
