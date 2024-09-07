/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.spi.RestconfStream.EncodingName.RFC8040_JSON;
import static org.opendaylight.restconf.server.spi.RestconfStream.EncodingName.RFC8040_XML;

import com.google.common.annotations.VisibleForTesting;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.ErrorResponseException;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfStreamService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamService.class);

    @VisibleForTesting
    static final String INVALID_STREAM_URI_ERROR = "Invalid stream URI";
    @VisibleForTesting
    static final String MISSING_PARAMS_ERROR = "Both stream encoding and stream name are required.";
    @VisibleForTesting
    static final String UNKNOWN_STREAM_ERROR = "Requested stream does not exist";

    private static final int ERROR_BUF_SIZE = 2048;

    private final RestconfStream.Registry streamRegistry;
    private final String basePath;
    private final ErrorTagMapping errorTagMapping;
    private final RestconfStream.EncodingName defaultEncoding;
    private final PrettyPrintParam defaultPrettyPrint;

    public RestconfStreamService(final RestconfStream.Registry registry, final URI baseUri,
            final ErrorTagMapping errorTagMapping, final AsciiString defaultAcceptType,
            final PrettyPrintParam defaultPrettyPrint) {
        streamRegistry = requireNonNull(registry);
        basePath = requireNonNull(baseUri).getPath();
        defaultEncoding = NettyMediaTypes.JSON_TYPES.contains(defaultAcceptType) ? RFC8040_JSON : RFC8040_XML;
        this.errorTagMapping = errorTagMapping;
        this.defaultPrettyPrint = defaultPrettyPrint;
    }

    @Override
    public void startEventStream(final @NonNull String requestUri, final @NonNull EventStreamListener listener,
            final @NonNull StartCallback callback) {
        // parse URI.
        // pattern /basePath/streams/streamEncoding/streamName
        final var decoder = new QueryStringDecoder(requestUri);
        final var pathParams = PathParameters.from(decoder.path(), basePath);
        if (!PathParameters.STREAMS.equals(pathParams.apiResource())) {
            callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, INVALID_STREAM_URI_ERROR, defaultEncoding));
            return;
        }
        final var args = pathParams.childIdentifier().split("/", 2);
        final var streamEncoding = encoding(args[0]);
        final var streamName = args.length > 1 ? args[1] : null;
        if (streamEncoding == null || streamName == null || streamName.isEmpty()) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR,
                streamEncoding == null ? defaultEncoding : streamEncoding));
            return;
        }

        // find stream by name
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, UNKNOWN_STREAM_ERROR, streamEncoding));
            return;
        }

        // Try starting stream via registry stream subscriber
        final var sender = new RestconfStream.Sender() {
            @Override
            public void sendDataMessage(final String data) {
                listener.onEventField("data", data);
            }

            @Override
            public void endOfStream() {
                listener.onStreamEnd();
            }
        };
        final var streamParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(decoder.parameters()));
        try {
            final var registration = stream.addSubscriber(sender, streamEncoding, streamParams);
            if (registration != null) {
                callback.onStreamStarted(registration::close);
            } else {
                callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, UNKNOWN_STREAM_ERROR, streamEncoding));
            }
        } catch (UnsupportedEncodingException | XPathExpressionException | IllegalArgumentException e) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, e.getMessage(), streamEncoding));
        }
    }

    private static RestconfStream.EncodingName encoding(final String encodingName) {
        try {
            return new RestconfStream.EncodingName(encodingName);
        } catch (IllegalArgumentException e) {
            LOG.warn("Stream encoding name '{}' is invalid: {}. Ignored.", encodingName, e.getMessage());
            return null;
        }
    }

    private Exception errorResponse(final ErrorTag errorTag, final String errorMessage,
            final RestconfStream.EncodingName encoding) {
        final var yangErrorsBody =
            new YangErrorsBody(List.of(new ServerError(ErrorType.PROTOCOL, errorTag, errorMessage)));
        final var statusCode = errorTagMapping.statusOf(errorTag).code();
        try (var out = new ByteArrayOutputStream(ERROR_BUF_SIZE)) {
            if (RFC8040_JSON.equals(encoding)) {
                yangErrorsBody.formatToJSON(defaultPrettyPrint, out);
                return new ErrorResponseException(statusCode, out.toString(StandardCharsets.UTF_8),
                    NettyMediaTypes.APPLICATION_YANG_DATA_JSON);
            } else {
                yangErrorsBody.formatToXML(defaultPrettyPrint, out);
                return new ErrorResponseException(statusCode, out.toString(StandardCharsets.UTF_8),
                    NettyMediaTypes.APPLICATION_YANG_DATA_XML);
            }
        } catch (IOException e) {
            LOG.error("Failure encoding error message", e);
            // return as plain text
            return new IllegalStateException(errorMessage);
        }
    }
}
