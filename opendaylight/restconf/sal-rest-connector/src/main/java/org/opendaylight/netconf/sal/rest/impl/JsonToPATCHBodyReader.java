/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
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
            return new PATCHContext(path, null);
        }

        final JsonReader jsonReader = new JsonReader(new InputStreamReader(entityStream));
        final List<NormalizedNode<?, ?>> resultList = read(jsonReader, path);
        jsonReader.close();

        return new PATCHContext(path, resultList);
    }

    public static List<NormalizedNode<?,?>> read(final JsonReader in, InstanceIdentifierContext path) throws
            IOException {

        //TODO: make sure this method works OK & refactor it up eventually

        boolean inEdit = false;
        boolean inValue = false;

        String operation = null;
        String target = null;

        List<NormalizedNode<?,?>> resultCollection = new ArrayList<>();

        while (in.hasNext()) {
            switch (in.peek()) {
                case STRING:
                case NUMBER:
                    in.nextString();
                    break;
                case BOOLEAN:
                    Boolean.toString(in.nextBoolean());
                    break;
                case NULL:
                    in.nextNull();
                    break;
                case BEGIN_ARRAY:
                    in.beginArray();
                    break;
                case BEGIN_OBJECT:
                    if (inEdit && operation != null & target != null & inValue) {
                        //let's do the stuff - find out target node
                        DataSchemaNode targetNode = ((DataNodeContainer)(path.getSchemaNode())).getDataChildByName
                                (target.replace("/", ""));
                        if (targetNode == null) {
                            //TODO: report error correctly
                            LOG.error("Target node {} not found in path {} ", target, path.getSchemaNode());
                        }

                        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

                        //keep on parsing json from place where target points
                        final JsonParserStream jsonParser = JsonParserStream.create(writer, path.getSchemaContext
                                (), path.getSchemaNode());
                        jsonParser.parse(in);

                        resultCollection.add(resultHolder.getResult());
                        inValue = false;

                        operation = null;
                        target = null;
                        in.endObject();
                    } else {
                        in.beginObject();
                    }
                    break;
                case END_DOCUMENT:
                    break;
                case NAME:
                    final String name = in.nextName();
                    if (name.equals("edit")) {
                        inEdit = true;
                    } else if (name.equals("operation")) {
                        operation = in.nextString();
                    } else if (name.equals("target")) {
                        target = in.nextString();
                    } else if (name.equals("value")) {
                        inValue = true;
                    }
                    break;
                case END_OBJECT:
                    in.endObject();
                    break;
            case END_ARRAY:
                in.endArray();
                break;

            default:
                break;
            }
        }

        return ImmutableList.copyOf(resultCollection);
    }
}
