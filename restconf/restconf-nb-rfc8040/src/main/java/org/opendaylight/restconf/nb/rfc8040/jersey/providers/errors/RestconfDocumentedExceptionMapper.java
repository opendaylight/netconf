/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableUnkeyedListEntryNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableUnkeyedListNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Mapper that is responsible for transformation of thrown {@link RestconfDocumentedException} to errors structure
 *  that is modelled by RESTCONF module (see section 8 of RFC-8040).
 */
@Provider
public final class RestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {
    @VisibleForTesting
    static final MediaType YANG_DATA_JSON_TYPE = MediaType.valueOf(MediaTypes.DATA + RestconfConstants.JSON);
    @VisibleForTesting
    static final MediaType YANG_DATA_XML_TYPE = MediaType.valueOf(MediaTypes.DATA + RestconfConstants.XML);
    @VisibleForTesting
    static final MediaType YANG_PATCH_JSON_TYPE = MediaType.valueOf(MediaTypes.YANG_PATCH + RestconfConstants.JSON);
    @VisibleForTesting
    static final MediaType YANG_PATCH_XML_TYPE = MediaType.valueOf(MediaTypes.YANG_PATCH + RestconfConstants.XML);

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;
    private static final Status DEFAULT_STATUS_CODE = Status.INTERNAL_SERVER_ERROR;
    private static final SchemaPath ERRORS_GROUPING_PATH = SchemaPath.create(true,
            RestconfModule.ERRORS_GROUPING_QNAME);

    @Context
    private HttpHeaders headers;
    private final SchemaContextHandler schemaContextHandler;

