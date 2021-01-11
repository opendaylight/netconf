/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.apache.aries.blueprint.annotation.service.Service;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.JsonToPatchBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.PatchJsonBodyWriter;
import org.opendaylight.netconf.sal.restconf.api.JSONRestconfService;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.util.MultivaluedHashMap;
import org.opendaylight.restconf.common.util.SimpleUriInfo;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the JSONRestconfService interface using the restconf Draft02 implementation.
 *
 * @author Thomas Pantelis
 * @deprecated Replaced by {JSONRestconfServiceRfc8040Impl from restconf-nb-rfc8040
 */
@Singleton
@Deprecated
@Service(classes = JSONRestconfService.class)
public class JSONRestconfServiceImpl implements JSONRestconfService {
    private static final Logger LOG = LoggerFactory.getLogger(JSONRestconfServiceImpl.class);

    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    private final ControllerContext controllerContext;
    private final RestconfService restconfService;

    @Inject
    public JSONRestconfServiceImpl(final ControllerContext controllerContext, final RestconfImpl restconfService) {
        this.controllerContext = controllerContext;
        this.restconfService = restconfService;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void put(final String uriPath, final String payload) throws OperationFailedException {
        requireNonNull(payload, "payload can't be null");

        LOG.debug("put: uriPath: {}, payload: {}", uriPath, payload);

        final InputStream entityStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        final NormalizedNodeContext context = JsonNormalizedNodeBodyReader.readFrom(uriPath, entityStream, false,
                controllerContext);

        LOG.debug("Parsed YangInstanceIdentifier: {}", context.getInstanceIdentifierContext().getInstanceIdentifier());
        LOG.debug("Parsed NormalizedNode: {}", context.getData());

        try {
            restconfService.updateConfigurationData(uriPath, context, new SimpleUriInfo(uriPath));
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "PUT");
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void post(final String uriPath, final String payload) throws OperationFailedException {
        requireNonNull(payload, "payload can't be null");

        LOG.debug("post: uriPath: {}, payload: {}", uriPath, payload);

        final InputStream entityStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        final NormalizedNodeContext context = JsonNormalizedNodeBodyReader.readFrom(uriPath, entityStream, true,
                controllerContext);

        LOG.debug("Parsed YangInstanceIdentifier: {}", context.getInstanceIdentifierContext().getInstanceIdentifier());
        LOG.debug("Parsed NormalizedNode: {}", context.getData());

        try {
            restconfService.createConfigurationData(uriPath, context, new SimpleUriInfo(uriPath));
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "POST");
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void delete(final String uriPath) throws OperationFailedException {
        LOG.debug("delete: uriPath: {}", uriPath);

        try {
            restconfService.deleteConfigurationData(uriPath);
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
            NormalizedNodeContext readData;
            final SimpleUriInfo uriInfo = new SimpleUriInfo(uriPath);
            if (datastoreType == LogicalDatastoreType.CONFIGURATION) {
                readData = restconfService.readConfigurationData(uriPath, uriInfo);
            } else {
                readData = restconfService.readOperationalData(uriPath, uriInfo);
            }

            final Optional<String> result = Optional.of(toJson(readData));

            LOG.debug("get returning: {}", result.get());

            return result;
        } catch (final Exception e) {
            if (!isDataMissing(e)) {
                propagateExceptionAs(uriPath, e, "GET");
            }

            LOG.debug("Data missing - returning absent");
            return Optional.empty();
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "Unrecognised NullableDecl")
    @Override
    public Optional<String> invokeRpc(final String uriPath, final Optional<String> input)
            throws OperationFailedException {
        requireNonNull(uriPath, "uriPath can't be null");

        final String actualInput = input.isPresent() ? input.get() : null;

        LOG.debug("invokeRpc: uriPath: {}, input: {}", uriPath, actualInput);

        String output = null;
        try {
            NormalizedNodeContext outputContext;
            if (actualInput != null) {
                final InputStream entityStream = new ByteArrayInputStream(actualInput.getBytes(StandardCharsets.UTF_8));
                final NormalizedNodeContext inputContext =
                        JsonNormalizedNodeBodyReader.readFrom(uriPath, entityStream, true, controllerContext);

                LOG.debug("Parsed YangInstanceIdentifier: {}", inputContext.getInstanceIdentifierContext()
                        .getInstanceIdentifier());
                LOG.debug("Parsed NormalizedNode: {}", inputContext.getData());

                outputContext = restconfService.invokeRpc(uriPath, inputContext, null);
            } else {
                outputContext = restconfService.invokeRpc(uriPath, null, null);
            }

            if (outputContext.getData() != null) {
                output = toJson(outputContext);
            }
        } catch (final RuntimeException | IOException e) {
            propagateExceptionAs(uriPath, e, "RPC");
        }

        return Optional.ofNullable(output);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Optional<String> patch(final String uriPath, final String payload)
            throws OperationFailedException {

        String output = null;
        requireNonNull(payload, "payload can't be null");

        LOG.debug("patch: uriPath: {}, payload: {}", uriPath, payload);

        final InputStream entityStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));

        JsonToPatchBodyReader jsonToPatchBodyReader = new JsonToPatchBodyReader(controllerContext);
        final PatchContext context = jsonToPatchBodyReader.readFrom(uriPath, entityStream);

        LOG.debug("Parsed YangInstanceIdentifier: {}", context.getInstanceIdentifierContext().getInstanceIdentifier());
        LOG.debug("Parsed NormalizedNode: {}", context.getData());

        try {
            PatchStatusContext patchStatusContext = restconfService
                .patchConfigurationData(context, new SimpleUriInfo(uriPath));
            output = toJson(patchStatusContext);
        } catch (final Exception e) {
            propagateExceptionAs(uriPath, e, "PATCH");
        }
        return Optional.ofNullable(output);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Optional<String> subscribeToStream(final String identifier, final MultivaluedMap<String, String> params)
            throws OperationFailedException {
        //Note: We use http://127.0.0.1 because the Uri parser requires something there though it does nothing
        String uri = new StringBuilder("http://127.0.0.1:8081/restconf/streams/stream/").append(identifier).toString();
        MultivaluedMap queryParams = params != null ? params : new MultivaluedHashMap<String, String>();
        UriInfo uriInfo = new SimpleUriInfo(uri, queryParams);

        String jsonRes = null;
        try {
            NormalizedNodeContext res = restconfService.subscribeToStream(identifier, uriInfo);
            jsonRes = toJson(res);
        } catch (final Exception e) {
            propagateExceptionAs(identifier, e, "RPC");
        }

        return Optional.ofNullable(jsonRes);
    }

    private static String toJson(final PatchStatusContext patchStatusContext) throws IOException {
        final PatchJsonBodyWriter writer = new PatchJsonBodyWriter();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writer.writeTo(patchStatusContext, PatchStatusContext.class, null, EMPTY_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, null, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private static String toJson(final NormalizedNodeContext readData) throws IOException {
        final NormalizedNodeJsonBodyWriter writer = new NormalizedNodeJsonBodyWriter();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writer.writeTo(readData, NormalizedNodeContext.class, null, EMPTY_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, null, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private static boolean isDataMissing(final Exception exception) {
        boolean dataMissing = false;
        if (exception instanceof RestconfDocumentedException) {
            final RestconfDocumentedException rde = (RestconfDocumentedException)exception;
            if (!rde.getErrors().isEmpty()) {
                if (rde.getErrors().get(0).getErrorTag() == ErrorTag.DATA_MISSING) {
                    dataMissing = true;
                }
            }
        }

        return dataMissing;
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
            case TRANSPORT: {
                return ErrorType.TRANSPORT;
            }
            case RPC: {
                return ErrorType.RPC;
            }
            case PROTOCOL: {
                return ErrorType.PROTOCOL;
            }
            default: {
                return ErrorType.APPLICATION;
            }
        }
    }
}
