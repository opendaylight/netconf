/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractNormalizedNodeBodyReader extends AbstractIdentifierAwareJaxRsProvider
        implements MessageBodyReader<NormalizedNodeContext> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNormalizedNodeBodyReader.class);

    public final void injectParams(final UriInfo uriInfo, final Request request) {
        setRequest(request);
        setUriInfo(uriInfo);
    }

    @Override
    public final boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return true;
    }


    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public final NormalizedNodeContext readFrom(final Class<NormalizedNodeContext> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream) throws IOException,
            WebApplicationException {
        final InstanceIdentifierContext<?> path = getInstanceIdentifierContext();
        if (entityStream.available() < 1) {
            return new NormalizedNodeContext(path, null);
        }

        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final SchemaNode parentSchema;
        if (isPost()) {
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

        try {
            readStream(entityStream, writer, path.getSchemaContext(), parentSchema);
        } catch (final Exception e) {
            throw propagateExceptionAs(e);
        }


        NormalizedNode<?, ?> result = resultHolder.getResult();
        final List<YangInstanceIdentifier.PathArgument> iiToDataList = new ArrayList<>();

        while (result instanceof AugmentationNode || result instanceof ChoiceNode) {
            final Object childNode = ((DataContainerNode<?>) result).getValue().iterator().next();
            if (isPost()) {
                iiToDataList.add(result.getIdentifier());
            }
            result = (NormalizedNode<?, ?>) childNode;
        }

        if (isPost()) {
            if (result instanceof MapEntryNode) {
                iiToDataList.add(new YangInstanceIdentifier.NodeIdentifier(result.getNodeType()));
                iiToDataList.add(result.getIdentifier());
            } else {
                iiToDataList.add(result.getIdentifier());
            }
        } else if (result instanceof MapNode) {
            result = Iterables.getOnlyElement(((MapNode) result).getValue());
        }

        final YangInstanceIdentifier fullIIToData = YangInstanceIdentifier.create(Iterables.concat(
                path.getInstanceIdentifier().getPathArguments(), iiToDataList));

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(fullIIToData, path.getSchemaNode(),
                path.getMountPoint(), path.getSchemaContext()), result);
    }

    abstract void readStream(final InputStream entityStream, NormalizedNodeStreamWriter writer,
            final SchemaContext schemaContext, final SchemaNode parentSchema) throws Exception;

    private static RuntimeException propagateExceptionAs(final Exception exception) throws RestconfDocumentedException {
        if (exception instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)exception;
        }

        if (exception instanceof ResultAlreadySetException) {
            LOG.debug("Error parsing input", exception);

            throw new RestconfDocumentedException("Error parsing input: Failed to create new parse result data. "
                    + "Are you creating multiple resources/subresources in POST request?");
        }

        LOG.debug("Error parsing input", exception);

        throw new RestconfDocumentedException("Error parsing input: " + exception.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, exception);
    }
}
