/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.ErrorInfo;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

class ServerExceptionTest {
    @Test
    void stringConstructor() {
        final var ex = new ServerException("some message");
        assertEquals("some message", ex.getMessage());
        assertNull(ex.getCause());
        assertServerError(new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "some message"), ex);
    }

    @Test
    void causeConstructor() {
        final var cause = new Throwable("cause message");
        final var ex = new ServerException(cause);
        assertEquals("java.lang.Throwable: cause message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "cause message"), ex);
    }

    @Test
    void causeConstructorIAE() {
        final var cause = new IllegalArgumentException("cause message");
        final var ex = new ServerException(cause);
        assertEquals("java.lang.IllegalArgumentException: cause message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(new ServerError(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, "cause message"), ex);
    }

    @Test
    void causeConstructorUOE() {
        final var cause = new UnsupportedOperationException("cause message");
        final var ex = new ServerException(cause);
        assertEquals("java.lang.UnsupportedOperationException: cause message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(
            new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, "cause message"),
            ex);
    }

    @Test
    void messageCauseConstructor() {
        final var cause = new Throwable("cause message");
        final var ex = new ServerException("some message", cause);
        assertEquals("some message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(
            new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, new ErrorMessage("some message"), null,
                null, new ErrorInfo("cause message")),
            ex);
    }

    @Test
    void messageCauseConstructorIAE() {
        final var cause = new IllegalArgumentException("cause message");
        final var ex = new ServerException("some message", cause);
        assertEquals("some message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(
            new ServerError(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, new ErrorMessage("some message"), null, null,
                new ErrorInfo("cause message")),
            ex);
    }

    @Test
    void messageCauseConstructorUOE() {
        final var cause = new UnsupportedOperationException("cause message");
        final var ex = new ServerException("some message", cause);
        assertEquals("some message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertServerError(
            new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, new ErrorMessage("some message"),
                null, null, new ErrorInfo("cause message")),
            ex);
    }

    @Test
    void formatConstructor() {
        final var ex = new ServerException("huh %s: %s", 1, "hah");
        assertEquals("huh 1: hah", ex.getMessage());
        assertNull(ex.getCause());
        assertServerError(new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "huh 1: hah"), ex);
    }

    private static void assertServerError(final ServerError expected, final ServerException ex) {
        assertEquals(List.of(expected), ex.errors());
    }
}
