/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.TestUtils.formattableBody;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpMethod;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;

class PipeliningTest extends AbstractRequestProcessorTest {

    @Test
    void testPipeliningQueue() throws InterruptedException {
        final ConfigurationMetadata.EntityTag entityTag =
            new ConfigurationMetadata.EntityTag(Long.toHexString(System.currentTimeMillis()), true);
        final var result = new DataGetResult(
            formattableBody(TestUtils.TestEncoding.JSON, JSON_CONTENT), entityTag, Instant.now());
        doAnswer(answerWithDelay(result)).when(server).dataGET(any());

        var mockEventLoop = mock(EventLoop.class);

        doAnswer(invocation -> {
            final Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mockEventLoop).execute(any());

        when(ctx.executor()).thenReturn(mockEventLoop);

        final var request1 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request2 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request3 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);

        dispatchWithAlloc(request1);
        dispatch(request2);
        dispatch(request3);

        // We expect 2 of requests are in queue until first one is finished
        assertEquals(2, blockedRequestsSize());
        // After short delay we expect that all requests were addressed and queue is empty
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> blockedRequestsSize() == 0);
    }

    private static Answer<Void> answerWithDelay(final DataGetResult result) {
        return invocation -> {
            final var request = invocation.<ServerRequest<DataGetResult>>getArgument(0);
            CompletableFuture.runAsync(
                () -> request.completeWith(result),
                CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS)
            );
            return null;
        };
    }
}
