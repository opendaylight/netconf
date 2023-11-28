/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.$YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
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
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.jaxrs.JaxRsMediaTypes;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.Errors;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.errors.errors.Error;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
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
public final class RestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;
    private static final Status DEFAULT_STATUS_CODE = Status.INTERNAL_SERVER_ERROR;
    private static final QName ERROR_TYPE_QNAME = qnameOf("error-type");
    private static final QName ERROR_TAG_QNAME = qnameOf("error-tag");
    private static final QName ERROR_APP_TAG_QNAME = qnameOf("error-app-tag");
    private static final QName ERROR_MESSAGE_QNAME = qnameOf("error-message");
    private static final QName ERROR_INFO_QNAME = qnameOf("error-info");
    private static final QName ERROR_PATH_QNAME = qnameOf("error-path");
    private static final XMLOutputFactory XML_OUTPUT_FACTORY = createXmlOutputFactory();

    private static final int DEFAULT_INDENT_SPACES_NUM = 2;

    private final DatabindProvider databindProvider;

    @Context
    private HttpHeaders headers;

    /**
     * Initialization of the exception mapper.
     *
     * @param databindProvider A {@link DatabindProvider}
     */
    public RestconfDocumentedExceptionMapper(final DatabindProvider databindProvider) {
        this.databindProvider = requireNonNull(databindProvider);
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
            // There should be at least one error entry for 4xx errors except 409 according to RFC8040, but we do not
            // have it. Issue a warning with the call trace so we can fix whoever was the originator.
            LOG.warn("Input exception has a family of 4xx but does not contain any descriptive errors", exception);
        }

        final String serializedResponseBody;
        final MediaType responseMediaType = transformToResponseMediaType(getSupportedMediaType());
        if (JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON.equals(responseMediaType)) {
            serializedResponseBody = serializeExceptionToJson(exception, databindProvider);
        } else {
            serializedResponseBody = serializeExceptionToXml(exception, databindProvider);
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
     * Serialization exceptions into JSON representation.
     *
     * @param exception To be serialized exception.
     * @param databindProvider Holder of current {@code DatabindContext}.
     * @return JSON representation of the exception.
     */
    private static String serializeExceptionToJson(final RestconfDocumentedException exception,
            final DatabindProvider databindProvider) {
        try (var outputStream = new ByteArrayOutputStream();
             var streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             var jsonWriter = JsonWriterFactory.createJsonWriter(streamWriter, DEFAULT_INDENT_SPACES_NUM)) {
            final var currentDatabindContext = exception.modelContext() != null
                ? DatabindContext.ofModel(exception.modelContext()) : databindProvider.currentDatabind();
            jsonWriter.beginObject();
            final var errors = exception.getErrors();
            if (errors != null && !errors.isEmpty()) {
                jsonWriter.name(Errors.QNAME.getLocalName()).beginObject();
                jsonWriter.name(Error.QNAME.getLocalName()).beginArray();
                for (final var error : errors) {
                    jsonWriter.beginObject()
                        .name(ERROR_TAG_QNAME.getLocalName()).value(error.getErrorTag().elementBody());
                    final var errorAppTag = error.getErrorAppTag();
                    if (errorAppTag != null) {
                        jsonWriter.name(ERROR_APP_TAG_QNAME.getLocalName()).value(errorAppTag);
                    }
                    final var errorInfo = error.getErrorInfo();
                    if (errorInfo != null) {
                        jsonWriter.name(ERROR_INFO_QNAME.getLocalName()).value(errorInfo);
                    }
                    final var errorMessage = error.getErrorMessage();
                    if (errorMessage != null) {
                        jsonWriter.name(ERROR_MESSAGE_QNAME.getLocalName()).value(errorMessage);
                    }
                    final var errorPath = error.getErrorPath();
                    if (errorPath != null) {
                        jsonWriter.name(ERROR_PATH_QNAME.getLocalName());
                        currentDatabindContext.jsonCodecs().instanceIdentifierCodec()
                            .writeValue(jsonWriter, errorPath);
                    }
                    jsonWriter.name(ERROR_TYPE_QNAME.getLocalName()).value(error.getErrorType().elementBody());
                    jsonWriter.endObject();
                }
                jsonWriter.endArray().endObject();
            }
            jsonWriter.endObject();
            streamWriter.flush();
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Error while serializing restconf exception into JSON", e);
        }
    }

    /**
     * Serialization exceptions into XML representation.
     *
     * @param exception To be serialized exception.
     * @param databindProvider Holder of current {@code DatabindContext}.
     * @return XML representation of the exception.
     */
    private static String serializeExceptionToXml(final RestconfDocumentedException exception,
            final DatabindProvider databindProvider) {
        try (var outputStream = new ByteArrayOutputStream()) {
            final var xmlWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(outputStream,
                StandardCharsets.UTF_8.name());
            xmlWriter.writeStartDocument();
            xmlWriter.writeStartElement(Errors.QNAME.getLocalName());
            xmlWriter.writeNamespace("xmlns", Errors.QNAME.getNamespace().toString());
            if (exception.getErrors() != null && !exception.getErrors().isEmpty()) {
                for (final var error : exception.getErrors()) {
                    xmlWriter.writeStartElement(Error.QNAME.getLocalName());
                    // Write error-type element
                    xmlWriter.writeStartElement(ERROR_TYPE_QNAME.getLocalName());
                    xmlWriter.writeCharacters(error.getErrorType().elementBody());
                    xmlWriter.writeEndElement();

                    if (error.getErrorPath() != null) {
                        xmlWriter.writeStartElement(ERROR_PATH_QNAME.getLocalName());
                        databindProvider.currentDatabind().xmlCodecs().instanceIdentifierCodec()
                            .writeValue(xmlWriter, error.getErrorPath());
                        xmlWriter.writeEndElement();
                    }
                    if (error.getErrorMessage() != null) {
                        xmlWriter.writeStartElement(ERROR_MESSAGE_QNAME.getLocalName());
                        xmlWriter.writeCharacters(error.getErrorMessage());
                        xmlWriter.writeEndElement();
                    }

                    // Write error-tag element
                    xmlWriter.writeStartElement(ERROR_TAG_QNAME.getLocalName());
                    xmlWriter.writeCharacters(error.getErrorTag().elementBody());
                    xmlWriter.writeEndElement();

                    if (error.getErrorAppTag() != null) {
                        xmlWriter.writeStartElement(ERROR_APP_TAG_QNAME.getLocalName());
                        xmlWriter.writeCharacters(error.getErrorAppTag());
                        xmlWriter.writeEndElement();
                    }
                    if (error.getErrorInfo() != null) {
                        xmlWriter.writeStartElement(ERROR_INFO_QNAME.getLocalName());
                        xmlWriter.writeCharacters(error.getErrorInfo());
                        xmlWriter.writeEndElement();
                    }
                    xmlWriter.writeEndElement();
                }
            }
            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
            xmlWriter.close();

            final var transformerFactory = TransformerFactory.newInstance();
            final var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            // 2 spaces for indentation
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                String.valueOf(DEFAULT_INDENT_SPACES_NUM));
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final var xmlSource = new StreamSource(new StringReader(outputStream.toString(StandardCharsets.UTF_8)));
            final var stringWriter = new StringWriter();
            final var streamResult = new StreamResult(stringWriter);
            transformer.transform(xmlSource, streamResult);
            return stringWriter.toString();
        } catch (IOException | XMLStreamException | TransformerException e) {
            throw new IllegalStateException("Error while serializing restconf exception into XML", e);
        }
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
        final var errors = exception.getErrors();
        if (errors.isEmpty()) {
            // if the module, that thrown exception, doesn't specify status code, it is treated as internal
            // server error
            return DEFAULT_STATUS_CODE;
        }

        final var allStatusCodesOfErrorEntries = errors.stream()
                .map(restconfError -> ErrorTags.statusOf(restconfError.getErrorTag()))
                // we would like to preserve iteration order in collected entries - hence usage of LinkedHashSet
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // choosing of the first status code from appended errors, if there are different status codes in error
        // entries, we should create WARN message
        if (allStatusCodesOfErrorEntries.size() > 1) {
            LOG.warn("""
                An unexpected error occurred during translation of exception {} to response: Different status codes
                have been found in appended error entries: {}. The first error entry status code is chosen for
                response.""", exception, allStatusCodesOfErrorEntries);
        }
        return allStatusCodesOfErrorEntries.iterator().next();
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

    private static XMLOutputFactory createXmlOutputFactory() {
        final var factory = XMLOutputFactory.newFactory();
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        return factory;
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
