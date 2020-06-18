/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.impl;

import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERRORS_CONTAINER_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_APP_TAG_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_LIST_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_MESSAGE_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_PATH_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_TAG_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_TYPE_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.NAMESPACE;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.REVISION;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class LightyRestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {
    private static final Logger LOG = LoggerFactory.getLogger(LightyRestconfDocumentedExceptionMapper.class);

    private final SchemaContextHandler schemaContextHandler;

    @Context
    private HttpHeaders headers;

    public LightyRestconfDocumentedExceptionMapper(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public Response toResponse(final RestconfDocumentedException exception) {
        final List<RestconfError> restconfErrors = exception.getErrors();
        if (restconfErrors.isEmpty()) {
            return Response.status(exception.getStatus()).build();
        }

        final int restconfErrorHttpStatusCode = restconfErrors.get(0).getErrorTag().getStatusCode();

        final SchemaContext schemaContext = schemaContextHandler.get();

        final Optional<Module> ietfRestconfModule = schemaContext.findModule(
                URI.create(NAMESPACE), REVISION);

        if (!ietfRestconfModule.isPresent()) {
            LOG.warn("Module 'ietf-restconf@2017-01-26' not available.");
            return Response.status(restconfErrorHttpStatusCode).type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(exception.getMessage()).build();
        }

        final Optional<? extends GroupingDefinition> errorsGroupingOpt =
                ietfRestconfModule.get().getGroupings().stream()
                        .filter(grouping -> grouping.getQName().equals(ERRORS_CONTAINER_QNAME))
                        .findFirst();
        if (!errorsGroupingOpt.isPresent()) {
            LOG.warn("Grouping 'errors' from module 'ietf-restconf@2017-01-26' not available.");
            return Response.status(restconfErrorHttpStatusCode).type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(exception.getMessage()).build();
        }

        final ContainerSchemaNode errorsContainerSchemaNode = (ContainerSchemaNode) errorsGroupingOpt.get()
                .findDataChildByName(ERRORS_CONTAINER_QNAME).get();

        if (errorsContainerSchemaNode == null) {
            LOG.warn("Container 'errors' from module 'ietf-restconf@2017-01-26' not available.");
            return Response.status(restconfErrorHttpStatusCode).type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(exception.getMessage()).build();
        }

        final CollectionNodeBuilder<UnkeyedListEntryNode, UnkeyedListNode> errorListBuilder = Builders
                .unkeyedListBuilder().withNodeIdentifier(new NodeIdentifier(ERROR_LIST_QNAME));

        for (final RestconfError error : restconfErrors) {
            errorListBuilder.withChild(createErrorListEntryNode(error));
        }

        final ContainerNode errorsContainerNode = Builders.containerBuilder().withNodeIdentifier(
                new NodeIdentifier(ERRORS_CONTAINER_QNAME)).withChild(errorListBuilder.build()).build();

        final List<MediaType> acceptHeaderMediaTypes = headers.getAcceptableMediaTypes();
        acceptHeaderMediaTypes.remove(MediaType.WILDCARD_TYPE);

        final MediaType mediaType;
        if (acceptHeaderMediaTypes.size() > 0) {
            mediaType = acceptHeaderMediaTypes.get(0);
        } else {
            // If no Accept header was specified in the client request, then use the media type of the request
            // message-body. If no message-body is present, then default to JSON.
            if (headers.getMediaType() != null) {
                mediaType = headers.getMediaType();
            } else {
                mediaType = MediaType.APPLICATION_JSON_TYPE;
            }
        }

        Object responseBody;
        if (mediaType.getSubtype().endsWith("json")) {
            responseBody = SubscribedNotificationsUtil.createJsonResponseBody(errorsContainerNode,
                    errorsContainerSchemaNode.getPath(), schemaContext);
        } else {
            responseBody = SubscribedNotificationsUtil.createXmlResponseBody(errorsContainerNode,
                    errorsContainerSchemaNode.getPath(), schemaContext);
        }

        return Response.status(restconfErrorHttpStatusCode).type(mediaType).entity(responseBody).build();
    }

    private static UnkeyedListEntryNode createErrorListEntryNode(final RestconfError restconfError) {
        final DataContainerNodeBuilder<NodeIdentifier, UnkeyedListEntryNode> errorListEntryBuilder = Builders
                .unkeyedListEntryBuilder().withNodeIdentifier(new NodeIdentifier(ERROR_LIST_QNAME));

        errorListEntryBuilder.withChild(Builders.<String>leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(ERROR_TYPE_QNAME))
                .withValue(restconfError.getErrorType().getErrorTypeTag())
                .build());
        errorListEntryBuilder.withChild(Builders.<String>leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(ERROR_TAG_QNAME))
                .withValue(restconfError.getErrorTag().getTagValue())
                .build());
        errorListEntryBuilder.withChild(Builders.<String>leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(ERROR_MESSAGE_QNAME))
                .withValue(restconfError.getErrorMessage())
                .build());

        if (restconfError.getErrorAppTag() != null) {
            errorListEntryBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(
                    new NodeIdentifier(ERROR_APP_TAG_QNAME)).withValue(restconfError.getErrorAppTag()).build());
        }

        if (restconfError.getErrorPath() != null) {
            errorListEntryBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(
                    new NodeIdentifier(ERROR_PATH_QNAME)).withValue(restconfError.getErrorPath()).build());
        }

        return errorListEntryBuilder.build();
    }
}
