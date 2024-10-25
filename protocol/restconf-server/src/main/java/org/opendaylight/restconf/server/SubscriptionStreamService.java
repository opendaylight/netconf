/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.QueryStringDecoder;
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
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SubscriptionStreamService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamService.class);

    static final String INVALID_SUBSCRIPTION_URI_ERROR = "Invalid subscription URI";
    static final String MISSING_PARAMS_ERROR = "Subscription id is required.";
    static final String UNKNOWN_SUBSCRIPTION_ERROR = "Requested subscription does not exist";

    private static final int ERROR_BUF_SIZE = 2048;

    private final RestconfStream.Registry streamRegistry;
    private final String basePath;
    private final ErrorTagMapping errorTagMapping;
    private final RestconfStream.EncodingName defaultEncoding;
    private final PrettyPrintParam defaultPrettyPrint;

    public SubscriptionStreamService(final RestconfStream.Registry registry, final String restconf,
            final ErrorTagMapping errorTagMapping, final MessageEncoding defaultEncoding,
            final PrettyPrintParam defaultPrettyPrint) {
        streamRegistry = requireNonNull(registry);
        basePath = requireNonNull(restconf);
        this.defaultEncoding = defaultEncoding.streamEncodingName();
        this.errorTagMapping = errorTagMapping;
        this.defaultPrettyPrint = defaultPrettyPrint;
    }

    @Override
    public void startEventStream(final @NonNull String requestUri, final @NonNull EventStreamListener listener,
            final @NonNull StartCallback callback) {
        // parse URI.
        // pattern /basePath/subscriptions/subscriptionId
        final var decoder = new QueryStringDecoder(requestUri);
        final var pathParams = PathParameters.from(decoder.path(), basePath);
        // TODO encodings
        if (!PathParameters.SUBSCRIPTIONS.equals(pathParams.apiResource())) {
            callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, INVALID_SUBSCRIPTION_URI_ERROR,
                defaultEncoding));
            return;
        }
        final var subscriptionId = pathParams.childIdentifier();
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR, defaultEncoding));
            return;
        }

        // find subscription by id
        final var stream = streamRegistry.lookupStream(subscriptionId);
        if (stream == null) {
            callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, UNKNOWN_SUBSCRIPTION_ERROR, defaultEncoding));
            return;
        }

        // Try starting subscription via registry stream subscriber
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
            final var registration = stream.addSubscriber(sender, defaultEncoding, streamParams);
            if (registration != null) {
                callback.onStreamStarted(registration::close);
            } else {
                callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, UNKNOWN_SUBSCRIPTION_ERROR,
                    defaultEncoding));
            }
        } catch (UnsupportedEncodingException | XPathExpressionException | IllegalArgumentException e) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, e.getMessage(), defaultEncoding));
        }
    }

    private Exception errorResponse(final ErrorTag errorTag, final String errorMessage,
            final RestconfStream.EncodingName encoding) {
        final var yangErrorsBody =
            new YangErrorsBody(List.of(new ServerError(ErrorType.PROTOCOL, errorTag, errorMessage)));
        final var statusCode = errorTagMapping.statusOf(errorTag).code();
        try (var out = new ByteArrayOutputStream(ERROR_BUF_SIZE)) {
            if (RestconfStream.EncodingName.RFC8040_JSON.equals(encoding)) {
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
