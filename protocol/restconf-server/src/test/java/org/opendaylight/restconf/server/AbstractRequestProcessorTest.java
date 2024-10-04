/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.TestUtils.ERROR_TAG_MAPPING;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpScheme;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.RestconfServer;

@ExtendWith(MockitoExtension.class)
class AbstractRequestProcessorTest {
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    protected static final String BASE_PATH = "/rests";
    protected static final String HOST = "somehost:1234";
    protected static final URI BASE_URI = URI.create("http://" + HOST + BASE_PATH);
    protected static final String DATA_PATH = BASE_PATH + "/data";
    protected static final String ID_PATH = "test-model:root";
    protected static final String NEW_ID_PATH = "test-model:new";
    protected static final String MOUNT_PATH = "test-model:root/sub/tree/mount:point";
    protected static final TestEncoding DEFAULT_ENCODING = TestEncoding.JSON;
    protected static final String XML_CONTENT = "xml-content";
    protected static final String JSON_CONTENT = "json-content";

    protected static final ApiPath API_PATH;
    protected static final ApiPath NEW_API_PATH;
    protected static final ApiPath MOUNT_API_PATH;

    static {
        try {
            API_PATH = ApiPath.parse(ID_PATH);
            NEW_API_PATH = ApiPath.parse(NEW_ID_PATH);
            MOUNT_API_PATH = ApiPath.parse(MOUNT_PATH);
        } catch (ParseException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final WellKnownResources WELL_KNOWN = new WellKnownResources(BASE_PATH);

    @Mock
    protected RestconfServer server;
    @Mock
    private PrincipalService principalService;
    @Mock
    private ChannelHandlerContext ctx;
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

    private RestconfSession session;

    @BeforeEach
    void beforeEach() {
        session = new RestconfSession(HttpScheme.HTTP,
            new EndpointRoot(principalService, WELL_KNOWN, BASE_PATH.substring(1),
                new APIResource(server, List.of(), "/rests/", ERROR_TAG_MAPPING, MessageEncoding.JSON, PRETTY_PRINT)));
        doReturn(null).when(principalService).acquirePrincipal(any());
    }

    protected final FullHttpResponse dispatch(final FullHttpRequest request) {
        session.channelRead0(ctx, request);
        verify(ctx).writeAndFlush(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    protected static final List<Arguments> encodings() {
        return List.of(
            Arguments.of(TestEncoding.JSON, JSON_CONTENT),
            Arguments.of(TestEncoding.XML, XML_CONTENT)
        );
    }
}
