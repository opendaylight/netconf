/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.status.YangPatchStatus;

/**
 * Result of a {@code PATCH} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2.3">RFC8072, section 2.3</a>.
 */
public final class YangPatchStatusBody extends FormattableBody {
    private static final String IETF_YANG_PATCH_NAMESPACE = YangPatchStatus.QNAME.getNamespace().toString();

    private final PatchStatusContext status;

    public YangPatchStatusBody(final PatchStatusContext status) {
        this.status = requireNonNull(status);
    }

    @Override
    public void formatToJSON(final FormatParameters format, final OutputStream out) throws IOException {
        try (var writer = FormattableBodySupport.createJsonWriter(out, format)) {
            writer.beginObject().name("ietf-yang-patch:yang-patch-status")
                .beginObject().name("patch-id").value(status.patchId());

            if (status.ok()) {
                writeOk(writer);
            } else {
                final var globalErrors = status.globalErrors();
                if (globalErrors != null) {
                    writeErrors(globalErrors, writer);
                } else {
                    writer.name("edit-status").beginObject()
                        .name("edit").beginArray();
                    for (var editStatus : status.editCollection()) {
                        writer.beginObject().name("edit-id").value(editStatus.getEditId());

                        final var editErrors = editStatus.getEditErrors();
                        if (editErrors != null) {
                            writeErrors(editErrors, writer);
                        } else if (editStatus.isOk()) {
                            writeOk(writer);
                        }
                        writer.endObject();
                    }
                    writer.endArray().endObject();
                }
            }
            writer.endObject().endObject();
        }
    }

    @Override
    public void formatToXML(final FormatParameters format, final OutputStream out) throws IOException {
        final var writer = FormattableBodySupport.createXmlWriter(out, format);
        try {
            formatToXML(writer);
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write body", e);
        }
    }

    private void formatToXML(final XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, "yang-patch-status", IETF_YANG_PATCH_NAMESPACE);
        writer.writeDefaultNamespace(IETF_YANG_PATCH_NAMESPACE);
        writer.writeStartElement("patch-id");
        writer.writeCharacters(status.patchId());
        writer.writeEndElement();

        if (status.ok()) {
            writer.writeEmptyElement("ok");
        } else {
            final var globalErrors = status.globalErrors();
            if (globalErrors != null) {
                reportErrors(globalErrors, writer);
            } else {
                writer.writeStartElement("edit-status");
                for (var patchStatusEntity : status.editCollection()) {
                    writer.writeStartElement("edit");
                    writer.writeStartElement("edit-id");
                    writer.writeCharacters(patchStatusEntity.getEditId());
                    writer.writeEndElement();

                    final var editErrors = patchStatusEntity.getEditErrors();
                    if (editErrors != null) {
                        reportErrors(editErrors, writer);
                    } else if (patchStatusEntity.isOk()) {
                        writer.writeEmptyElement("ok");
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
        writer.close();
    }

    private static void writeOk(final JsonWriter writer) throws IOException {
        writer.name("ok").beginArray().nullValue().endArray();
    }

    private void writeErrors(final List<RestconfError> errors, final JsonWriter writer) throws IOException {
        writer.name("errors").beginObject().name("error").beginArray();

        for (var restconfError : errors) {
            writer.beginObject()
                .name("error-type").value(restconfError.getErrorType().elementBody())
                .name("error-tag").value(restconfError.getErrorTag().elementBody());

            final var errorPath = restconfError.getErrorPath();
            if (errorPath != null) {
                writer.name("error-path");
                status.databind().jsonCodecs().instanceIdentifierCodec().writeValue(writer, errorPath);
            }
            final var errorMessage = restconfError.getErrorMessage();
            if (errorMessage != null) {
                writer.name("error-message").value(errorMessage);
            }
            final var errorInfo = restconfError.getErrorInfo();
            if (errorInfo != null) {
                writer.name("error-info").value(errorInfo);
            }

            writer.endObject();
        }

        writer.endArray().endObject();
    }

    private void reportErrors(final List<RestconfError> errors, final XMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeStartElement("errors");

        for (var restconfError : errors) {
            writer.writeStartElement("error-type");
            writer.writeCharacters(restconfError.getErrorType().elementBody());
            writer.writeEndElement();

            writer.writeStartElement("error-tag");
            writer.writeCharacters(restconfError.getErrorTag().elementBody());
            writer.writeEndElement();

            // optional node
            final var errorPath = restconfError.getErrorPath();
            if (errorPath != null) {
                writer.writeStartElement("error-path");
                status.databind().xmlCodecs().instanceIdentifierCodec().writeValue(writer, errorPath);
                writer.writeEndElement();
            }

            // optional node
            final var errorMessage = restconfError.getErrorMessage();
            if (errorMessage != null) {
                writer.writeStartElement("error-message");
                writer.writeCharacters(errorMessage);
                writer.writeEndElement();
            }

            // optional node
            final var errorInfo = restconfError.getErrorInfo();
            if (errorInfo != null) {
                writer.writeStartElement("error-info");
                writer.writeCharacters(errorInfo);
                writer.writeEndElement();
            }
        }

        writer.writeEndElement();
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("status", status);
    }
}
