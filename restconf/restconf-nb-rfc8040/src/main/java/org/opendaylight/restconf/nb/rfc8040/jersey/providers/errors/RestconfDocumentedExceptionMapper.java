/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Mapper that is responsible for transformation of thrown {@link RestconfDocumentedException} to errors structure
 *  that is modelled by RESTCONF module (see section 8 of RFC-8040).
 */
@Provider
public class RestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;
    private static final MediaType YANG_DATA_JSON_TYPE = MediaType.valueOf("application/yang-data+json");
    private static final MediaType YANG_DATA_XML_TYPE = MediaType.valueOf("application/yang-data+xml");

    @Context
    private HttpHeaders headers;

    @Override
    public Response toResponse(final RestconfDocumentedException exception) {
        LOG.debug("Starting to map received exception to error response: {}", exception.getMessage());
        final Response preparedResponse;
        if (exception.getErrors().isEmpty()) {
            preparedResponse = processExceptionWithoutErrors(exception);
        } else {
            preparedResponse = processExceptionWithErrors(exception);
        }
        LOG.debug("Exception {} has been successfully mapped to response: {}",
                exception.getMessage(), preparedResponse);
        return preparedResponse;
    }

    /**
     * Building of response from exception that doesn't contain any errors in the embedded list.
     *
     * @param exception Exception thrown during processing of HTTP request.
     * @return Built HTTP response.
     */
    private static Response processExceptionWithoutErrors(final RestconfDocumentedException exception) {
        if (!exception.getStatus().equals(Response.Status.FORBIDDEN)
                && exception.getStatus().getFamily().equals(Response.Status.Family.CLIENT_ERROR)) {
            // there should be some error messages, creation of WARN log
            LOG.warn("Input exception has a family of 4xx but doesn't contain any descriptive errors: {}",
                    exception.getMessage());
        }
        // We don't actually want to send any content but, if we don't set any content here, the tomcat front-end
        // will send back an html error report. To prevent that, set a single space char in the entity.
        return Response.status(exception.getStatus())
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(" ")
                .build();
    }

    /**
     * Building of response from exception that contains non-empty list of errors.
     *
     * @param exception Exception thrown during processing of HTTP request.
     * @return Built HTTP response.
     */
    private Response processExceptionWithErrors(final RestconfDocumentedException exception) {
        final ContainerNode errorsContainer = buildErrorsContainer(exception);
        final Object serializedResponseBody;
        final MediaType responseMediaType = buildResponseMediaType(buildResponseMediaType());
        if (responseMediaType.equals(YANG_DATA_JSON_TYPE)) {
            serializedResponseBody = serializeErrorsContainerToJson(errorsContainer);
        } else {
            serializedResponseBody = serializeErrorsContainerToXml(errorsContainer);
        }
        return Response.status(exception.getStatus())
                .type(responseMediaType)
                .entity(serializedResponseBody)
                .build();
    }


    private static ContainerNode buildErrorsContainer(final RestconfDocumentedException exception) {
        final List<MapEntryNode> errorEntries = exception.getErrors().stream()
                .map(restconfError -> createErrorEntry(restconfError))
                .collect(Collectors.toList());
        return ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                        RestconfModule.RESTCONF_CONTAINER_QNAME))
                .withChild(ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                                RestconfModule.ERROR_LIST_QNAME))
                        .withValue(errorEntries)
                        .build())
                .build();
    }

    private static MapEntryNode createErrorEntry(final RestconfError restconfError) {
        return null;
    }

    private static Object serializeErrorsContainerToXml(final ContainerNode errorsContainer) {
        return null;
    }

    private static Object serializeErrorsContainerToJson(final ContainerNode errorsContainer) {
        return null;
    }

    /**
     * Selection of media type that will be used for suffix of 'application/yang-data'. Selection criteria are described
     * in RFC 8040, section 7.1. At the first step, accepted media-type is analyzed and only supported media-types
     * are filtered. If both XML and JSON media-types are accepted, JSON is selected as a default one used in RESTCONF.
     * If accepted-media type is not specified, the media-type used in request is chosen only if it is supported one.
     * If it is not supported or it is not specified at all, again, the default one (JSON) is selected.
     *
     * @return Media type.
     */
    private MediaType buildResponseMediaType() {
        final List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        final List<MediaType> acceptableAndSupportedMediaTypes = acceptableMediaTypes.stream()
                .filter(RestconfDocumentedExceptionMapper::isCompatibleMediaType)
                .collect(Collectors.toList());
        if (acceptableAndSupportedMediaTypes.size() == 0) {
            // check content type of the request
            final MediaType requestMediaType = headers.getMediaType();
            if (isCompatibleMediaType(requestMediaType)) {
                return requestMediaType;
            } else {
                LOG.warn("Request doesn't specify accepted media-types and the media-type '{}' used by request is "
                        + "not supported - using of default '{}' media-type.", requestMediaType, DEFAULT_MEDIA_TYPE);
                return DEFAULT_MEDIA_TYPE;
            }
        } else if (acceptableAndSupportedMediaTypes.size() == 1
                && acceptableAndSupportedMediaTypes.get(0).equals(MediaType.WILDCARD_TYPE)) {
            // choose server-preferred type
            return DEFAULT_MEDIA_TYPE;
        } else if (acceptableAndSupportedMediaTypes.size() == 1) {
            // choose the only-one accepted media type
            return acceptableAndSupportedMediaTypes.get(0);
        } else {
            // choose the server-preferred type
            return DEFAULT_MEDIA_TYPE;
        }
    }

    /**
     * Mapping of JSON-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_JSON_TYPE}
     * or XML-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_XML_TYPE}.
     *
     * @param mediaTypeBase Base media type from which the response media-type is built.
     * @return Derived media type.
     */
    private static MediaType buildResponseMediaType(final MediaType mediaTypeBase) {
        if (mediaTypeBase.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return YANG_DATA_JSON_TYPE;
        } else {
            return YANG_DATA_XML_TYPE;
        }
    }

    private static boolean isCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE);
    }
}