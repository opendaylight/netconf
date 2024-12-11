/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * The contents of the {@code host-meta} document, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6415.html#section-3">RFC6415, section 3</a>.
 */
@NonNullByDefault
public final class HostMeta extends AbstractHostMeta {
    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();

    /**
     * A {@code AsciiString} constant representing {@code application/xrd+xml} media type.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-2">RFC6415, section 2</a>
     */
    public static final AsciiString MEDIA_TYPE = AsciiString.cached("application/xrd+xml");

    public HostMeta(final XRD xrd) {
        super(xrd);
    }

    @Override
    public WellKnownURI wellKnownUri() {
        return WellKnownURI.HOST_META;
    }

    @Override
    AsciiString mediaType() {
        return MEDIA_TYPE;
    }

    @Override
    void writeBody(final OutputStream out) throws IOException {
        try {
            final var writer = XML_FACTORY.createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            writer.writeStartDocument();
            writer.writeCharacters("\n");
            writer.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, "XRD", XRD.NS_URI);
            writer.writeDefaultNamespace(XRD.NS_URI);

            final var links = xrd.links().toArray(Link[]::new);
            if (links.length != 0) {
                writer.writeCharacters("\n");

                for (var link : links) {
                    writer.writeCharacters("  ");
                    writer.writeEmptyElement("Link");
                    writer.writeAttribute("rel", link.rel().toString());
                    switch (link) {
                        case TargetUri targetUri -> writer.writeAttribute("href", targetUri.href().toString());
                        case Template template -> writer.writeAttribute("href", template.template());
                    }
                    writer.writeCharacters("\n");
                }
            }

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to emit XML body", e);
        }
    }
}