    /**
     * Initialization of the exception mapper.
     *
     * @param schemaContextHandler Handler that provides actual schema context.
     */
    public RestconfDocumentedExceptionMapper(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    @SuppressFBWarnings(value = "SLF4J_MANUALLY_PROVIDED_MESSAGE", justification = "In the debug messages "
            + "we don't to have full stack trace - getMessage(..) method provides finer output.")
    public Response toResponse(final RestconfDocumentedException exception) {
        LOG.debug("Starting to map received exception to error response: {}", exception.getMessage());
        final Status responseStatus = getResponseStatusCode(exception);
        if (responseStatus != Response.Status.FORBIDDEN
                && responseStatus.getFamily() == Response.Status.Family.CLIENT_ERROR
                && exception.getErrors().isEmpty()) {
            // there should be at least one error entry for 4xx errors except 409 according to the RFC 8040
            // - creation of WARN log that something went wrong way on the server side
            LOG.warn("Input exception has a family of 4xx but doesn't contain any descriptive errors: {}",
                    exception.getMessage());
        }

        final ContainerNode errorsContainer = buildErrorsContainer(exception);
        final String serializedResponseBody;
        final MediaType responseMediaType = transformToResponseMediaType(getSupportedMediaType());
        if (YANG_DATA_JSON_TYPE.equals(responseMediaType)) {
            serializedResponseBody = serializeErrorsContainerToJson(errorsContainer);
        } else {
            serializedResponseBody = serializeErrorsContainerToXml(errorsContainer);
        }

        final Response preparedResponse = Response.status(responseStatus)
                .type(responseMediaType)
                .entity(serializedResponseBody)
                .build();
        LOG.debug("Exception {} has been successfully mapped to response: {}",
                exception.getMessage(), preparedResponse);
        return preparedResponse;
    }

    /**
     * Filling up of the errors container with data from input {@link RestconfDocumentedException}.
     *
     * @param exception Thrown exception.
     * @return Built errors container.
     */
    private ContainerNode buildErrorsContainer(final RestconfDocumentedException exception) {
        final List<UnkeyedListEntryNode> errorEntries = exception.getErrors().stream()
                .map(this::createErrorEntry)
                .collect(Collectors.toList());
        return ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                        RestconfModule.ERRORS_CONTAINER_QNAME))
                .withChild(ImmutableUnkeyedListNodeBuilder.create()
                        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                                RestconfModule.ERROR_LIST_QNAME))
                        .withValue(errorEntries)
                        .build())
                .build();
    }

    /**
     * Building of one error entry using provided {@link RestconfError}.
     *
     * @param restconfError Error details.
     * @return Built list entry.
     */
    private UnkeyedListEntryNode createErrorEntry(final RestconfError restconfError) {
        // filling in mandatory leafs
        final DataContainerNodeBuilder<NodeIdentifier, UnkeyedListEntryNode> entryBuilder
                = ImmutableUnkeyedListEntryNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(RestconfModule.ERROR_LIST_QNAME))
                .withChild(ImmutableNodes.leafNode(RestconfModule.ERROR_TYPE_QNAME,
                        restconfError.getErrorType().getErrorTypeTag()))
                .withChild(ImmutableNodes.leafNode(RestconfModule.ERROR_TAG_QNAME,
                        restconfError.getErrorTag().getTagValue()));

        // filling in optional fields
        if (restconfError.getErrorMessage() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_MESSAGE_QNAME, restconfError.getErrorMessage()));
        }
        if (restconfError.getErrorAppTag() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_APP_TAG_QNAME, restconfError.getErrorAppTag()));
        }
        if (restconfError.getErrorInfo() != null) {
            // Oddly, error-info is defined as an empty container in the restconf yang. Apparently the
            // intention is for implementors to define their own data content so we'll just treat it as a leaf
            // with string data.
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_INFO_QNAME, restconfError.getErrorInfo()));
        }

        if (restconfError.getErrorPath() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_PATH_QNAME, restconfError.getErrorPath()));
        }
        return entryBuilder.build();
    }

    /**
     * Serialization of the errors container into JSON representation.
     *
     * @param errorsContainer To be serialized errors container.
     * @return JSON representation of the errors container.
     */
    private String serializeErrorsContainerToJson(final ContainerNode errorsContainer) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             OutputStreamWriter streamStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        ) {
            return writeNormalizedNode(errorsContainer, outputStream, new JsonStreamWriterWithDisabledValidation(
                RestconfModule.ERROR_INFO_QNAME, streamStreamWriter, ERRORS_GROUPING_PATH,
                RestconfModule.URI_MODULE, schemaContextHandler));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close some of the output JSON writers", e);
        }
    }

    /**
     * Serialization of the errors container into XML representation.
     *
     * @param errorsContainer To be serialized errors container.
     * @return XML representation of the errors container.
     */
    private String serializeErrorsContainerToXml(final ContainerNode errorsContainer) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            return writeNormalizedNode(errorsContainer, outputStream, new XmlStreamWriterWithDisabledValidation(
                RestconfModule.ERROR_INFO_QNAME, outputStream, ERRORS_GROUPING_PATH, schemaContextHandler));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close some of the output XML writers", e);
        }
    }

    private static String writeNormalizedNode(final NormalizedNode<?, ?> errorsContainer,
            final ByteArrayOutputStream outputStream, final StreamWriterWithDisabledValidation streamWriter) {
        try (NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter)) {
            nnWriter.write(errorsContainer);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write error response body", e);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Deriving of the status code from the thrown exception. At the first step, status code is tried to be read using
     * {@link RestconfDocumentedException#getStatus()}. If it is {@code null}, status code will be derived from status
     * codes appended to error entries (the first that will be found). If there are not any error entries,
     * {@link RestconfDocumentedExceptionMapper#DEFAULT_STATUS_CODE} will be used.
     *
     * @param exception Thrown exception.
     * @return Derived status code.
     */
    private static Status getResponseStatusCode(final RestconfDocumentedException exception) {
        final Status status = exception.getStatus();
        if (status != null) {
            // status code that is specified directly as field in exception has the precedence over error entries
            return status;
        }

        final List<RestconfError> errors = exception.getErrors();
        if (errors.isEmpty()) {
            // if the module, that thrown exception, doesn't specify status code, it is treated as internal
            // server error
            return DEFAULT_STATUS_CODE;
        }

        final Set<Integer> allStatusCodesOfErrorEntries = errors.stream()
                .map(restconfError -> restconfError.getErrorTag().getStatusCode())
                // we would like to preserve iteration order in collected entries - hence usage of LinkedHashSet
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // choosing of the first status code from appended errors, if there are different status codes in error
        // entries, we should create WARN message
        if (allStatusCodesOfErrorEntries.size() > 1) {
            LOG.warn("An unexpected error occurred during translation of exception {} to response: "
                    + "Different status codes have been found in appended error entries: {}. The first error "
                    + "entry status code is chosen for response.", exception, allStatusCodesOfErrorEntries);
        }
        return Status.fromStatusCode(allStatusCodesOfErrorEntries.iterator().next());
    }

    /**
     * Selection of media type that will be used for creation suffix of 'application/yang-data'. Selection criteria
     * is described in RFC 8040, section 7.1. At the first step, accepted media-type is analyzed and only supported
     * media-types are filtered out. If both XML and JSON media-types are accepted, JSON is selected as a default one
     * used in RESTCONF. If accepted-media type is not specified, the media-type used in request is chosen only if it
     * is supported one. If it is not supported or it is not specified, again, the default one (JSON) is selected.
     *
     * @return Media type.
     */
    private MediaType getSupportedMediaType() {
        final Set<MediaType> acceptableAndSupportedMediaTypes = headers.getAcceptableMediaTypes().stream()
                .filter(RestconfDocumentedExceptionMapper::isCompatibleMediaType)
                .collect(Collectors.toSet());
        if (acceptableAndSupportedMediaTypes.size() == 0) {
            // check content type of the request
            final MediaType requestMediaType = headers.getMediaType();
            return requestMediaType == null ? DEFAULT_MEDIA_TYPE
                    : chooseMediaType(Collections.singletonList(requestMediaType)).orElseGet(() -> {
                        LOG.warn("Request doesn't specify accepted media-types and the media-type '{}' used by "
                                + "request is not supported - using of default '{}' media-type.",
                                requestMediaType, DEFAULT_MEDIA_TYPE);
                        return DEFAULT_MEDIA_TYPE;
                    });
        }

        // at first step, fully specified types without any wildcards are considered (for example, application/json)
        final List<MediaType> fullySpecifiedMediaTypes = acceptableAndSupportedMediaTypes.stream()
                .filter(mediaType -> !mediaType.isWildcardType() && !mediaType.isWildcardSubtype())
                .collect(Collectors.toList());
        if (!fullySpecifiedMediaTypes.isEmpty()) {
            return chooseAndCheckMediaType(fullySpecifiedMediaTypes);
        }

        // at the second step, only types with specified subtype are considered (for example, */json)
        final List<MediaType> mediaTypesWithSpecifiedSubtypes = acceptableAndSupportedMediaTypes.stream()
                .filter(mediaType -> !mediaType.isWildcardSubtype())
                .collect(Collectors.toList());
        if (!mediaTypesWithSpecifiedSubtypes.isEmpty()) {
            return chooseAndCheckMediaType(mediaTypesWithSpecifiedSubtypes);
        }

        // at the third step, only types with specified parent are considered (for example, application/*)
        final List<MediaType> mediaTypesWithSpecifiedParent = acceptableAndSupportedMediaTypes.stream()
                .filter(mediaType -> !mediaType.isWildcardType())
                .collect(Collectors.toList());
        if (!mediaTypesWithSpecifiedParent.isEmpty()) {
            return chooseAndCheckMediaType(mediaTypesWithSpecifiedParent);
        }

        // it must be fully-wildcard-ed type - */*
        return DEFAULT_MEDIA_TYPE;
    }

    private static MediaType chooseAndCheckMediaType(final List<MediaType> options) {
        final Optional<MediaType> mediaTypeOpt = chooseMediaType(options);
        checkState(mediaTypeOpt.isPresent());
        return mediaTypeOpt.get();
    }

    /**
     * This method is responsible for choosing of he media type from multiple options. At the first step,
     * JSON-compatible types are considered, then, if there are not any JSON types, XML types are considered. The first
     * compatible media-type is chosen.
     *
     * @param options Supported media types.
     * @return Selected one media type or {@link Optional#empty()} if none of the provided options are compatible with
     *     RESTCONF.
     */
    private static Optional<MediaType> chooseMediaType(final List<MediaType> options) {
        return options.stream()
                .filter(RestconfDocumentedExceptionMapper::isJsonCompatibleMediaType)
                .findFirst()
                .map(Optional::of)
                .orElse(options.stream()
                        .filter(RestconfDocumentedExceptionMapper::isXmlCompatibleMediaType)
                        .findFirst());
    }

    /**
     * Mapping of JSON-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_JSON_TYPE}
     * or XML-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_XML_TYPE}.
     *
     * @param mediaTypeBase Base media type from which the response media-type is built.
     * @return Derived media type.
     */
    private static MediaType transformToResponseMediaType(final MediaType mediaTypeBase) {
        if (isJsonCompatibleMediaType(mediaTypeBase)) {
            return YANG_DATA_JSON_TYPE;
        } else if (isXmlCompatibleMediaType(mediaTypeBase)) {
            return YANG_DATA_XML_TYPE;
        } else {
            throw new IllegalStateException(String.format("Unexpected input media-type %s "
                    + "- it should be JSON/XML compatible type.", mediaTypeBase));
        }
    }

    private static boolean isCompatibleMediaType(final MediaType mediaType) {
        return isJsonCompatibleMediaType(mediaType) || isXmlCompatibleMediaType(mediaType);
    }

    private static boolean isJsonCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(YANG_DATA_JSON_TYPE) || mediaType.isCompatible(YANG_PATCH_JSON_TYPE);
    }

    private static boolean isXmlCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)
                || mediaType.isCompatible(YANG_DATA_XML_TYPE) || mediaType.isCompatible(YANG_PATCH_XML_TYPE);
    }

    /**
     * Used just for testing purposes - simulation of HTTP headers with different accepted types and content type.
     *
     * @param httpHeaders Mocked HTTP headers.
     */
    @VisibleForTesting
    void setHttpHeaders(final HttpHeaders httpHeaders) {
        this.headers = httpHeaders;
    }
}