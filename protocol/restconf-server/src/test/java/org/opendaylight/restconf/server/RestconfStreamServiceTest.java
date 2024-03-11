/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.PathParameters.STREAMS;
import static org.opendaylight.restconf.server.RestconfStreamService.INVALID_STREAM_URI_ERROR;
import static org.opendaylight.restconf.server.RestconfStreamService.MISSING_PARAMS_ERROR;
import static org.opendaylight.restconf.server.RestconfStreamService.UNKNOWN_STREAM_ERROR;
import static org.opendaylight.restconf.server.spi.ErrorTagMapping.RFC8040;

import io.netty.handler.codec.http.QueryStringEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.ErrorResponseException;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.StartTimeParam;
import org.opendaylight.restconf.api.query.StopTimeParam;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@ExtendWith(MockitoExtension.class)
class RestconfStreamServiceTest {
    private static final String RESTS = "rests";
    private static final String URI_PREFIX = "/" + RESTS + STREAMS;
    private static final String URI_TEMPLATE = URI_PREFIX + "/%s/%s";
    private static final String XML = "xml";
    private static final String JSON = "json";
    private static final String VALID_STREAM_NAME = "valid-stream-name";
    private static final String INVALID_STREAM_NAME = "invalid-stream-name";
    private static final String ERROR_MESSAGE = "error-message";
    private static final String ERROR_TYPE_PROTOCOL = ErrorType.PROTOCOL.elementBody();
    private static final ErrorTagMapping ERROR_TAG_MAPPING = RFC8040;
    private static final EventStreamGetParams EMPTY_PARAMS = EventStreamGetParams.of(QueryParameters.of());

    @Mock
    private RestconfStream.Registry registry;
    @Mock
    private RestconfStream<?> stream;
    @Mock
    private EventStreamListener listener;
    @Mock
    private EventStreamService.StartCallback callback;
    @Mock
    private Registration registration;

    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;
    @Captor
    private ArgumentCaptor<EventStreamService.StreamControl> controlCaptor;
    @Captor
    private ArgumentCaptor<EventStreamGetParams> getParamsCaptor;
    @Captor
    private ArgumentCaptor<RestconfStream.Sender> senderCaptor;

    private RestconfStreamService streamService;

    @BeforeEach
    void beforeEach() {
        streamService = new RestconfStreamService(registry, RESTS,
            ERROR_TAG_MAPPING, APPLICATION_JSON, PrettyPrintParam.FALSE);
    }

    @ParameterizedTest
    @MethodSource
    void uriParseFailure(final String uri, final String expectedFormat, final ErrorTag expectedErrorTag,
            final String expectedMessage) {
        streamService.startEventStream(uri, listener, callback);
        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponseException(exceptionCaptor.getValue(), expectedFormat, expectedErrorTag, expectedMessage);
    }

