/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static java.util.Objects.requireNonNull;

import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;

/**
 * NetconfMessage represents a wrapper around {@link Document}.
 */
public abstract class NetconfMessage {
    private static final Transformer TRANSFORMER;

    static {
        final Transformer t;
        try {
            t = XmlUtil.newIndentingTransformer();
        } catch (TransformerConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        TRANSFORMER = t;
    }

    private final @NonNull Document doc;

    protected NetconfMessage(final Document doc) {
        this.doc = requireNonNull(doc);
    }

    public static @NonNull NetconfMessage of(final Document doc) {
        if (HelloMessage.isHelloMessage(doc)) {
            return new HelloMessage(doc);
        } else {
            throw new IllegalArgumentException("Unhandled message " + XmlUtil.toString(doc));
        }
    }

    public @NonNull Document getDocument() {
        return doc;
    }

    @Override
    public String toString() {
        final var result = new StreamResult(new StringWriter());
        final var source = new DOMSource(doc.getDocumentElement());

        try {
            // Slight critical section is a tradeoff. This should be reasonably fast.
            synchronized (TRANSFORMER) {
                TRANSFORMER.transform(source, result);
            }
        } catch (TransformerException e) {
            throw new IllegalStateException("Failed to encode document", e);
        }

        return result.getWriter().toString();
    }
}
