/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.gson.stream.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
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
            return readFrom(path, entityStream, isPost());
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null;
        }
    }

    public static NormalizedNodePayload readFrom(
            final InstanceIdentifierContext path, final InputStream entityStream, final boolean isPost) {
        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final Inference parentSchema;
        if (isPost) {
            parentSchema = path.inference();
        } else {
            final var stack = path.inference().toSchemaInferenceStack();
            if (!stack.isEmpty()) {
                stack.exit();
            }
            parentSchema = stack.toInference();
        }

        final JsonParserStream jsonParser = JsonParserStream.create(writer,
            JSONCodecFactorySupplier.RFC7951.getShared(path.getSchemaContext()), parentSchema);

        final JsonReader reader = new JsonReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8));
        jsonParser.parse(reader);

        NormalizedNode result = resultHolder.getResult().data();
        final List<YangInstanceIdentifier.PathArgument> iiToDataList = new ArrayList<>();

        while (result instanceof ChoiceNode choice) {
            final var childNode = choice.body().iterator().next();
            if (isPost) {
                iiToDataList.add(result.name());
            }
            result = childNode;
        }

        if (isPost) {
            if (result instanceof MapEntryNode) {
                iiToDataList.add(new NodeIdentifier(result.name().getNodeType()));
                iiToDataList.add(result.name());
            } else {
                final var parentPath = parentSchema.statementPath();
                if (parentPath.isEmpty() || !(parentPath.get(parentPath.size() - 1) instanceof OperationDefinition)) {
                    iiToDataList.add(result.name());
                }
            }
        } else if (result instanceof MapNode map) {
            result = Iterables.getOnlyElement(map.body());
        }

        // FIXME: can result really be null?
        return NormalizedNodePayload.ofNullable(path.withConcatenatedArgs(iiToDataList), result);
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
