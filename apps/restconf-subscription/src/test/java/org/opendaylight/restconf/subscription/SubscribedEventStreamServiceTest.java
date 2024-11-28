/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.yang.common.ErrorTag.BAD_ATTRIBUTE;
import static org.opendaylight.yangtools.yang.common.ErrorTag.DATA_MISSING;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.ErrorResponseException;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
public class SubscribedEventStreamServiceTest {
    private static final String SUBSCRIPTIONS_BASE_URI = "/subscriptions";
    private static final String INVALID_URI_ERROR = "Invalid stream URI";
    private static final String MISSING_PARAMS_ERROR = "Stream name is missing";
    private static final String UNKNOWN_STREAM_ERROR = "Requested stream does not exist";

    @Mock
    private SubscriptionStateMachine machine;
    @Mock
    private EventStreamListener listener;
    @Mock
    private EventStreamService.StartCallback callback;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private SubscribedEventStreamService service;

    @BeforeEach
    void setUp() {
        service = new SubscribedEventStreamService(machine, ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE);
    }

    @Test
    void testStartEventStreamInvalidStreamUri() {
        final var invalidUri = "/invalid/path";
        service.startEventStream(invalidUri, listener, callback);

        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponse(exceptionCaptor.getValue(), DATA_MISSING, INVALID_URI_ERROR);
    }

    @Test
    void testStartEventStreamMissingParams() {
        final var missingParamsUri = SUBSCRIPTIONS_BASE_URI ;
        service.startEventStream(missingParamsUri, listener, callback);

        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponse(exceptionCaptor.getValue(), BAD_ATTRIBUTE, MISSING_PARAMS_ERROR);
    }

    @Test
    void testStartEventStreamEmptyParams() {
        final var missingParamsUri = SUBSCRIPTIONS_BASE_URI + "/";
        service.startEventStream(missingParamsUri, listener, callback);

        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponse(exceptionCaptor.getValue(), BAD_ATTRIBUTE, MISSING_PARAMS_ERROR);
    }

    @Test
    void testStartEventStreamExcessParam() {
        final var missingParamsUri = SUBSCRIPTIONS_BASE_URI + "/2147483648/excessParam";
        service.startEventStream(missingParamsUri, listener, callback);

        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponse(exceptionCaptor.getValue(), DATA_MISSING, INVALID_URI_ERROR);
    }

    @Test
    void testStartEventStreamUnknownStream() {
        final var unknownStreamUri = SUBSCRIPTIONS_BASE_URI + "/2147483648";
        when(machine.getSubscriptionSession(Uint32.valueOf(2147483648L))).thenReturn(null);
        service.startEventStream(unknownStreamUri, listener, callback);

        verify(callback).onStartFailure(exceptionCaptor.capture());
        assertErrorResponse(exceptionCaptor.getValue(), DATA_MISSING, UNKNOWN_STREAM_ERROR);
    }

    @Test
    void testStartEventStreamSuccess() {
        final var validUri = SUBSCRIPTIONS_BASE_URI + "/2147483648";
        when(machine.getSubscriptionSession(Uint32.valueOf(2147483648L))).thenReturn(
            registration -> { });
        service.startEventStream(validUri, listener, callback);

        verify(callback).onStreamStarted(any());
        verify(callback, never()).onStartFailure(any());
    }

    private void assertErrorResponse(Exception exception, ErrorTag expectedTag, String expectedMessage) {
        assertEquals(ErrorResponseException.class, exception.getClass());
        final var errorResponse = (ErrorResponseException) exception;

        assertTrue(errorResponse.getMessage().contains("<error-message>" + expectedMessage + "</error-message>"));
        assertTrue(errorResponse.getMessage().contains("<error-tag>" + expectedTag + "</error-tag>"));

    }
}
