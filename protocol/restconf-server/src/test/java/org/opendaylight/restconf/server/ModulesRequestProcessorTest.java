/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.opendaylight.restconf.server.PathParameters.MODULES;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertOptionsResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.TestUtils.charSource;
import static org.opendaylight.restconf.server.TestUtils.formattableBody;
import static org.opendaylight.restconf.server.TestUtils.newOptionsRequest;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.ModulesGetResult;

class ModulesRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String YANG_LIBRARY_VERSION_URI = BASE_PATH + YANG_LIBRARY_VERSION;
    private static final String MODULES_PATH = BASE_PATH + MODULES + "/";
    private static final String MODULE_FILENAME = "module-filename";
    private static final String MODULE_URI = MODULES_PATH + MODULE_FILENAME;
    private static final String MODULE_URI_WITH_MOUNT = MODULES_PATH + MOUNT_PATH + "/" + MODULE_FILENAME;
    private static final String REVISION_VALUE = "revision-value";
    private static final String REVISION_PARAM = "?revision=" + REVISION_VALUE;
    private static final String YANG_CONTENT = "yang-content";
    private static final String YIN_CONTENT = "yin-content";

    @Mock
    private CharSource source;
    @Mock
    private ByteSource byteSource;

    @ParameterizedTest
    @ValueSource(strings = {YANG_LIBRARY_VERSION_URI, MODULES_PATH, MODULE_URI, MODULE_URI_WITH_MOUNT})
    void options(final String uri) {
        final var request = newOptionsRequest(uri);
        assertOptionsResponse(dispatch(request), "GET, HEAD, OPTIONS");
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void getYangLibraryVersion(final TestEncoding encoding, final String content) {
        mockSession();
        final var result = formattableBody(encoding, content);
        doAnswer(answerCompleteWith(result)).when(server).yangLibraryVersionGET(any());

        final var request = buildRequest(HttpMethod.GET, YANG_LIBRARY_VERSION_URI, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void getModule(final TestEncoding encoding, final String content, final boolean hasRevision) {
        mockSession();
        final var result = new ModulesGetResult(charSource(content));
        final var revision = hasRevision ? REVISION_VALUE : null;
        if (encoding.isYin()) {
            doAnswer(answerCompleteWith(result)).when(server).modulesYinGET(any(), eq(MODULE_FILENAME), eq(revision));
        } else {
            doAnswer(answerCompleteWith(result)).when(server).modulesYangGET(any(), eq(MODULE_FILENAME), eq(revision));
        }

        final var uri = MODULE_URI + (hasRevision ? REVISION_PARAM : "");
        final var request = buildRequest(HttpMethod.GET, uri, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void getModuleWithMountPath(final TestEncoding encoding, final String content, final boolean hasRevision) {
        mockSession();
        final var result = new ModulesGetResult(charSource(content));
        final var revision = hasRevision ? REVISION_VALUE : null;
        if (encoding.isYin()) {
            doAnswer(answerCompleteWith(result)).when(server)
                .modulesYinGET(any(), eq(MOUNT_API_PATH), eq(MODULE_FILENAME), eq(revision));
        } else {
            doAnswer(answerCompleteWith(result)).when(server)
                .modulesYangGET(any(), eq(MOUNT_API_PATH), eq(MODULE_FILENAME), eq(revision));
        }
        final var uri = MODULE_URI_WITH_MOUNT + (hasRevision ? REVISION_PARAM : "");
        final var request = buildRequest(HttpMethod.GET, uri, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    private static Stream<Arguments> modules() {
        return Stream.of(
            // encoding, content, hasRevision
            Arguments.of(TestEncoding.YANG, YANG_CONTENT, true),
            Arguments.of(TestEncoding.YANG, YANG_CONTENT, false),
            Arguments.of(TestEncoding.YIN, YIN_CONTENT, true),
            Arguments.of(TestEncoding.YIN, YIN_CONTENT, false)
        );
    }

    @ParameterizedTest
    @MethodSource("moduleErrorEncodings")
    void noFilenameError(final TestEncoding encoding, final TestEncoding errorEncoding) {
        final var request = buildRequest(HttpMethod.GET, MODULES_PATH, encoding, null);
        final var response = dispatch(request);
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
    }

    @Disabled("Will be disabled until NETCONF-1492 has been resolved")
    @ParameterizedTest
    @MethodSource("moduleErrorEncodings")
    void sourceReadFailure(final TestEncoding encoding, final TestEncoding errorEncoding) throws IOException {
        final var errorMessage = "source-read-failure";
        doReturn(byteSource).when(source).asByteSource(any());
        doThrow(new IOException(errorMessage)).when(byteSource).copyTo(any(OutputStream.class));

        final var result = new ModulesGetResult(source);
        if (encoding.isYin()) {
            doAnswer(answerCompleteWith(result)).when(server).modulesYinGET(any(), eq(MODULE_FILENAME), isNull());
        } else {
            doAnswer(answerCompleteWith(result)).when(server).modulesYangGET(any(), eq(MODULE_FILENAME), isNull());
        }

        final var request = buildRequest(HttpMethod.GET, MODULE_URI, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("source-read-failure", response.content().toString(StandardCharsets.UTF_8));
    }

    private static Stream<Arguments> moduleErrorEncodings() {
        return Stream.of(
            // request accept encoding, error response encoding
            Arguments.of(TestEncoding.YANG, DEFAULT_ENCODING),
            Arguments.of(TestEncoding.YIN, TestEncoding.XML)
        );
    }
}
