/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ErrorResponseException;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.NettyMediaTypes;
import org.opendaylight.restconf.server.PathParameters;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class SubscribedEventStreamService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(SubscribedEventStreamService.class);

    private static final String INVALID_STREAM_URI_ERROR = "Invalid stream URI";
    private static final String MISSING_PARAMS_ERROR = "Both stream encoding and subscriptionId are required.";
    private static final String UNKNOWN_STREAM_ERROR = "Requested stream does not exist";
    private static final int ERROR_BUF_SIZE = 2048;


    private final SubscriptionStateMachine machine;
    private final String basePath;
    private final ErrorTagMapping errorTagMapping;
    private final PrettyPrintParam defaultPrettyPrint;

    public SubscribedEventStreamService(final SubscriptionStateMachine machine, final String basePath,
            final ErrorTagMapping errorTagMapping, final RestconfStream.EncodingName defaultEncoding,
            final PrettyPrintParam defaultPrettyPrint) {
        this.machine = requireNonNull(machine);
        this.basePath = basePath;
        this.errorTagMapping = errorTagMapping;
        this.defaultPrettyPrint = defaultPrettyPrint;
    }

    @Override
    public void startEventStream(final String requestUri, final EventStreamListener listener,
            final StartCallback callback) {
        LOG.info("Starting subscribed stream at: {}", requestUri);
        final var decoder = new QueryStringDecoder(requestUri);

        final var pathParams = PathParameters.from(decoder.path(), basePath);
        if (!PathParameters.STREAMS.equals(pathParams.apiResource())) {
            callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, INVALID_STREAM_URI_ERROR));
            return;
        }

        final var streamName = pathParams.childIdentifier();
        if (streamName == null || streamName.isEmpty()) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR));
            return;
        }

        try {
            if (machine.getSubscriptionSession(Uint32.valueOf(requestUri)) != null) {
                callback.onStreamStarted(listener::onStreamStart);
            } else {
                callback.onStartFailure(errorResponse(ErrorTag.DATA_MISSING, UNKNOWN_STREAM_ERROR));
            }
        } catch (IllegalArgumentException | NoSuchElementException e) {
            callback.onStartFailure(errorResponse(ErrorTag.BAD_ATTRIBUTE, e.getMessage()));
        }

    }

    private Exception errorResponse(final ErrorTag errorTag, final String errorMessage) {
        final var yangErrorsBody =
            new YangErrorsBody(List.of(new ServerError(ErrorType.PROTOCOL, errorTag, errorMessage)));
        final var statusCode = errorTagMapping.statusOf(errorTag).code();
        try (var out = new ByteArrayOutputStream(ERROR_BUF_SIZE)) {
            yangErrorsBody.formatToXML(defaultPrettyPrint, out);
            return new ErrorResponseException(statusCode, out.toString(StandardCharsets.UTF_8),
                NettyMediaTypes.APPLICATION_YANG_DATA_XML);
        } catch (IOException e) {
            LOG.error("Failure encoding error message", e);
            // return as plain text
            return new IllegalStateException(errorMessage);
        }
    }
}
