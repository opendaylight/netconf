/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.jaxrs.JaxRsMediaTypes;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ExceptionMapper} that is responsible for transformation of thrown {@link RestconfDocumentedException} to
 * {@code errors} structure that is modelled by RESTCONF module (see section 8 of RFC-8040).
 *
 * @see Errors
 */
// FIXME: NETCONF-1188: eliminate the need for this class by having a separate exception which a has a HTTP status and
//                      optionally holds an ErrorsBody -- i.e. the equivalent of Errors, perhaps as NormalizedNode,
//                      with sufficient context to send it to JSON or XML -- very similar to a NormalizedNodePayload
@Deprecated
@Provider
public final class ServerExceptionMapper implements ExceptionMapper<ServerException> {
    private static final Logger LOG = LoggerFactory.getLogger(ServerExceptionMapper.class);
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;

    private final ErrorTagMapping errorTagMapping;

    @Context
    private HttpHeaders headers;

    /**
     * Initialization of the exception mapper.
     *
     * @param databindProvider A {@link DatabindProvider}
     */
    public ServerExceptionMapper(final ErrorTagMapping errorTagMapping) {
        this.errorTagMapping = requireNonNull(errorTagMapping);
    }

    @Override
    @SuppressFBWarnings(value = "SLF4J_MANUALLY_PROVIDED_MESSAGE", justification = "In the debug messages "
            + "we don't to have full stack trace - getMessage(..) method provides finer output.")
    public Response toResponse(final ServerException exception) {
        final var msg = exception.getMessage();
        LOG.debug("Starting to map received exception to error response: {}", msg);

        final var body = new YangErrorsBody(exception.errors());
        final var errors = body.errors();
        final var statusCodes = errors.stream()
            .map(restconfError -> errorTagMapping.statusOf(restconfError.tag()))
            .distinct()
            .toList();
        if (statusCodes.size() > 1) {
            LOG.warn("""
                An unexpected error occurred during translation to response: Different status codes have been found in
                appended error entries: {}. The first error entry status code is chosen for response.""",
                statusCodes, new Throwable());
        }
        final var responseStatus = statusCodes.get(0);

        final var responseMediaType = transformToResponseMediaType(getSupportedMediaType());
        final var baos = new ByteArrayOutputStream();
        try {
            if (JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON.equals(responseMediaType)) {
                body.formatToJSON(PrettyPrintParam.TRUE, baos);
            } else {
                body.formatToXML(PrettyPrintParam.TRUE, baos);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error while serializing yang-errors body", e);
        }

        final var response = Response.status(responseStatus.code(), responseStatus.phrase())
            .type(responseMediaType)
            .entity(baos.toString(StandardCharsets.UTF_8))
            .build();
        LOG.debug("Exception {} has been successfully mapped to response: {}", exception, response);
        return response;
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
        final var acceptableAndSupportedMediaTypes = headers.getAcceptableMediaTypes().stream()
            .filter(ServerExceptionMapper::isCompatibleMediaType)
            .collect(Collectors.toSet());
        if (acceptableAndSupportedMediaTypes.isEmpty()) {
            // check content type of the request
            final MediaType requestMediaType = headers.getMediaType();
            return requestMediaType == null ? DEFAULT_MEDIA_TYPE : chooseMediaType(List.of(requestMediaType))
                .orElseGet(() -> {
                    LOG.warn("""
                        Request doesn't specify accepted media-types and the media-type '{}' used by request is not
                        supported - using of default '{}' media-type.""", requestMediaType, DEFAULT_MEDIA_TYPE);
                    return DEFAULT_MEDIA_TYPE;
                });
        }

        // at first step, fully specified types without any wildcards are considered (for example, application/json)
        final var fullySpecifiedMediaTypes = acceptableAndSupportedMediaTypes.stream()
            .filter(mediaType -> !mediaType.isWildcardType() && !mediaType.isWildcardSubtype())
            .collect(Collectors.toList());
        if (!fullySpecifiedMediaTypes.isEmpty()) {
            return chooseAndCheckMediaType(fullySpecifiedMediaTypes);
        }

        // at the second step, only types with specified subtype are considered (for example, */json)
        final var mediaTypesWithSpecifiedSubtypes = acceptableAndSupportedMediaTypes.stream()
            .filter(mediaType -> !mediaType.isWildcardSubtype())
            .collect(Collectors.toList());
        if (!mediaTypesWithSpecifiedSubtypes.isEmpty()) {
            return chooseAndCheckMediaType(mediaTypesWithSpecifiedSubtypes);
        }

        // at the third step, only types with specified parent are considered (for example, application/*)
        final var mediaTypesWithSpecifiedParent = acceptableAndSupportedMediaTypes.stream()
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
                .filter(ServerExceptionMapper::isJsonCompatibleMediaType)
                .findFirst()
                .map(Optional::of)
                .orElse(options.stream()
                        .filter(ServerExceptionMapper::isXmlCompatibleMediaType)
                        .findFirst());
    }

    /**
     * Mapping of JSON-compatible type to {@link ServerExceptionMapper#YANG_DATA_JSON_TYPE}
     * or XML-compatible type to {@link ServerExceptionMapper#YANG_DATA_XML_TYPE}.
     *
     * @param mediaTypeBase Base media type from which the response media-type is built.
     * @return Derived media type.
     */
    private static MediaType transformToResponseMediaType(final MediaType mediaTypeBase) {
        if (isJsonCompatibleMediaType(mediaTypeBase)) {
            return JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON;
        } else if (isXmlCompatibleMediaType(mediaTypeBase)) {
            return JaxRsMediaTypes.APPLICATION_YANG_DATA_XML;
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
                || mediaType.isCompatible(JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON)
                || mediaType.isCompatible(JaxRsMediaTypes.APPLICATION_YANG_PATCH_JSON);
    }

    private static boolean isXmlCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)
                || mediaType.isCompatible(JaxRsMediaTypes.APPLICATION_YANG_DATA_XML)
                || mediaType.isCompatible(JaxRsMediaTypes.APPLICATION_YANG_PATCH_XML);
    }

    /**
     * Used just for testing purposes - simulation of HTTP headers with different accepted types and content type.
     *
     * @param httpHeaders Mocked HTTP headers.
     */
    @VisibleForTesting
    void setHttpHeaders(final HttpHeaders httpHeaders) {
        headers = httpHeaders;
    }
}
