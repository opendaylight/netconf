/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.patch;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
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
import org.opendaylight.netconf.sal.rest.api.Draft02.MediaTypes;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.Draft11;

@Provider
@Produces({Draft11.MediaTypes.PATCH_STATUS + RestconfService.XML})
public class Draft11PATCHXmlBodyWriter implements MessageBodyWriter<PATCHStatusContext> {

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(PATCHStatusContext.class);
    }

    @Override
    public long getSize(PATCHStatusContext patchStatusContext, Class<?> type, Type genericType, Annotation[]
            annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(PATCHStatusContext patchStatusContext, Class<?> type, Type genericType, Annotation[]
            annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                throws IOException, WebApplicationException {

        try {
            XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream);
            writeDocument(xmlWriter, patchStatusContext);
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (final FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }

    }

    private static void writeDocument(XMLStreamWriter writer, PATCHStatusContext context) throws XMLStreamException, IOException {
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
            for (PATCHStatusEntity patchStatusEntity : context.getEditCollection()) {
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

    private static void reportErrors(List<RestconfError> errors, XMLStreamWriter writer) throws IOException, XMLStreamException {
        writer.writeStartElement("errors");

        for (RestconfError restconfError : errors) {
            writer.writeStartElement("error-type");
            writer.writeCharacters(restconfError.getErrorType().getErrorTypeTag());
            writer.writeEndElement();
            writer.writeStartElement("error-tag");
            writer.writeCharacters(restconfError.getErrorTag().getTagValue());
            writer.writeEndElement();
            //TODO: fix error-path reporting (separate error-path from error-message)
//            writer.writeStartElement("error-path");
//            writer.writeCharacters(restconfError.getErrorPath());
//            writer.writeEndElement();
            writer.writeStartElement("error-message");
            writer.writeCharacters(restconfError.getErrorMessage());
            writer.writeEndElement();
        }

        writer.writeEndElement();
    }
}
