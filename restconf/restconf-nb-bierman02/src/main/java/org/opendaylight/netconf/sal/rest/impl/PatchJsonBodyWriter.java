/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;


@Provider
@Produces({ Draft02.MediaTypes.PATCH_STATUS + RestconfService.JSON })
public class PatchJsonBodyWriter implements MessageBodyWriter<PatchStatusContext> {

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

        final JsonWriter jsonWriter = createJsonWriter(entityStream);
        jsonWriter.beginObject().name("ietf-yang-patch:yang-patch-status");
        jsonWriter.beginObject();
        jsonWriter.name("patch-id").value(patchStatusContext.getPatchId());
        if (patchStatusContext.isOk()) {
            reportSuccess(jsonWriter);
        } else {
            if (patchStatusContext.getGlobalErrors() != null) {
                reportErrors(patchStatusContext.getGlobalErrors(), jsonWriter);
            }

            jsonWriter.name("edit-status");
            jsonWriter.beginObject();
            jsonWriter.name("edit");
            jsonWriter.beginArray();
            for (final PatchStatusEntity patchStatusEntity : patchStatusContext.getEditCollection()) {
                jsonWriter.beginObject();
                jsonWriter.name("edit-id").value(patchStatusEntity.getEditId());
                if (patchStatusEntity.getEditErrors() != null) {
                    reportErrors(patchStatusEntity.getEditErrors(), jsonWriter);
                } else {
                    if (patchStatusEntity.isOk()) {
                        reportSuccess(jsonWriter);
                    }
                }
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.endObject();
        jsonWriter.endObject();
        jsonWriter.flush();
    }

    private static void reportSuccess(final JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("ok").beginArray().nullValue().endArray();
    }

    private static void reportErrors(final List<RestconfError> errors, final JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("errors");
        jsonWriter.beginObject();
        jsonWriter.name("error");
        jsonWriter.beginArray();

        for (final RestconfError restconfError : errors) {
            jsonWriter.beginObject();
            jsonWriter.name("error-type").value(restconfError.getErrorType().getErrorTypeTag());
            jsonWriter.name("error-tag").value(restconfError.getErrorTag().getTagValue());

            // optional node
            if (restconfError.getErrorPath() != null) {
                jsonWriter.name("error-path").value(restconfError.getErrorPath().toString());
            }

            // optional node
            if (restconfError.getErrorMessage() != null) {
                jsonWriter.name("error-message").value(restconfError.getErrorMessage());
            }

            // optional node
            if (restconfError.getErrorInfo() != null) {
                jsonWriter.name("error-info").value(restconfError.getErrorInfo());
            }

            jsonWriter.endObject();
        }

        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream) {
        return JsonWriterFactory.createJsonWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8));
    }
}
