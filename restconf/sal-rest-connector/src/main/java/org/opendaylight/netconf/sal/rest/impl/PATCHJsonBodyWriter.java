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
import org.opendaylight.netconf.sal.rest.api.Draft02.MediaTypes;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;

@Provider
@Produces({MediaTypes.PATCH_STATUS + RestconfService.JSON})
public class PATCHJsonBodyWriter implements MessageBodyWriter<PATCHStatusContext> {

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
            for (PATCHStatusEntity patchStatusEntity : patchStatusContext.getEditCollection()) {
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

    private void reportSuccess(JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("ok").beginArray().nullValue().endArray();
    }

    private static void reportErrors(List<RestconfError> errors, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("errors");
        jsonWriter.beginObject();
        jsonWriter.name("error");
        jsonWriter.beginArray();

        for (RestconfError restconfError : errors) {
            jsonWriter.beginObject();
            jsonWriter.name("error-type").value(restconfError.getErrorType().getErrorTypeTag());
            jsonWriter.name("error-tag").value(restconfError.getErrorTag().getTagValue());
            //TODO: fix error-path reporting (separate error-path from error-message)
            //jsonWriter.name("error-path").value(restconfError.getErrorPath());
            jsonWriter.name("error-message").value(restconfError.getErrorMessage());
            jsonWriter.endObject();
        }

        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream) {
        return JsonWriterFactory.createJsonWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8));
    }
}
