/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;
import static org.opendaylight.restconf.server.ProcessorTestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.ProcessorTestUtils.formattableBody;
import static org.opendaylight.restconf.server.spi.ErrorTagMapping.ERRATA_5565;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.sql.Date;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.ProcessorTestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.ConfigurationMetadata.EntityTag;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;

@ExtendWith(MockitoExtension.class)
public class YangRequestProcessorTest {
    private static final String RESTS = "rests";
    private static final String YANG_LIBRARY_VERSION_PATH = "/" + RESTS + YANG_LIBRARY_VERSION;
    private static final String XML_CONTENT = "xml-content";
    private static final String JSON_CONTENT = "json-content";
    private static final EntityTag ETAG = new EntityTag(Long.toHexString(System.currentTimeMillis()), true);
    private static final Instant LAST_MODIFIED = Instant.now();
    private static final Map<CharSequence, Object> META_HEADERS = Map.of(
        HttpHeaderNames.ETAG, ETAG.value(),
        HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date.from(LAST_MODIFIED))
    );
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    @Mock
    RestconfServer service;
    @Mock
    PrincipalService principalService;
    @Mock
    FutureCallback<FullHttpResponse> callback;

    @Captor
    ArgumentCaptor<FullHttpResponse> responseCaptor;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new RestconfRequestDispatcher(service, principalService,
            RESTS, ERRATA_5565, APPLICATION_JSON, PRETTY_PRINT);
    }

    private static Stream<Arguments> encodings() {
        return Stream.of(
            Arguments.of(TestEncoding.JSON, JSON_CONTENT),
            Arguments.of(TestEncoding.XML, XML_CONTENT)
        );
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void getYangLibraryVersionTest(final TestEncoding encoding, final String content) {
        final var result = new DataGetResult(formattableBody(encoding, content), ETAG, LAST_MODIFIED);
        doAnswer(answerCompleteWith(result)).when(service).dataGET(any());

        final var request = buildRequest(HttpMethod.GET, YANG_LIBRARY_VERSION_PATH, encoding, null);
        final var response = dispatch(request);

        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE));
        assertResponseHeaders(response, META_HEADERS);
    }

    private FullHttpResponse dispatch(final FullHttpRequest request) {
        dispatcher.dispatch(request, callback);
        verify(callback).onSuccess(responseCaptor.capture());
        return responseCaptor.getValue();
    }
}
