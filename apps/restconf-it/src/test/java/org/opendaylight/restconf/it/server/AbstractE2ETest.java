/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static java.util.stream.Collectors.toSet;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.api.ActionSpec;
import org.opendaylight.mdsal.binding.dom.adapter.BindingAdapterFactory;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionProviderService;
import org.opendaylight.netconf.odl.device.notification.SubscribeDeviceNotificationRpc;
import org.opendaylight.netconf.rfc8639.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.netconf.sal.remote.impl.CreateDataChangeEventSubscriptionRpc;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.EventStreamService.StreamControl;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.restconf.client.impl.ClientHttp1Session;
import org.opendaylight.restconf.client.impl.ClientHttp2Session;
import org.opendaylight.restconf.it.AbstractIT;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.example.action.rev240919.Root;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleAction;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionInput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.generator.impl.DefaultBindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.runtime.api.DefaultBindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.yangtools.dagger.yang.parser.DaggerDefaultYangParserComponent;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractE2ETest extends AbstractIT {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractE2ETest.class);
    private static final BindingRuntimeGenerator GENERATOR = new DefaultBindingRuntimeGenerator();

    protected static final @NonNull YangParserFactory PARSER_FACTORY =
        DaggerDefaultYangParserComponent.create().parserFactory();
    private static final LoadingCache<Set<YangModuleInfo>, BindingRuntimeContext> RUNTIME_CONTEXT_CACHE = CacheBuilder
        .newBuilder().weakValues().build(new CacheLoader<>() {
            public BindingRuntimeContext load(final Set<YangModuleInfo> key) throws Exception {
                final var snapshot = new ModuleInfoSnapshotBuilder(PARSER_FACTORY)
                    .add(key)
                    .addModuleFeatures(IetfSubscribedNotificationsData.class,
                        new IetfSubscriptionFeatureProvider().supportedFeatures())
                    .build();
                return new DefaultBindingRuntimeContext(GENERATOR.generateTypeMapping(snapshot.modelContext()),
                    snapshot);
            }
        });

    private final List<StreamControl> streamControl = new ArrayList<>();

    private HttpClientStackGrouping invalidClientStackGrouping;

    private volatile EventStreamService clientStreamService;

    @Override
    protected BindingRuntimeContext getRuntimeContext() {
        return RUNTIME_CONTEXT_CACHE.getUnchecked(getModuleInfos());
    }

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        invalidClientStackGrouping = new HttpClientStackConfiguration(
            ConfigUtils.clientTransportTcp(localAddress(), port(), USERNAME, "wrong-password"));
        // action implementations
        final var adapterFactory = new BindingAdapterFactory(adapterContext());
        final var actionProviderService = adapterFactory.createActionProviderService(
            new RouterDOMActionProviderService(domRpcRouter()));
        actionProviderService.registerImplementation(
            ActionSpec.builder(Root.class).build(ExampleAction.class), new ExampleActionImpl());
    }

    @Override
    @AfterEach
    protected void afterEach() throws Exception {
        closeAllStreams();
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        super.afterEach();
    }

    /**
     * {@return the invalidClientStackGrouping}
     */
    protected final HttpClientStackGrouping invalidClientStackGrouping() {
        return invalidClientStackGrouping;
    }

    @Override
    protected List<RpcImplementation> rpcImplementations(final DOMDataBroker domDataBroker,
            final MdsalDatabindProvider dataBindProvider) {
        return List.of(
            new CreateDataChangeEventSubscriptionRpc(streamRegistry(), dataBindProvider, domDataBroker),
            new SubscribeDeviceNotificationRpc(streamRegistry(), domMountPointService())
        );
    }

    protected static void assertErrorResponseJson(final FullHttpResponse response, final ErrorType expectedErrorType,
            final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        // {
        //    errors": {
        //       "error": [{
        //             "error-type": "...",
        //             "error-tag": "..."
        //             "error-message": "..."
        //         }]
        //    }
        // }
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8), jsonParserConfiguration());
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }

    protected static void assertErrorResponseXml(final FullHttpResponse response, final ErrorType expectedErrorType,
            final ErrorTag expectedErrorTag) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        assertThat(content, hasXPath("/r:errors/r:error/r:error-message",
            not(emptyOrNullString())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-type",
            equalTo(expectedErrorType.elementBody())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-tag",
            equalTo(expectedErrorTag.elementBody())).withNamespaceContext(NS_CONTEXT));
    }

    // FIXME: Must be eliminated, usage must be replaced with assertErrorResponseJson once
    //  DataE2ETest#invokeActionWithBadInputsTest will return properly formatted response.
    protected static void assertSimpleErrorResponse(final FullHttpResponse response, final String expectedMessage,
        final HttpResponseStatus expectedStatus) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, response.status());
        assertEquals(expectedMessage, content);
    }

    protected void assertOptions(final String uri, final Set<String> methods) throws Exception {
        assertOptionsResponse(invokeRequest(HttpMethod.OPTIONS, uri), methods);
    }

    protected static void assertOptionsResponse(final FullHttpResponse response, final Set<String> methods) {
        assertEquals(HttpResponseStatus.OK, response.status());
        assertHeaderValue(response, HttpHeaderNames.ALLOW, methods);
    }

    protected static void assertHeaderValue(final FullHttpResponse response, final CharSequence headerName,
            final Set<String> expectedValues) {
        final var headerValue = response.headers().get(headerName);
        assertNotNull(headerValue);
        assertEquals(expectedValues, COMMA_SPLITTER.splitToStream(headerValue).collect(toSet()));
    }

    protected void assertHead(final String uri) throws Exception {
        assertHead(uri, APPLICATION_JSON);
        assertHead(uri, APPLICATION_XML);
    }

    protected void assertHead(final String uri, final String mediaType) throws Exception {
        final var getResponse = invokeRequest(HttpMethod.GET, uri, mediaType);
        assertEquals(HttpResponseStatus.OK, getResponse.status());
        assertTrue(getResponse.content().readableBytes() > 0);

        // HEAD response contains same headers as GET but empty body
        final var headResponse = invokeRequest(HttpMethod.HEAD, uri, mediaType);
        assertEquals(HttpResponseStatus.OK, headResponse.status());
        getResponse.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        if (getResponse.headers().contains(HttpHeaderNames.CONNECTION)) {
            assertEquals("close", getResponse.headers().get(HttpHeaderNames.CONNECTION));
            getResponse.headers().remove(HttpHeaderNames.CONNECTION);
        }
        headResponse.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        assertEquals(getResponse.headers(), headResponse.headers());
        assertEquals(0, headResponse.content().readableBytes());
    }

    protected URI getStreamUrlJson(final String streamName) throws Exception {
        // get stream URL from restconf-state
        final var response = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + streamName);
        assertEquals(HttpResponseStatus.OK, response.status());
        return extractStreamUrlJson(response.content().toString(StandardCharsets.UTF_8));
    }

    protected static URI extractStreamUrlJson(final String content) {
        // {
        //      "ietf-restconf-monitoring:stream": [{
        //              "name": "urn:uuid:6413c077-5dfe-464c-b17f-20c5bbb456f4",
        //              "access": [
        //                  { "encoding": "json", "location": "..." },
        //                  { "encoding": "xml", "location": "..."}
        //              ],
        //              "description": "..."
        //      }]
        // }
        final var json = new JSONObject(content, jsonParserConfiguration());
        final var stream = json.getJSONArray("ietf-restconf-monitoring:stream").getJSONObject(0);
        for (var access : stream.getJSONArray("access")) {
            final var accessObj = (JSONObject) access;
            if ("json".equals(accessObj.getString("encoding"))) {
                return URI.create(accessObj.getString("location"));
            }
        }
        return null;
    }

    protected HTTPClient startStreamClient() throws Exception {
        return startStreamClient(false);
    }

    protected HTTPClient startStreamClient(final boolean http2) throws Exception {
        final var transportListener = new TestTransportChannelListener(channel -> {
            final ChannelHandler session;
            if (http2) {
                session = new ClientHttp2Session(HTTPScheme.HTTP);
            } else {
                session = new ClientHttp1Session();
            }
            channel.channel().pipeline().addLast("restconf-session", session);
            clientStreamService = SseUtils.enableClientSse(channel);
        });

        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory().newBootstrap(),
            clientStackGrouping(), http2).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(transportListener::initialized);
        assertNotNull(clientStreamService);
        return streamClient;
    }

    protected TestEventStreamListener startStream(final String uri) {
        final var eventListener = new TestEventStreamListener();
        final int initSize = streamControl.size();
        clientStreamService.startEventStream("localhost", uri, eventListener,
            new EventStreamService.StartCallback() {
                @Override
                public void onStreamStarted(final StreamControl control) {
                    streamControl.add(control);
                }

                @Override
                public void onStartFailure(final Throwable cause) {
                    LOG.error("Stream was not started", cause);
                }
            });
        await().atMost(Duration.ofSeconds(2)).until(() -> eventListener.started() && streamControl.size() > initSize);
        return eventListener;
    }

    protected final void addStreamControl(final StreamControl control) {
        streamControl.add(control);
    }

    protected final void closeAllStreams() {
        streamControl.forEach(StreamControl::close);
        streamControl.clear();
    }

    static final class ExampleActionImpl implements ExampleAction {
        @Override
        public ListenableFuture<RpcResult<ExampleActionOutput>> invoke(final DataObjectIdentifier<Root> path,
                final ExampleActionInput input) {
            return Futures.immediateFuture(RpcResultBuilder.success(
                new ExampleActionOutputBuilder().setResponse("Action was invoked").build()).build());
        }
    }
}
