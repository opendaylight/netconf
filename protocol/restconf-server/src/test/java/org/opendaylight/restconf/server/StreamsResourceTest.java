/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;

@ExtendWith(MockitoExtension.class)
public class StreamsResourceTest extends AbstractRequestProcessorTest {
    private static final String STREAMS_PATH = BASE_PATH + "/streams/json/urn:uuid:f5c98414-5e3f-4e9a-b532-6231c52c777";

    @Mock
    protected RestconfStream<DataTreeCandidate> stream;
    @Mock
    protected Registration reg;

    @Test
    void testPipeliningQueue() throws Exception {
        mockWriteAndFlush();
        mockExecutor();
        mockSession();
        mockRegistry(stream);
        final var request = buildRequest(HttpMethod.GET, STREAMS_PATH, TestUtils.TestEncoding.JSON, null);
        request.headers().add(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM);

        doReturn(reg).when(stream).addSubscriber(any(), any(), any());
        // Dispatch all requests manually
        assertResponse(dispatch(request), HttpResponseStatus.OK);
    }
}
