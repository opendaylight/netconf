/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;


import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.$YangModuleInfoImpl.qnameOf;

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
import java.util.stream.Stream;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.common.patch.YangPatchDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.Errors;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.errors.Error;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapper that is responsible for transformation of thrown {@link YangPatchDocumentedException} to errors structure
 * that is modelled by RESTCONF module.
 */
public final class YangPatchDocumentedExceptionMapper implements ExceptionMapper<YangPatchDocumentedException> {
    private static final Logger LOG = LoggerFactory.getLogger(YangPatchDocumentedExceptionMapper.class);
    private static final Status DEFAULT_STATUS_CODE = Status.INTERNAL_SERVER_ERROR;
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;
    private static final QName ERROR_TYPE_QNAME = qnameOf("error-type");
    private static final QName ERROR_TAG_QNAME = qnameOf("error-tag");
    private static final QName ERROR_APP_TAG_QNAME = qnameOf("error-app-tag");
    private static final QName ERROR_MESSAGE_QNAME = qnameOf("error-message");
    private static final QName ERROR_PATH_QNAME = qnameOf("error-path");
    static final QName ERROR_INFO_QNAME = qnameOf("error-info");

    private final DatabindProvider databindProvider;

    @Context
    private HttpHeaders headers;

    public YangPatchDocumentedExceptionMapper(DatabindProvider databindProvider) {
        this.databindProvider = databindProvider;
    }

    @Override
    @SuppressFBWarnings(value = "SLF4J_MANUALLY_PROVIDED_MESSAGE", justification = "In the debug messages "
            + "we don't to have full stack trace - getMessage(..) method provides finer output.")
    public Response toResponse(YangPatchDocumentedException exception) {
        LOG.debug("Starting to map received exception to error response: {}", exception.getMessage());
        final String serializedResponseBody;
        final ContainerNode errorsContainer = buildErrorsContainer(exception);
        final Status responseStatus = getResponseStatusCode(exception);
        final MediaType responseMediaType = transformToResponseMediaType(getSupportedMediaType());
        if (MediaTypes.APPLICATION_YANG_DATA_JSON_TYPE.equals(responseMediaType)) {
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
     * Deriving of the status code from the thrown exception. At the first step, status code is tried to be read using
     * {@link YangPatchDocumentedException#status()}. If it is {@code null}, status code will be derived from status
     * codes appended to error entries (the first that will be found). If there are not any error entries,
     * {@link YangPatchDocumentedExceptionMapper#DEFAULT_STATUS_CODE} will be used.
     *
     * @param exception Thrown exception.
     * @return Derived status code.
     */
    private static Status getResponseStatusCode(final YangPatchDocumentedException exception) {
        final Status status = exception.status();
        if (status != null) {
            return status;
        }

        LinkedHashSet<Status> errors = new LinkedHashSet<>();
        for (PatchStatusEntity patchStatusEntity : exception.editCollection()) {
            List<RestconfError> entityErrors = patchStatusEntity.getEditErrors();
            if (entityErrors == null) {
                continue;
            }
            for (RestconfError restconfError : entityErrors) {
                errors.add(ErrorTags.statusOf(restconfError.getErrorTag()));
            }
        }

        if (errors.isEmpty()) {
            return DEFAULT_STATUS_CODE;
        }
        return errors.iterator().next();
    }

    /**
     * Selection of media type that will be used for creation suffix of 'application/yang-data'. Selection criteria
     * is described in RFC 8040, section 7.1. At the first step, accepted media-type is analyzed and only supported
     * media-types are filtered out. If both XML and JSON media-types are accepted, JSON is selected as a default one
     * used in RESTCONF. If accepted-media type is not specified, the media-type used in request is chosen only if it
     * is supported one. If it is not supported, or it is not specified, again, the default one (JSON) is selected.
     *
     * @return Media type.
     */
    private MediaType getSupportedMediaType() {
        final Set<MediaType> acceptableAndSupportedMediaTypes = headers.getAcceptableMediaTypes().stream()
            .filter(YangPatchDocumentedExceptionMapper::isCompatibleMediaType)
            .collect(Collectors.toSet());
        if (acceptableAndSupportedMediaTypes.isEmpty()) {
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
        return chooseMediaType(options).orElseThrow(IllegalStateException::new);
    }

    /**
     * This method is responsible for choosing of the media type from multiple options. At the first step,
     * JSON-compatible types are considered, then, if there are not any JSON types, XML types are considered. The first
     * compatible media-type is chosen.
     *
     * @param options Supported media types.
     * @return Selected one media type or {@link Optional#empty()} if none of the provided options are compatible with
     *     RESTCONF.
     */
    private static Optional<MediaType> chooseMediaType(final List<MediaType> options) {
        return options.stream()
            .filter(YangPatchDocumentedExceptionMapper::isJsonCompatibleMediaType)
            .findFirst()
            .map(Optional::of)
            .orElse(options.stream()
                .filter(YangPatchDocumentedExceptionMapper::isXmlCompatibleMediaType)
                .findFirst());
    }

    /**
     * Mapping of JSON-compatible type to YANG_DATA_JSON_TYPE or XML-compatible type to
     * YANG_DATA_XML_TYPE.
     *
     * @param mediaTypeBase Base media type from which the response media-type is built.
     * @return Derived media type.
     */
    private static MediaType transformToResponseMediaType(final MediaType mediaTypeBase) {
        if (isJsonCompatibleMediaType(mediaTypeBase)) {
            return MediaTypes.APPLICATION_YANG_DATA_JSON_TYPE;
        } else if (isXmlCompatibleMediaType(mediaTypeBase)) {
            return MediaTypes.APPLICATION_YANG_DATA_XML_TYPE;
        } else {
            throw new IllegalStateException(String.format("Unexpected input media-type %s "
                + "- it should be JSON/XML compatible type.", mediaTypeBase));
        }
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
            return writeNormalizedNode(errorsContainer, outputStream,
                new JsonStreamWriterWithDisabledValidation(databindProvider.currentContext(), streamStreamWriter));
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
            return writeNormalizedNode(errorsContainer, outputStream,
                new XmlStreamWriterWithDisabledValidation(databindProvider.currentContext(), outputStream));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close some of the output XML writers", e);
        }
    }

    private static String writeNormalizedNode(final NormalizedNode errorsContainer,
        final ByteArrayOutputStream outputStream, final StreamWriterWithDisabledValidation streamWriter) {
        try (NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter)) {
            nnWriter.write(errorsContainer);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write error response body", e);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Filling up of the errors container with data from input {@link YangPatchDocumentedExceptionMapper}.
     *
     * @param exception Thrown exception.
     * @return Built errors container.
     */
    private static ContainerNode buildErrorsContainer(final YangPatchDocumentedException exception) {
        return Builders.containerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Errors.QNAME))
            .withChild(Builders.unkeyedListBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Error.QNAME))
                .withValue(exception.editCollection().stream()
                    .flatMap(patchStatusEntity -> patchStatusEntity.isOk()
                        ? Stream.empty() : patchStatusEntity.getEditErrors().stream())
                    .map(YangPatchDocumentedExceptionMapper::createErrorEntry)
                    .collect(Collectors.toList()))
                .build())
            .build();
    }

    /**
     * Building of one error entry using provided {@link RestconfError}.
     *
     * @param restconfError Error details.
     * @return Built list entry.
     */
    public static UnkeyedListEntryNode createErrorEntry(final RestconfError restconfError) {
        // filling in mandatory leafs
        final var entryBuilder = Builders.unkeyedListEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Error.QNAME))
            .withChild(ImmutableNodes.leafNode(ERROR_TYPE_QNAME, restconfError.getErrorType().elementBody()))
            .withChild(ImmutableNodes.leafNode(ERROR_TAG_QNAME, restconfError.getErrorTag().elementBody()));