    private static Stream<Arguments> uriParseFailure() {
        return Stream.of(
            // uri, expectedFormat, expectedErrorTag, expectedMessage
            Arguments.of("/", JSON, ErrorTag.DATA_MISSING, INVALID_STREAM_URI_ERROR),
            Arguments.of("/smth", JSON, ErrorTag.DATA_MISSING, INVALID_STREAM_URI_ERROR),
            Arguments.of("/" + RESTS, JSON, ErrorTag.DATA_MISSING, INVALID_STREAM_URI_ERROR),
            Arguments.of(URI_PREFIX, JSON, ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR),
            Arguments.of(URI_PREFIX + "/..", JSON, ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR),
            Arguments.of(URI_PREFIX + "/" + JSON, JSON, ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR),
            Arguments.of(URI_PREFIX + "/" + XML, XML, ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR),
            Arguments.of(URI_PREFIX + "/" + XML + "/", XML, ErrorTag.BAD_ATTRIBUTE, MISSING_PARAMS_ERROR)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {XML, JSON})
    void streamNotFoundFailure(final String encoding) {
        doReturn(null).when(registry).lookupStream(INVALID_STREAM_NAME);
        final var uri = URI_TEMPLATE.formatted(encoding, INVALID_STREAM_NAME);
        streamService.startEventStream(uri, listener, callback);
        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponseException(exceptionCaptor.getValue(), encoding, ErrorTag.DATA_MISSING, UNKNOWN_STREAM_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {XML, JSON})
    void streamSubscribeFailureWithException(final String encoding) throws Exception {
        final var encodingName = new RestconfStream.EncodingName(encoding);
        doReturn(stream).when(registry).lookupStream(VALID_STREAM_NAME);
        doThrow(new IllegalArgumentException(ERROR_MESSAGE))
            .when(stream).addSubscriber(any(RestconfStream.Sender.class), eq(encodingName), eq(EMPTY_PARAMS));

        final var uri = URI_TEMPLATE.formatted(encoding, VALID_STREAM_NAME);
        streamService.startEventStream(uri, listener, callback);
        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponseException(exceptionCaptor.getValue(), encoding, ErrorTag.BAD_ATTRIBUTE, ERROR_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {XML, JSON})
    void streamSubscribeFailureWithoutException(final String encoding) throws Exception {
        final var encodingName = new RestconfStream.EncodingName(encoding);
        doReturn(stream).when(registry).lookupStream(VALID_STREAM_NAME);
        doReturn(null).when(stream).addSubscriber(any(RestconfStream.Sender.class),
            eq(encodingName), eq(EMPTY_PARAMS));

        final var uri = URI_TEMPLATE.formatted(encoding, VALID_STREAM_NAME);
        streamService.startEventStream(uri, listener, callback);
        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponseException(exceptionCaptor.getValue(), encoding, ErrorTag.DATA_MISSING, UNKNOWN_STREAM_ERROR);
    }

    private static void assertErrorResponseException(final Exception caught, final String expectedFormat,
            final ErrorTag expectedErrorTag, final String expectedMessage) {
        final var exception = assertInstanceOf(ErrorResponseException.class, caught);
        final var expectedStatusCode = ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code();
        assertEquals(expectedStatusCode, exception.statusCode());
        final var message = exception.getMessage();

        // simplified encoding validation
        if (JSON.equals(expectedFormat)) {
            assertEquals(MediaTypes.APPLICATION_YANG_DATA_JSON, exception.contentType());
            assertTrue(message.startsWith("{"));
        } else {
            assertEquals(MediaTypes.APPLICATION_YANG_DATA_XML, exception.contentType());
            assertTrue(message.startsWith("<"));
        }
        // simplified message content validation
        final var tagString = expectedErrorTag.elementBody();
        assertTrue(message.indexOf(ERROR_TYPE_PROTOCOL) > 0, "Should contain `error-type` " + ERROR_TYPE_PROTOCOL);
        assertTrue(message.indexOf(tagString) > 0, "Should contain `error-tag` " + tagString);
        assertTrue(message.indexOf(expectedMessage) > 0, "should contain message " + expectedMessage);
    }

    @ParameterizedTest
    @MethodSource
    void subscribeSuccess(final String encoding, final Map<String, List<String>> queryParams) throws Exception {
        final var encodingName = new RestconfStream.EncodingName(encoding);
        doReturn(stream).when(registry).lookupStream(VALID_STREAM_NAME);
        doReturn(registration).when(stream).addSubscriber(any(), eq(encodingName), any(EventStreamGetParams.class));

        // build uri with query parameters
        final var uriEncoder = new QueryStringEncoder(URI_TEMPLATE.formatted(encoding, VALID_STREAM_NAME),
            Charset.defaultCharset());
        queryParams.forEach((name, list) -> {
            if (!list.isEmpty()) {
                uriEncoder.addParam(name, list.get(0));
            }
        });
        streamService.startEventStream(uriEncoder.toString(), listener, callback);
        verify(stream).addSubscriber(senderCaptor.capture(), eq(encodingName), getParamsCaptor.capture());

        // verify params passed
        final var params = getParamsCaptor.getValue();
        assertNotNull(params);
        if (!queryParams.isEmpty()) {
            final var expected = EventStreamGetParams.of(QueryParameters.ofMultiValue(queryParams));
            assertNotNull(params.startTime());
            assertNotNull(params.stopTime());
            assertNotNull(params.skipNotificationData());
            assertEquals(expected.startTime().paramValue(), params.startTime().paramValue());
            assertEquals(expected.stopTime().paramValue(), params.stopTime().paramValue());
            assertEquals(expected.skipNotificationData().paramValue(), params.skipNotificationData().paramValue());
        }

        // verify sender object passed is linked to listener
        final var sender = senderCaptor.getValue();
        assertNotNull(sender);
        sender.sendDataMessage("test");
        verify(listener, times(1)).onEventField("data", "test");
        sender.endOfStream();
        verify(listener, times(1)).onStreamEnd();

        // verify returned control object terminates subscription registration
        verify(callback).onStreamStarted(controlCaptor.capture());
        final var control = controlCaptor.getValue();
        assertNotNull(control);
        control.close();
        verify(registration, times(1)).close();
    }

    private static Stream<Arguments> subscribeSuccess() {
        return Stream.of(
            Arguments.of(JSON, Map.of()),
            Arguments.of(XML, Map.of(
                StartTimeParam.uriName, List.of("2024-08-05T09:00:00Z"),
                StopTimeParam.uriName, List.of("2024-08-05T18:00:00Z"),
                SkipNotificationDataParam.uriName, List.of("true")))
        );
    }
}
