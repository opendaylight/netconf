/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.JSONRestconfService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.TransactionServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the JSONRestconfService interface using the restconf Draft18 implementation.
 *
 * @author Thomas Pantelis
 */
public class JSONRestconfServiceRfc8040Impl implements JSONRestconfService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JSONRestconfServiceRfc8040Impl.class);

    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    private final TransactionServicesWrapper services;
    private final DOMMountPointServiceHandler mountPointServiceHandler;

    public JSONRestconfServiceRfc8040Impl(final TransactionServicesWrapper services,
            final DOMMountPointServiceHandler mountPointServiceHandler) {
        this.services = services;
        this.mountPointServiceHandler = mountPointServiceHandler;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void put(final String uriPath, final String payload) throws OperationFailedException {
        Preconditions.checkNotNull(payload, "payload can't be null");

        LOG.debug("put: uriPath: {}, payload: {}", uriPath, payload);

        final NormalizedNodeContext context = toNormalizedNodeContext(uriPath, payload, false);

        LOG.debug("Parsed YangInstanceIdentifier: {}", context.getInstanceIdentifierContext().getInstanceIdentifier());
        LOG.debug("Parsed NormalizedNode: {}", context.getData());

        try {
            services.putData(uriPath, context, new SimpleUriInfo(uriPath));
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "PUT");
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void post(final String uriPath, final String payload)
            throws OperationFailedException {
        Preconditions.checkNotNull(payload, "payload can't be null");

        LOG.debug("post: uriPath: {}, payload: {}", uriPath, payload);

        final NormalizedNodeContext context = toNormalizedNodeContext(uriPath, payload, true);

        LOG.debug("Parsed YangInstanceIdentifier: {}", context.getInstanceIdentifierContext().getInstanceIdentifier());
        LOG.debug("Parsed NormalizedNode: {}", context.getData());

        try {
            services.postData(uriPath, context, new SimpleUriInfo(uriPath));
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "POST");
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void delete(final String uriPath) throws OperationFailedException {
        LOG.debug("delete: uriPath: {}", uriPath);

        try {
            services.deleteData(uriPath);
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "DELETE");
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Optional<String> get(final String uriPath, final LogicalDatastoreType datastoreType)
            throws OperationFailedException {
        LOG.debug("get: uriPath: {}", uriPath);

        try {
            final MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
            queryParams.putSingle(RestconfDataServiceConstant.ReadData.CONTENT,
                    datastoreType == LogicalDatastoreType.CONFIGURATION ? RestconfDataServiceConstant.ReadData.CONFIG :
                        RestconfDataServiceConstant.ReadData.NONCONFIG);

            final Response response = services.readData(uriPath, new SimpleUriInfo(uriPath, queryParams));
            final NormalizedNodeContext readData = (NormalizedNodeContext) response.getEntity();

            final Optional<String> result = Optional.of(toJson(readData));

            LOG.debug("get returning: {}", result.get());

            return result;
        } catch (final Exception e) {
            if (!isDataMissing(e)) {
                propagateExceptionAs(uriPath, e, "GET");
            }

            LOG.debug("Data missing - returning absent");
            return Optional.absent();
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Optional<String> invokeRpc(final String uriPath, final Optional<String> input)
            throws OperationFailedException {
        Preconditions.checkNotNull(uriPath, "uriPath can't be null");

        final String actualInput = input.isPresent() ? input.get() : null;

        LOG.debug("invokeRpc: uriPath: {}, input: {}", uriPath, actualInput);

        String output = null;
        try {
            final NormalizedNodeContext inputContext = toNormalizedNodeContext(uriPath, actualInput, true);

            LOG.debug("Parsed YangInstanceIdentifier: {}", inputContext.getInstanceIdentifierContext()
                    .getInstanceIdentifier());
            LOG.debug("Parsed NormalizedNode: {}", inputContext.getData());

            final NormalizedNodeContext outputContext =
                    services.invokeRpc(uriPath, inputContext, new SimpleUriInfo(uriPath));

            if (outputContext.getData() != null) {
                output = toJson(outputContext);
            }
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "RPC");
        }

        return Optional.fromNullable(output);
    }

    @Override
    public void close() {
    }

    private NormalizedNodeContext toNormalizedNodeContext(final String uriPath, @Nullable final String payload,
            final boolean isPost) throws OperationFailedException {
        final InstanceIdentifierContext<?> instanceIdentifierContext = ParserIdentifier.toInstanceIdentifier(
                uriPath, SchemaContextHandler.getActualSchemaContext(),
                Optional.of(mountPointServiceHandler.get()));

        if (payload == null) {
            return new NormalizedNodeContext(instanceIdentifierContext, null);
        }

        final InputStream entityStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        try {
            return JsonNormalizedNodeBodyReader.readFrom(instanceIdentifierContext, entityStream, isPost);
        } catch (final IOException e) {
            propagateExceptionAs(uriPath, e, "GET");
            return null;
        }
    }

    private static String toJson(final NormalizedNodeContext readData) throws IOException {
        final NormalizedNodeJsonBodyWriter writer = new NormalizedNodeJsonBodyWriter();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writer.writeTo(readData, NormalizedNodeContext.class, null, EMPTY_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, null, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isDataMissing(final Exception exception) {
        if (exception instanceof RestconfDocumentedException) {
            final RestconfDocumentedException rde = (RestconfDocumentedException)exception;
            return !rde.getErrors().isEmpty() && rde.getErrors().get(0).getErrorTag() == ErrorTag.DATA_MISSING;
        }

        return false;
    }

    private static void propagateExceptionAs(final String uriPath, final Exception exception, final String operation)
            throws OperationFailedException {
        LOG.debug("Error for uriPath: {}", uriPath, exception);

        if (exception instanceof RestconfDocumentedException) {
            throw new OperationFailedException(String.format(
                    "%s failed for URI %s", operation, uriPath), exception.getCause(),
                    toRpcErrors(((RestconfDocumentedException)exception).getErrors()));
        }

        throw new OperationFailedException(String.format("%s failed for URI %s", operation, uriPath), exception);
    }

    private static RpcError[] toRpcErrors(final List<RestconfError> from) {
        final RpcError[] to = new RpcError[from.size()];
        int index = 0;
        for (final RestconfError e: from) {
            to[index++] = RpcResultBuilder.newError(toRpcErrorType(e.getErrorType()), e.getErrorTag().getTagValue(),
                    e.getErrorMessage());
        }

        return to;
    }

    private static ErrorType toRpcErrorType(final RestconfError.ErrorType errorType) {
        switch (errorType) {
            case TRANSPORT:
                return ErrorType.TRANSPORT;
            case RPC:
                return ErrorType.RPC;
            case PROTOCOL:
                return ErrorType.PROTOCOL;
            default:
                return ErrorType.APPLICATION;
        }
    }

    private static class SimpleUriInfo implements UriInfo {
        private final String path;
        private final MultivaluedMap<String, String> queryParams;

        SimpleUriInfo(final String path) {
            this(path, new MultivaluedHashMap<>());
        }

        SimpleUriInfo(final String path, final MultivaluedMap<String, String> queryParams) {
            this.path = path;
            this.queryParams = queryParams;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getPath(final boolean decode) {
            return path;
        }

        @Override
        public List<PathSegment> getPathSegments() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PathSegment> getPathSegments(final boolean decode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getRequestUri() {
            return URI.create(path);
        }

        @Override
        public UriBuilder getRequestUriBuilder() {
            return UriBuilder.fromUri(getRequestUri());
        }

        @Override
        public URI getAbsolutePath() {
            return getRequestUri();
        }

        @Override
        public UriBuilder getAbsolutePathBuilder() {
            return UriBuilder.fromUri(getAbsolutePath());
        }

        @Override
        public URI getBaseUri() {
            return URI.create("");
        }

        @Override
        public UriBuilder getBaseUriBuilder() {
            return UriBuilder.fromUri(getBaseUri());
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters() {
            return new MultivaluedHashMap<>();
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters(final boolean decode) {
            return getPathParameters();
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters() {
            return queryParams;
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters(final boolean decode) {
            return getQueryParameters();
        }

        @Override
        public List<String> getMatchedURIs() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getMatchedURIs(final boolean decode) {
            return getMatchedURIs();
        }

        @Override
        public List<Object> getMatchedResources() {
            return Collections.emptyList();
        }

        @Override
        public URI resolve(final URI uri) {
            return uri;
        }

        @Override
        public URI relativize(final URI uri) {
            return uri;
        }
    }
}
