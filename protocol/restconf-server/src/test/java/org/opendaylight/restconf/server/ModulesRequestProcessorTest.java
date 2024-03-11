/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.opendaylight.restconf.server.ModulesRequestProcessor.REVISION;
import static org.opendaylight.restconf.server.PathParameters.MODULES;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;
import static org.opendaylight.restconf.server.ProcessorTestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.ProcessorTestUtils.charSource;
import static org.opendaylight.restconf.server.ProcessorTestUtils.formattableBody;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.server.ProcessorTestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.ModulesGetResult;

class ModulesRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String YANG_LIBRARY_VERSION_URI = "/" + RESTS + YANG_LIBRARY_VERSION;
    private static final String MODULES_PATH = "/" + RESTS + MODULES + "/";
    private static final String MODULE_FILENAME = "module-filename";
    private static final String MODULE_URI = MODULES_PATH + MODULE_FILENAME;
    private static final String MODULE_URI_WITH_MOUNT = MODULES_PATH + MOUNT_PATH + "/" + MODULE_FILENAME;
    private static final String REVISION_VALUE = "revision-value";
    private static final String REVISION_PARAM = "?" + REVISION + "=" + REVISION_VALUE;
    private static final String YANG_CONTENT = "yang-content";
    private static final String YIN_CONTENT = "yin-content";

    @ParameterizedTest
    @MethodSource("encodings")
    void getYangLibraryVersion(final TestEncoding encoding, final String content) {
        final var result = formattableBody(encoding, content);
        doAnswer(answerCompleteWith(result)).when(service).yangLibraryVersionGET(any());

        final var request = buildRequest(HttpMethod.GET, YANG_LIBRARY_VERSION_URI, encoding, null);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void getModule(final TestEncoding encoding, final String content, final boolean hasRevision) {
        final var result = new ModulesGetResult(charSource(content));
        final var revision = hasRevision ? REVISION_VALUE : null;
        if (encoding.isYin()) {
            doAnswer(answerCompleteWith(result)).when(service).modulesYinGET(any(), eq(MODULE_FILENAME), eq(revision));
        } else {
            doAnswer(answerCompleteWith(result)).when(service).modulesYangGET(any(), eq(MODULE_FILENAME), eq(revision));
        }

        final var uri = MODULE_URI + (hasRevision ? REVISION_PARAM : "");
        final var request = buildRequest(HttpMethod.GET, uri, encoding, null);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void getModuleWithMountPath(final TestEncoding encoding, final String content, final boolean hasRevision) {
        final var result = new ModulesGetResult(charSource(content));
        final var revision = hasRevision ? REVISION_VALUE : null;
        if (encoding.isYin()) {
            doAnswer(answerCompleteWith(result)).when(service)
                .modulesYinGET(any(), eq(MOUNT_API_PATH), eq(MODULE_FILENAME), eq(revision));
        } else {
            doAnswer(answerCompleteWith(result)).when(service)
                .modulesYangGET(any(), eq(MOUNT_API_PATH), eq(MODULE_FILENAME), eq(revision));
        }

        final var uri = MODULE_URI_WITH_MOUNT + (hasRevision ? REVISION_PARAM : "");
        final var request = buildRequest(HttpMethod.GET, uri, encoding, null);
        final var response = dispatch(request);
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

    // FIXME error cases
}
