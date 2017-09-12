/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.restconf.api.JSONRestconfService;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.restful.services.api.TransactionServicesWrapper;
import org.opendaylight.restconf.restful.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
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
public class JSONRestconfServiceDraft18 implements JSONRestconfService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JSONRestconfServiceDraft18.class);

    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    private final TransactionServicesWrapper services;
    private final DOMMountPointServiceHandler mountPointServiceHandler;

    public JSONRestconfServiceDraft18(final TransactionServicesWrapper services,
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
            NormalizedNodeContext readData = (NormalizedNodeContext) response.getEntity();

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

            NormalizedNodeContext outputContext = services.invokeRpc(uriPath, inputContext, new SimpleUriInfo(uriPath));

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
                uriPath, ControllerContext.getInstance().getGlobalSchema(),
                Optional.of(mountPointServiceHandler.get()));

        if (payload == null) {
            return new NormalizedNodeContext(instanceIdentifierContext, null);
        }

        final InputStream entityStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        try {
            return JsonNormalizedNodeBodyReader.readFrom(instanceIdentifierContext, entityStream, isPost);
        } catch (IOException e) {
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
}
