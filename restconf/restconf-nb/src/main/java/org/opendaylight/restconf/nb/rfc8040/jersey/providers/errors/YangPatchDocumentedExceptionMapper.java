/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.common.patch.YangPatchDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchStatusBodyWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Mapper that is responsible for transformation of thrown {@link YangPatchDocumentedException} to errors structure
 *  that is modelled by RESTCONF module.
 */
public final class YangPatchDocumentedExceptionMapper implements ExceptionMapper<YangPatchDocumentedException> {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);
    private static final XmlPatchStatusBodyWriter xmlPatchStatusBodyWriter = new XmlPatchStatusBodyWriter();
    private static final JsonPatchStatusBodyWriter jsonPatchStatusBodyWriter = new JsonPatchStatusBodyWriter();
    private static final Status DEFAULT_STATUS_CODE = Status.INTERNAL_SERVER_ERROR;
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;

    @Context
    private HttpHeaders headers;

    @Override
    @SuppressFBWarnings(value = "SLF4J_MANUALLY_PROVIDED_MESSAGE", justification = "In the debug messages "
        + "we don't to have full stack trace - getMessage(..) method provides finer output.")
    public Response toResponse(YangPatchDocumentedException exception) {
        LOG.debug("Starting to map received exception to error response: {}", exception.getMessage());
        final Status responseStatus = getResponseStatusCode(exception);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final MediaType responseMediaType = transformToResponseMediaType(getSupportedMediaType());
        if (MediaTypes.APPLICATION_YANG_DATA_JSON_TYPE.equals(responseMediaType)) {
            try {
                jsonPatchStatusBodyWriter.writeTo(exception.getPatchStatusContext(), null, null, null,
                    null, null, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                xmlPatchStatusBodyWriter.writeTo(exception.getPatchStatusContext(), null, null, null,
                    null, null, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        final Response preparedResponse = Response.status(responseStatus)
            .type(responseMediaType)
            .entity(outputStream.toString(StandardCharsets.UTF_8))
            .build();
        LOG.debug("Exception {} has been successfully mapped to response: {}",
            exception.getMessage(), preparedResponse);
        return preparedResponse;
    }
    /**
     * Deriving of the status code from the thrown exception. At the first step, status code is tried to be read using
     * {@link YangPatchDocumentedException#getStatus()}. If it is {@code null}, status code will be derived from status
     * codes appended to error entries (the first that will be found). If there are not any error entries,
     * {@link YangPatchDocumentedExceptionMapper#DEFAULT_STATUS_CODE} will be used.
     *
     * @param exception Thrown exception.
     * @return Derived status code.
     */
    private static Status getResponseStatusCode(final YangPatchDocumentedException exception) {
        final Status status = exception.getStatus();
        if (status != null) {
            return status;
        }

        LinkedHashSet<Status> errors = new LinkedHashSet<>();
        for (PatchStatusEntity patchStatusEntity : exception.getPatchStatusContext().editCollection()) {
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
