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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

@Provider
@Produces({ Rfc8040.MediaTypes.PATCH_STATUS + RestconfConstants.XML })
public class PatchXmlBodyWriter implements MessageBodyWriter<PatchStatusContext> {

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType,
                               final Annotation[] annotations, final MediaType mediaType) {
        return type.equals(PatchStatusContext.class);
    }

    @Override
    public long getSize(final PatchStatusContext patchStatusContext, final Class<?> type, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final PatchStatusContext patchStatusContext, final Class<?> type, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream)
            throws IOException, WebApplicationException {

        try {
            final XMLStreamWriter xmlWriter =
                    XML_FACTORY.createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
            writeDocument(xmlWriter, patchStatusContext);
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (final FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeDocument(final XMLStreamWriter writer, final PatchStatusContext context)
            throws XMLStreamException, IOException {
        writer.writeStartElement("", "yang-patch-status", "urn:ietf:params:xml:ns:yang:ietf-yang-patch");
        writer.writeStartElement("patch-id");
        writer.writeCharacters(context.getPatchId());
        writer.writeEndElement();

        if (context.isOk()) {
            writer.writeEmptyElement("ok");
        } else {
            if (context.getGlobalErrors() != null) {
                reportErrors(context.getGlobalErrors(), writer);
            }
            writer.writeStartElement("edit-status");
            for (final PatchStatusEntity patchStatusEntity : context.getEditCollection()) {
                writer.writeStartElement("edit");
                writer.writeStartElement("edit-id");
                writer.writeCharacters(patchStatusEntity.getEditId());
                writer.writeEndElement();
                if (patchStatusEntity.getEditErrors() != null) {
                    reportErrors(patchStatusEntity.getEditErrors(), writer);
                } else {
                    if (patchStatusEntity.isOk()) {
                        writer.writeEmptyElement("ok");
                    }
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();

        }
        writer.writeEndElement();

        writer.flush();
    }

    private static void reportErrors(final List<RestconfError> errors, final XMLStreamWriter writer)
            throws IOException, XMLStreamException {
        writer.writeStartElement("errors");

        for (final RestconfError restconfError : errors) {
            writer.writeStartElement("error-type");
            writer.writeCharacters(restconfError.getErrorType().getErrorTypeTag());
            writer.writeEndElement();

            writer.writeStartElement("error-tag");
            writer.writeCharacters(restconfError.getErrorTag().getTagValue());
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
