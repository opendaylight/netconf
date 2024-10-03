/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * This is a ugly answer that uses deep reflection on ConsumableBody.
 *
 * @deprecated This code should never have been written, but alas, some people cannot be bothered with appropriate test
 *             payloads. Thus they resolve to use deep reflection and muck in internals of a class to check if the crud
 *             they put in is still the same.
 *
 *             All users of this class need to be migrated to use proper assertions.
 */
@Deprecated(since = "8.0.3", forRemoval = true)
final class FuglyRestconfServerAnswer implements Answer<Void> {
    private static final Method INPUT_STREAM_METHOD;

    static {
        try {
            INPUT_STREAM_METHOD = ConsumableBody.class.getDeclaredMethod("consume");
            INPUT_STREAM_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Class<? extends ConsumableBody> bodyClass;
    private final Object result;
    private final int index;

    private byte[] bytes;

    FuglyRestconfServerAnswer(final Class<? extends ConsumableBody> bodyClass, final int index, final Object result) {
        this.bodyClass = requireNonNull(bodyClass);
        this.result = requireNonNull(result);
        this.index = index;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Void answer(final InvocationOnMock invocation) {
        final var body = invocation.getArgument(index, bodyClass);
        try (var is = (InputStream) INPUT_STREAM_METHOD.invoke(body)) {
            bytes = is.readAllBytes();
        } catch (ReflectiveOperationException | IOException e) {
            throw new AssertionError(e);
        }

        // server request is always first arg in RestconfServer
        invocation.getArgument(0, ServerRequest.class).completeWith(result);
        return null;
    }

    void assertContent(final String expected) {
        final var local = bytes;
        assertNotNull(local);
        assertEquals(expected, new String(local, StandardCharsets.UTF_8));
    }
}
