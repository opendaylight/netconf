/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

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
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
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
@Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, MediaType.APPLICATION_JSON })
public class JsonNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(JsonNormalizedNodeBodyReader.class);

    public JsonNormalizedNodeBodyReader(SchemaContextHandler schemaContextHandler,
            DOMMountPointServiceHandler mountPointServiceHandler) {
        super(schemaContextHandler, mountPointServiceHandler);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected NormalizedNodeContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws WebApplicationException {
        try {
            return readFrom(path, entityStream, isPost());
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null;
        }
    }

    public static NormalizedNodeContext readFrom(
            final InstanceIdentifierContext<?> path, final InputStream entityStream, final boolean isPost) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final SchemaNode parentSchema;
        if (isPost) {
            parentSchema = path.getSchemaNode();
        } else if (path.getSchemaNode() instanceof SchemaContext) {
            parentSchema = path.getSchemaContext();
        } else {
            if (SchemaPath.ROOT.equals(path.getSchemaNode().getPath().getParent())) {
                parentSchema = path.getSchemaContext();
            } else {
                parentSchema = SchemaContextUtil
                        .findDataSchemaNode(path.getSchemaContext(), path.getSchemaNode().getPath().getParent());
            }
        }

        final JsonParserStream jsonParser = JsonParserStream.create(writer,
            JSONCodecFactorySupplier.RFC7951.getShared(path.getSchemaContext()), parentSchema);

        final JsonReader reader = new JsonReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8));
        jsonParser.parse(reader);

        NormalizedNode<?, ?> result = resultHolder.getResult();
        final List<YangInstanceIdentifier.PathArgument> iiToDataList = new ArrayList<>();
        InstanceIdentifierContext<? extends SchemaNode> newIIContext;

        while (result instanceof AugmentationNode || result instanceof ChoiceNode) {
            final Object childNode = ((DataContainerNode<?>) result).getValue().iterator().next();
            if (isPost) {
                iiToDataList.add(result.getIdentifier());
            }
            result = (NormalizedNode<?, ?>) childNode;
        }

        if (isPost) {
            if (result instanceof MapEntryNode) {
                iiToDataList.add(new YangInstanceIdentifier.NodeIdentifier(result.getNodeType()));
                iiToDataList.add(result.getIdentifier());
            } else {
                iiToDataList.add(result.getIdentifier());
            }
        } else {
            if (result instanceof MapNode) {
                result = Iterables.getOnlyElement(((MapNode) result).getValue());
            }
        }

        final YangInstanceIdentifier fullIIToData = YangInstanceIdentifier.create(Iterables.concat(
                path.getInstanceIdentifier().getPathArguments(), iiToDataList));

        newIIContext = new InstanceIdentifierContext<>(fullIIToData, path.getSchemaNode(), path.getMountPoint(),
                path.getSchemaContext());

        return new NormalizedNodeContext(newIIContext, result);
    }

    private static void propagateExceptionAs(final Exception exception) throws RestconfDocumentedException {
        if (exception instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)exception;
        }

        if (exception instanceof ResultAlreadySetException) {
            LOG.debug("Error parsing json input:", exception);

            throw new RestconfDocumentedException("Error parsing json input: Failed to create new parse result data. "
                    + "Are you creating multiple resources/subresources in POST request?", exception);
        }

        LOG.debug("Error parsing json input", exception);

        throw new RestconfDocumentedException("Error parsing input: " + exception.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, exception);
    }
}
