/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.FormattableBodySupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.Errors;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.errors.Error;

/**
 * A {@link FormattableBody} of <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.9">yang-errors</a> data
 * template.
 *
 * @param errors reported errors, guaranteed to have at least one element
 */
@NonNullByDefault
public record YangErrorsBody(List<ServerError> errors) {
    private static final String XML_NAMESPACE = Errors.QNAME.getNamespace().toString();
    private static final String ERRORS = Errors.QNAME.getLocalName();
    private static final String ERROR = Error.QNAME.getLocalName();
    private static final String ERROR_TYPE = "error-type";
    private static final String ERROR_TAG = "error-tag";
    private static final String ERROR_APP_TAG = "error-app-tag";
    private static final String ERROR_MESSAGE = "error-message";
    private static final String ERROR_INFO = "error-info";
    private static final String ERROR_PATH = "error-path";

    // FIXME: do not use repairing output factory
    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    public YangErrorsBody(final List<ServerError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("empty errors");
        }
        this.errors = List.copyOf(errors);
    }

    public YangErrorsBody(final ServerError error) {
        this(List.of(error));
    }

    public void formatToJSON(final OutputStream out) throws IOException {
        try (var writer = FormattableBodySupport.createJsonWriter(out, PrettyPrintParam.TRUE)) {
            writer.beginObject()
                .name(ERRORS).beginObject()
                .name(ERROR).beginArray();

            for (final var error : errors) {
                writer.beginObject().name(ERROR_TAG).value(error.tag().elementBody());
                final var errorAppTag = error.appTag();
                if (errorAppTag != null) {
                    writer.name(ERROR_APP_TAG).value(errorAppTag);
                }
                final var errorInfo = error.info();
                if (errorInfo != null) {
                    writer.name(ERROR_INFO).value(errorInfo.elementBody());
                }
                final var errorMessage = error.message();
                if (errorMessage != null) {
                    writer.name(ERROR_MESSAGE).value(errorMessage.elementBody());
                }
                final var errorPath = error.path();
                if (errorPath != null) {
                    writer.name(ERROR_PATH);
                    errorPath.databind().jsonCodecs().instanceIdentifierCodec()
                        .writeValue(writer, errorPath.path());
                }
                writer.name(ERROR_TYPE).value(error.type().elementBody());
                writer.endObject();
            }

            writer.endArray().endObject().endObject();
        }
    }

    public void formatToXML(final OutputStream out) throws IOException, XMLStreamException {
        final var writer = FormattableBodySupport.indentXmlWriter(
            XML_OUTPUT_FACTORY.createXMLStreamWriter(out, StandardCharsets.UTF_8.name()),
            PrettyPrintParam.TRUE);

        writer.writeStartDocument();
        writer.writeStartElement(ERRORS);
        writer.writeDefaultNamespace(XML_NAMESPACE);
        for (final var error : errors) {
            writer.writeStartElement(ERROR);
            // Write error-type element
            writer.writeStartElement(ERROR_TYPE);
            writer.writeCharacters(error.type().elementBody());
            writer.writeEndElement();

            final var path = error.path();
            if (path != null) {
                writer.writeStartElement(ERROR_PATH);
                path.databind().xmlCodecs().instanceIdentifierCodec().writeValue(writer, path.path());
                writer.writeEndElement();
            }
            final var message = error.message();
            if (message != null) {
                writer.writeStartElement(ERROR_MESSAGE);
                // FIXME: propagate xml:lang
                writer.writeCharacters(message.elementBody());
                writer.writeEndElement();
            }

            // Write error-tag element
            writer.writeStartElement(ERROR_TAG);
            writer.writeCharacters(error.tag().elementBody());
            writer.writeEndElement();

            final var appTag = error.appTag();
            if (appTag != null) {
                writer.writeStartElement(ERROR_APP_TAG);
                writer.writeCharacters(appTag);
                writer.writeEndElement();
            }
            final var info = error.info();
            if (info != null) {
                // FIXME: defer to FormattableBody?
                writer.writeStartElement(ERROR_INFO);
                writer.writeCharacters(info.elementBody());
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("errors", errors).toString();
    }
}
