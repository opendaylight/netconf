/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import javax.ws.rs.ext.ExceptionMapper;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.common.patch.YangPatchDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchStatusBodyWriter;


public final class YangPatchDocumentedExceptionMapper implements ExceptionMapper<YangPatchDocumentedException> {

    private final XmlPatchStatusBodyWriter xmlPatchStatusBodyWriter = new XmlPatchStatusBodyWriter();
    private final JsonPatchStatusBodyWriter jsonPatchStatusBodyWriter = new JsonPatchStatusBodyWriter();
    private final DatabindProvider databindProvider;
    private static final Response.Status DEFAULT_STATUS_CODE = Response.Status.INTERNAL_SERVER_ERROR;
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;

    @Context
    private HttpHeaders headers;

    /**
     * Initialization of the exception mapper.
     *
     * @param databindProvider A {@link DatabindProvider}
     */
    public YangPatchDocumentedExceptionMapper(final DatabindProvider databindProvider) {
        this.databindProvider = requireNonNull(databindProvider);
    }

    @Override
    public Response toResponse(YangPatchDocumentedException exception) {
        final Response.Status responseStatus = getResponseStatusCode(exception);
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

        return Response.status(responseStatus)
            .type(responseMediaType)
            .entity(outputStream.toString())
            .build();
    }

    private static Response.Status getResponseStatusCode(final YangPatchDocumentedException exception) {
        final Response.Status status = exception.getStatus();
        if (status != null) {
            return status;
        }

        LinkedHashSet<Response.Status> x = new LinkedHashSet<>();
        final List<PatchStatusEntity> errors = exception.getPatchStatusContext().getEditCollection();

        for (PatchStatusEntity patchStatusEntity : errors) {
            List<RestconfError> errs = patchStatusEntity.getEditErrors();
            if (errs == null){
                continue;
            }
            for (RestconfError restconfError : errs) {
                x.add(ErrorTags.statusOf(restconfError.getErrorTag()));
            }
        }

        if (x.isEmpty()) {

            return DEFAULT_STATUS_CODE;
        }
        return x.iterator().next();
    }


    private MediaType getSupportedMediaType() {
        final Set<MediaType> acceptableAndSupportedMediaTypes = headers.getAcceptableMediaTypes().stream()
            .filter(YangPatchDocumentedExceptionMapper::isCompatibleMediaType)
            .collect(Collectors.toSet());
        if (acceptableAndSupportedMediaTypes.isEmpty()) {
            // check content type of the request
            final MediaType requestMediaType = headers.getMediaType();
            return requestMediaType == null ? DEFAULT_MEDIA_TYPE
                : chooseMediaType(Collections.singletonList(requestMediaType)).orElse(DEFAULT_MEDIA_TYPE);
        }

        final List<MediaType> fullySpecifiedMediaTypes = acceptableAndSupportedMediaTypes.stream()
            .filter(mediaType -> !mediaType.isWildcardType() && !mediaType.isWildcardSubtype())
            .collect(Collectors.toList());
        if (!fullySpecifiedMediaTypes.isEmpty()) {
            return chooseAndCheckMediaType(fullySpecifiedMediaTypes);
        }

        final List<MediaType> mediaTypesWithSpecifiedSubtypes = acceptableAndSupportedMediaTypes.stream()
            .filter(mediaType -> !mediaType.isWildcardSubtype())
            .collect(Collectors.toList());
        if (!mediaTypesWithSpecifiedSubtypes.isEmpty()) {
            return chooseAndCheckMediaType(mediaTypesWithSpecifiedSubtypes);
        }

        final List<MediaType> mediaTypesWithSpecifiedParent = acceptableAndSupportedMediaTypes.stream()
            .filter(mediaType -> !mediaType.isWildcardType())
            .collect(Collectors.toList());
        if (!mediaTypesWithSpecifiedParent.isEmpty()) {
            return chooseAndCheckMediaType(mediaTypesWithSpecifiedParent);
        }

        return DEFAULT_MEDIA_TYPE;
    }

    private static boolean isCompatibleMediaType(final MediaType mediaType) {
        return isJsonCompatibleMediaType(mediaType) || isXmlCompatibleMediaType(mediaType);
    }

    private static MediaType chooseAndCheckMediaType(final List<MediaType> options) {
        return chooseMediaType(options).orElseThrow(IllegalStateException::new);
    }

    private static Optional<MediaType> chooseMediaType(final List<MediaType> options) {
        return options.stream()
            .filter(YangPatchDocumentedExceptionMapper::isJsonCompatibleMediaType)
            .findFirst()
            .map(Optional::of)
            .orElse(options.stream()
                .filter(YangPatchDocumentedExceptionMapper::isXmlCompatibleMediaType)
                .findFirst());
    }

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
