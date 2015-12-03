/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.YangPatch;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.YangPatchBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.yang.patch.Edit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.yang.patch.EditBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev140703.yang.patch.yang.patch.EditKey;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Consumes({ Draft02.MediaTypes.PATCH + RestconfService.JSON, MediaType.APPLICATION_JSON })
public class JsonToPATCHBodyReader extends AbstractIdentifierAwareJaxRsProvider implements MessageBodyReader<PATCHContext> {

    private final static Logger LOG = LoggerFactory.getLogger(JsonToPATCHBodyReader.class);

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public PATCHContext readFrom(Class<PATCHContext> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        try {
            return readFrom(getInstanceIdentifierContext(), entityStream);
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null; // no-op
        }
    }

    private static void propagateExceptionAs(Exception e) throws RestconfDocumentedException {
        if(e instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)e;
        }

        if(e instanceof ResultAlreadySetException) {
            LOG.debug("Error parsing json input:", e);

            throw new RestconfDocumentedException("Error parsing json input: Failed to create new parse result data. ");
        }

        LOG.debug("Error parsing json input", e);

        throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, e);
    }

    public static PATCHContext readFrom(final String uriPath, final InputStream entityStream) throws
            RestconfDocumentedException {
        try {
            return readFrom(ControllerContext.getInstance().toInstanceIdentifier(uriPath), entityStream);
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null; // no-op
        }
    }

    private static PATCHContext readFrom(final InstanceIdentifierContext<?> path, final InputStream entityStream) throws IOException {
        if (entityStream.available() < 1) {
            return new PATCHContext(path);
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Edit.class, new EditInstanceCreator());
        gsonBuilder.registerTypeAdapter(Operation.class, new OperationDeserializer());

        Gson gson = gsonBuilder.create();
        YangPatch httpPatch = gson.fromJson(new JsonReader(new InputStreamReader(entityStream)), new YangPatchBuilder()
                .build().getClass());

        for (Edit edit : httpPatch.getEdit()) {

            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

            final SchemaNode parentSchema;

            if(path.getSchemaNode() instanceof SchemaContext) {
                parentSchema = path.getSchemaContext();
            } else {
                if (SchemaPath.ROOT.equals(path.getSchemaNode().getPath().getParent())) {
                    parentSchema = path.getSchemaContext();
                } else {
                    parentSchema = SchemaContextUtil.findDataSchemaNode(path.getSchemaContext(), path.getSchemaNode().getPath().getParent());
                }
            }

            final JsonParserStream jsonParser = JsonParserStream.create(writer, path.getSchemaContext(), parentSchema);
            final JsonReader reader = new JsonReader(new InputStreamReader(entityStream));
            jsonParser.parse(reader);

        }

        return new PATCHContext(path);
    }

    private static class EditInstanceCreator implements InstanceCreator<Edit> {

        @Override
        public Edit createInstance(Type type) {
            System.out.println("type: " + type.toString());
            return new EditBuilder().build();
        }
    }

    private static class OperationDeserializer implements JsonDeserializer<Operation> {

        @Override
        public Operation deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext
                jsonDeserializationContext) throws JsonParseException {

            for (Operation operation : Operation.values())
            {
                if (operation.equals(jsonElement.getAsString()))
                    return operation;
            }

            return null;
        }
    }
}
