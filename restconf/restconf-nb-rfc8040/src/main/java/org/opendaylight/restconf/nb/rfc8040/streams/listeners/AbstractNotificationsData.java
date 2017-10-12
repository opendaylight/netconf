/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Abstract class for processing and preparing data.
 *
 */
abstract class AbstractNotificationsData {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationsData.class);
    private static final TransformerFactory TF = TransformerFactory.newInstance();
    private static final XMLOutputFactory OF = XMLOutputFactory.newInstance();

    private TransactionChainHandler transactionChainHandler;
    protected SchemaContextHandler schemaHandler;
    private String localName;

    /**
     * Transaction chain for delete data in DS on close().
     *
     * @param transactionChainHandler
     *            creating new write transaction for delete data on close
     * @param schemaHandler
     *            for getting schema to deserialize
     *            {@link MonitoringModule#PATH_TO_STREAM_WITHOUT_KEY} to
     *            {@link YangInstanceIdentifier}
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public void setCloseVars(final TransactionChainHandler transactionChainHandler,
            final SchemaContextHandler schemaHandler) {
        this.transactionChainHandler = transactionChainHandler;
        this.schemaHandler = schemaHandler;
    }

    /**
     * Delete data in DS.
     */
    protected void deleteDataInDS() throws Exception {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, IdentifierCodec
                .deserialize(MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY + this.localName, this.schemaHandler.get()));
        wTx.submit().checkedGet();
    }

    /**
     * Set localName of last path element of specific listener.
     *
     * @param localName
     *            local name
     */
    @SuppressWarnings("checkstyle:hiddenField")
    protected void setLocalNameOfPath(final String localName) {
        this.localName = localName;
    }

    /**
     * Formats data specified by RFC3339.
     *
     * @param now time stamp
     * @return Data specified by RFC3339.
     */
    protected static String toRFC3339(final Instant now) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    /**
     * Creates {@link Document} document.
     *
     * @return {@link Document} document.
     */
    protected static Document createDocument() {
        return UntrustedXML.newDocumentBuilder().newDocument();
    }

    /**
     * Write normalized node to {@link DOMResult}.
     *
     * @param normalized
     *            data
     * @param context
     *            actual schema context
     * @param schemaPath
     *            schema path of data
     * @return {@link DOMResult}
     */
    protected DOMResult writeNormalizedNode(final NormalizedNode<?, ?> normalized, final SchemaContext context,
            final SchemaPath schemaPath) throws IOException, XMLStreamException {
        final Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        final DOMResult result = new DOMResult(doc);
        NormalizedNodeWriter normalizedNodeWriter = null;
        NormalizedNodeStreamWriter normalizedNodeStreamWriter = null;
        XMLStreamWriter writer = null;

        try {
            writer = OF.createXMLStreamWriter(result);
            normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
            normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter);

            normalizedNodeWriter.write(normalized);

            normalizedNodeWriter.flush();
        } finally {
            if (normalizedNodeWriter != null) {
                normalizedNodeWriter.close();
            }
            if (normalizedNodeStreamWriter != null) {
                normalizedNodeStreamWriter.close();
            }
            if (writer != null) {
                writer.close();
            }
        }

        return result;
    }

    /**
     * Generating base element of every notification.
     *
     * @param doc
     *            base {@link Document}
     * @return element of {@link Document}
     */
    protected Element basePartDoc(final Document doc) {
        final Element notificationElement =
                doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0", "notification");

        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(Instant.now()));
        notificationElement.appendChild(eventTimeElement);

        return notificationElement;
    }

    /**
     * Generating of {@link Document} transforming to string.
     *
     * @param doc
     *            {@link Document} with data
     * @return - string from {@link Document}
     */
    protected String transformDoc(final Document doc) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            final Transformer transformer = TF.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        } catch (final TransformerException e) {
            // FIXME: this should raise an exception
            final String msg = "Error during transformation of Document into String";
            LOG.error(msg, e);
            return msg;
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
