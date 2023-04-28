/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.stream.ForwardingNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * Proxy {@link NormalizedNodeStreamWriter} that is responsible for serialization of empty leaf-list and list
 * nodes to output {@link XMLStreamWriter} as empty XML elements. Other operations are proxied
 * to delegated {@link NormalizedNodeStreamWriter}.
 */
final class EmptyListXmlWriter extends ForwardingNormalizedNodeStreamWriter {
    private final NormalizedNodeStreamWriter delegatedWriter;
    private final XMLStreamWriter xmlStreamWriter;

    private boolean isInsideEmptyList = false;

    EmptyListXmlWriter(final NormalizedNodeStreamWriter delegatedWriter, final XMLStreamWriter xmlStreamWriter) {
        this.delegatedWriter = delegatedWriter;
        this.xmlStreamWriter = xmlStreamWriter;
    }

    @Override
    protected NormalizedNodeStreamWriter delegate() {
        return delegatedWriter;
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childSizeHint) throws IOException {
        if (childSizeHint == 0) {
            writeEmptyElement(name);
        } else {
            super.startUnkeyedList(name, childSizeHint);
        }
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        if (childSizeHint == 0) {
            writeEmptyElement(name);
        } else {
            super.startMapNode(name, childSizeHint);
        }
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        if (childSizeHint == 0) {
            writeEmptyElement(name);
        } else {
            super.startOrderedMapNode(name, childSizeHint);
        }
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        if (childSizeHint == 0) {
            writeEmptyElement(name);
        } else {
            super.startLeafSet(name, childSizeHint);
        }
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        if (childSizeHint == 0) {
            writeEmptyElement(name);
        } else {
            super.startOrderedLeafSet(name, childSizeHint);
        }
    }

    @Override
    public void endNode() throws IOException {
        if (isInsideEmptyList) {
            isInsideEmptyList = false;
        } else {
            super.endNode();
        }
    }

    private void writeEmptyElement(final NodeIdentifier identifier) throws IOException {
        final QName nodeType = identifier.getNodeType();
        try {
            xmlStreamWriter.writeEmptyElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(),
                    nodeType.getNamespace().toString());
        } catch (XMLStreamException e) {
            throw new IOException("Failed to serialize empty element to XML: " + identifier, e);
        }
        isInsideEmptyList = true;
    }
}