        // filling in optional fields
        if (restconfError.getErrorMessage() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(ERROR_MESSAGE_QNAME, restconfError.getErrorMessage()));
        }
        if (restconfError.getErrorAppTag() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(ERROR_APP_TAG_QNAME, restconfError.getErrorAppTag()));
        }
        if (restconfError.getErrorInfo() != null) {
            // Oddly, error-info is defined as an empty container in the restconf yang. Apparently the
            // intention is for implementors to define their own data content so we'll just treat it as a leaf
            // with string data.
            entryBuilder.withChild(ImmutableNodes.leafNode(ERROR_INFO_QNAME, restconfError.getErrorInfo()));
        }

        if (restconfError.getErrorPath() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(ERROR_PATH_QNAME, restconfError.getErrorPath()));
        }
        return entryBuilder.build();
    }

    private static boolean isCompatibleMediaType(final MediaType mediaType) {
        return isJsonCompatibleMediaType(mediaType) || isXmlCompatibleMediaType(mediaType);
    }

    private static boolean isJsonCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
            || mediaType.isCompatible(MediaTypes.APPLICATION_YANG_DATA_JSON_TYPE)
            || mediaType.isCompatible(MediaTypes.APPLICATION_YANG_PATCH_JSON_TYPE);
    }

    private static boolean isXmlCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)
            || mediaType.isCompatible(MediaTypes.APPLICATION_YANG_DATA_XML_TYPE)
            || mediaType.isCompatible(MediaTypes.APPLICATION_YANG_PATCH_XML_TYPE);
    }
}
