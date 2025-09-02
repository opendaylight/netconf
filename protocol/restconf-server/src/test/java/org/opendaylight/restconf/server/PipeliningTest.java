/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.TestUtils.formattableBody;

import io.netty.handler.codec.http.HttpMethod;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;

public class PipeliningTest extends AbstractRequestProcessorTest {
    @Test
    void testPipeliningQueue() throws InterruptedException {
        final ConfigurationMetadata.EntityTag entityTag =
            new ConfigurationMetadata.EntityTag(Long.toHexString(System.currentTimeMillis()), true);
        final var result = new DataGetResult(
            formattableBody(TestUtils.TestEncoding.JSON, JSON_CONTENT), entityTag, Instant.now());
        doAnswer(answerWithDelay(result)).when(server).dataGET(any());

        final var request1 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request2 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request3 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);

        dispatchWithAlloc(request1);
        dispatch(request2);
        dispatch(request3);

        // We expect 2 of requests are in queue until first one is finished
        assertEquals(2, blockedRequestsSize());
        Thread.sleep(500);
        // After short delay we expect that all requests were addressed and queue is empty
        assertEquals(0, blockedRequestsSize());
    }

    static Answer<Void> answerWithDelay(final DataGetResult result) {
        return invocation -> {
            Thread.sleep(100);
            invocation.<ServerRequest<DataGetResult>>getArgument(0).completeWith(result);
            return null;
        };
    }
}
