/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import com.google.common.base.Throwables;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamPushTask;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Consumes({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
public class JsonNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(JsonNormalizedNodeBodyReader.class);

    public JsonNormalizedNodeBodyReader(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        super(databindProvider, mountPointService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected NormalizedNodePayload readBody(final InstanceIdentifierContext path, final InputStream entityStream)
            throws WebApplicationException {
        try {
            return readFrom(path, entityStream);
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null;
        }
    }

    public static NormalizedNodePayload readFrom(final InstanceIdentifierContext path, final InputStream entityStream)
            throws IOException {
        final var resultHolder = new NormalizedNodeResult();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final var jsonCodecFactory = JSONCodecFactorySupplier.RFC7951.getShared(path.getSchemaContext());

        final var jsonParser = JsonParserStream.create(writer, jsonCodecFactory);
        final var jsonReader = new JsonReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8));
        jsonParser.parse(jsonReader);

        final var jsonStreamPushTask = JSONNormalizedNodeStreamPushTask.builder()
            .withCodecFactory(jsonCodecFactory)
            .withInference(path.inference())
            .withWriter(writer)
            .build();
        final var pathArguments = jsonStreamPushTask.execute();

        // FIXME: can result really be null?
        return NormalizedNodePayload.ofNullable(path.withConcatenatedArgs(pathArguments), resultHolder.getResult());
    }

    private static void propagateExceptionAs(final Exception exception) throws RestconfDocumentedException {
        Throwables.throwIfInstanceOf(exception, RestconfDocumentedException.class);
        LOG.debug("Error parsing json input", exception);

        if (exception instanceof ResultAlreadySetException) {
            throw new RestconfDocumentedException("Error parsing json input: Failed to create new parse result data. "
                    + "Are you creating multiple resources/subresources in POST request?", exception);
        }

        RestconfDocumentedException.throwIfYangError(exception);
        throw new RestconfDocumentedException("Error parsing input: " + exception.getMessage(), ErrorType.PROTOCOL,
            ErrorTag.MALFORMED_MESSAGE, exception);
    }
}
