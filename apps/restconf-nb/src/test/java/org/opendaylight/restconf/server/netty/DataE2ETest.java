/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class DataE2ETest extends AbstractE2ETest {

    @Test
    void userAuthenticationTest() {
        // TODO
    }

    @Test
    void crudOperationsTest() throws Exception {
        invokeRequest(AbstractE2ETest.buildRequest(HttpMethod.POST, "/rests/data", TestEncoding.JSON, """
                <content>
                """),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final FullHttpResponse result) {
                    assertEquals(201, result.status().code());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    fail("Failed to create data");
                }
            });

        invokeRequest(AbstractE2ETest.buildRequest(HttpMethod.GET, "/rests/data", TestEncoding.JSON, null),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final FullHttpResponse result) {
                    assertEquals(200, result.status().code());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    fail("Failed to read data");
                }
            });

        invokeRequest(AbstractE2ETest.buildRequest(HttpMethod.PUT, "/rests/data", TestEncoding.JSON, """
                <content>
                """),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final FullHttpResponse result) {
                    assertEquals(201, result.status().code());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    fail("Failed to update data");
                }
            });

        invokeRequest(AbstractE2ETest.buildRequest(HttpMethod.DELETE, "/rests/data", TestEncoding.JSON, null),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final FullHttpResponse result) {
                    assertEquals(200, result.status().code());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    fail("Failed to delete data");
                }
            });
    }

    @Test
    void invokeActionTest() throws Exception {

    }

    @Test
    void dataOptionsTest() throws Exception {

    }

    @Test
    void dataHeadTest() throws Exception {

    }

    @Test
    void errorHandlingTest() throws Exception {
        // TODO
    }
}
