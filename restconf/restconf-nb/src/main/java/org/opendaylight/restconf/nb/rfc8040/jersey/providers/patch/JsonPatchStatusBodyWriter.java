/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;

@Provider
@Produces(MediaTypes.APPLICATION_YANG_DATA_JSON)
public class JsonPatchStatusBodyWriter extends AbstractPatchStatusBodyWriter {
    @Override
    public void writeTo(final PatchStatusContext patchStatusContext, final Class<?> type, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream)
            throws IOException {

        final JsonWriter jsonWriter = createJsonWriter(entityStream);
        jsonWriter.beginObject().name("ietf-yang-patch:yang-patch-status");
        jsonWriter.beginObject();
        jsonWriter.name("patch-id").value(patchStatusContext.patchId());
        if (patchStatusContext.ok()) {
            reportSuccess(jsonWriter);
        } else {
            if (patchStatusContext.globalErrors() != null) {
                reportErrors(patchStatusContext.globalErrors(), jsonWriter);
            }

            jsonWriter.name("edit-status");
            jsonWriter.beginObject();
            jsonWriter.name("edit");
            jsonWriter.beginArray();
            for (final PatchStatusEntity patchStatusEntity : patchStatusContext.editCollection()) {
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
            jsonWriter.name("error-type").value(restconfError.getErrorType().elementBody());
            jsonWriter.name("error-tag").value(restconfError.getErrorTag().elementBody());

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
