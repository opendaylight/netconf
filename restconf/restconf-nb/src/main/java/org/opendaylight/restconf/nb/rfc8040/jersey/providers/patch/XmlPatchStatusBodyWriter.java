/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.status.YangPatchStatus;

@Provider
@Produces(MediaTypes.APPLICATION_YANG_DATA_XML)
public class XmlPatchStatusBodyWriter extends AbstractPatchStatusBodyWriter {
    private static final String XML_NAMESPACE = YangPatchStatus.QNAME.getNamespace().toString();
    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public void writeTo(final PatchStatusContext patchStatusContext, final Class<?> type, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream)
            throws IOException {
        try {
            final XMLStreamWriter xmlWriter =
                    XML_FACTORY.createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
            writeDocument(xmlWriter, patchStatusContext);
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write body", e);
        }
    }

    private static void writeDocument(final XMLStreamWriter writer, final PatchStatusContext context)
        throws XMLStreamException {
        writer.writeStartElement("", "yang-patch-status", XML_NAMESPACE);
        writer.writeStartElement("patch-id");
        writer.writeCharacters(context.getPatchId());
        writer.writeEndElement();

        if (context.isOk()) {
            writer.writeEmptyElement("ok");
        } else {
            final var globalErrors = context.getGlobalErrors();
            if (globalErrors != null) {
                reportErrors(globalErrors, writer);
            } else {
                writer.writeStartElement("edit-status");
                for (final PatchStatusEntity patchStatusEntity : context.getEditCollection()) {
                    writer.writeStartElement("edit");
                    writer.writeStartElement("edit-id");
                    writer.writeCharacters(patchStatusEntity.getEditId());
                    writer.writeEndElement();
                    if (patchStatusEntity.getEditErrors() != null) {
                        reportErrors(patchStatusEntity.getEditErrors(), writer);
                    } else if (patchStatusEntity.isOk()) {
                        writer.writeEmptyElement("ok");
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
        writer.flush();
    }

    private static void reportErrors(final List<RestconfError> errors, final XMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeStartElement("errors");

        for (final RestconfError restconfError : errors) {
            writer.writeStartElement("error-type");
            writer.writeCharacters(restconfError.getErrorType().elementBody());
            writer.writeEndElement();

            writer.writeStartElement("error-tag");
            writer.writeCharacters(restconfError.getErrorTag().elementBody());
            writer.writeEndElement();

            // optional node
            if (restconfError.getErrorPath() != null) {
                writer.writeStartElement("error-path");
                writer.writeCharacters(restconfError.getErrorPath().toString());
                writer.writeEndElement();
            }

            // optional node
            if (restconfError.getErrorMessage() != null) {
                writer.writeStartElement("error-message");
                writer.writeCharacters(restconfError.getErrorMessage());
                writer.writeEndElement();
            }

            // optional node
            if (restconfError.getErrorInfo() != null) {
                writer.writeStartElement("error-info");
                writer.writeCharacters(restconfError.getErrorInfo());
                writer.writeEndElement();
            }
        }

        writer.writeEndElement();
    }
}
