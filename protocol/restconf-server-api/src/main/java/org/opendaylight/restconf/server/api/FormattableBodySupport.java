/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.util.xml.IndentedXML;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;

/**
 * Various methods supporting {@link FormattableBody}.
 */
@NonNullByDefault
public final class FormattableBodySupport {
    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();
    private static final String PRETTY_PRINT_INDENT = "  ";

    private FormattableBodySupport() {
        // Hidden on purpose
    }

    public static JsonWriter createJsonWriter(final OutputStream out, final PrettyPrintParam prettyPrint) {
        final var ret = JsonWriterFactory.createJsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        ret.setIndent(prettyPrint.value() ? PRETTY_PRINT_INDENT : "");
        return ret;
    }

    public static XMLStreamWriter createXmlWriter(final OutputStream out, final PrettyPrintParam prettyPrint)
            throws IOException {
        return indentXmlWriter(createXmlWriter(out), prettyPrint);
    }

    private static XMLStreamWriter createXmlWriter(final OutputStream out) throws IOException {
        try {
            return XML_FACTORY.createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IOException(e);
        }
    }

    public static XMLStreamWriter indentXmlWriter(final XMLStreamWriter writer, final PrettyPrintParam prettyPrint) {
        return prettyPrint.value() ? IndentedXML.of().wrapStreamWriter(writer) : writer;
    }
}
