/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.broker.RouterDOMPublishNotificationService;
import org.opendaylight.netconf.rfc8639.DeleteSubscriptionRpc;
import org.opendaylight.netconf.rfc8639.EstablishSubscriptionRpc;
import org.opendaylight.netconf.rfc8639.KillSubscriptionRpc;
import org.opendaylight.netconf.rfc8639.ModifySubscriptionRpc;
import org.opendaylight.netconf.rfc8639.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.client.ClientSession;
import org.opendaylight.restconf.client.impl.ClientHttp1Session;
import org.opendaylight.restconf.client.impl.ClientHttp2Session;
import org.opendaylight.restconf.it.AbstractIT;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.restconf.it.server.TestRequestCallback;
import org.opendaylight.restconf.it.server.TestTransportChannelListener;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.runtime.api.DefaultBindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNotificationSubscriptionTest extends AbstractIT {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationSubscriptionTest.class);
    private static final YangParserFactory PARSER_FACTORY = ServiceLoader.load(YangParserFactory.class)
        .findFirst().orElseThrow(() -> new ExceptionInInitializerError("No YangParserFactory found"));
    private static final BindingRuntimeGenerator GENERATOR = ServiceLoader.load(BindingRuntimeGenerator.class)
        .findFirst().orElseThrow(() -> new ExceptionInInitializerError("No BindingRuntimeGenerator found"));
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

    static final String MODIFY_SUBSCRIPTION_URI =
        "/rests/operations/ietf-subscribed-notifications:modify-subscription";
    static final String ESTABLISH_SUBSCRIPTION_URI =
        "/rests/operations/ietf-subscribed-notifications:establish-subscription";

    private EventStreamService clientStreamService;
    private HTTPClient subscriptionStreamClient;
    private HTTPClient rpcClient;
    private EventStreamService.StreamControl streamControl;
    private DOMNotificationPublishService notificationPublishService;
    private ClientSession rpcSession;

    @Override
    protected BindingRuntimeContext getRuntimeContext() {
        return RUNTIME_CONTEXT_CACHE.getUnchecked(getModuleInfos());
    }

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        notificationPublishService = new RouterDOMPublishNotificationService(domNotificationRouter());

        // Initialize the RPC connection
        rpcSession = new ClientHttp1Session();
        final var rpcListener = new TestTransportChannelListener(channel -> {
            channel.channel().pipeline().addLast("restconf-session", rpcSession);
        });
        rpcClient = HTTPClient.connect(rpcListener, bootstrapFactory().newBootstrap(),
            clientStackGrouping(), false).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(rpcListener::initialized);
    }

    @Override
    @AfterEach
    protected void afterEach() throws Exception {
        if (rpcClient != null) {
            rpcClient.shutdown().get(2, TimeUnit.SECONDS);
            rpcClient = null;
        }
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (subscriptionStreamClient != null) {
            subscriptionStreamClient.shutdown().get(2, TimeUnit.SECONDS);
            subscriptionStreamClient = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        super.afterEach();
    }

    FullHttpResponse invokeTwoRequests(final FullHttpRequest request1, final FullHttpRequest request2)
        throws Exception {
        final var clientSession = new ClientHttp1Session();
        final var channelListener = new TestTransportChannelListener(transportChannel -> {
            transportChannel.channel().pipeline().addLast("restconf-session", clientSession);
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory().newBootstrap(),
            clientStackGrouping(), false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(channelListener::initialized);
        final var callback1 = new TestRequestCallback();
        clientSession.invoke(request1, callback1);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback1::completed);
        final var response1 = callback1.response();
        assertNotNull(response1);
        assertEquals(HttpResponseStatus.OK, response1.status());

        final var callback2 = new TestRequestCallback();
        clientSession.invoke(request2, callback2);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback2::completed);
        final var response2 = callback2.response();
        assertNotNull(response2);
        client.shutdown().get(2, TimeUnit.SECONDS);
        return response2;
    }

    protected @NonNull FullHttpResponse invokeRequestKeepClient(final @NonNull HttpMethod method,
            final @NonNull String uri, final @NonNull String mediaType, final @Nullable String acceptType,
            final @Nullable String content) {
        final var callback = new TestRequestCallback();
        rpcSession.invoke(buildRequest(method, uri, mediaType, acceptType, content), callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        final var response = callback.response();
        assertNotNull(response);
        return response;
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

    protected TestEventStreamListener startSubscriptionStream(final String subscriptionId) throws Exception {
        return startSubscriptionStream(subscriptionId, false);
    }

    protected TestEventStreamListener startSubscriptionStream(final String subscriptionId, final boolean http2)
            throws Exception {
        subscriptionStreamClient = startStreamClient(http2);
        return startSubscriptionStreamOnExistingClient(subscriptionId);
    }

    protected TestEventStreamListener startSubscriptionStreamOnExistingClient(final String subscriptionId)
            throws Exception {
        assertNotNull(clientStreamService);
        final var eventListener = new TestEventStreamListener();
        clientStreamService.startEventStream("localhost", "/subscriptions/" + subscriptionId, eventListener,
            new EventStreamService.StartCallback() {
                @Override
                public void onStreamStarted(final EventStreamService.StreamControl control) {
                    streamControl = control;
                }

                @Override
                public void onStartFailure(final Throwable cause) {
                    LOG.error("Stream was not started", cause);
                }
            });
        await().atMost(Duration.ofSeconds(2)).until(eventListener::started);
        assertNotNull(streamControl);
        return eventListener;
    }

    protected final DOMNotificationPublishService publishService() {
        return notificationPublishService;
    }

    /**
     * Utility method to establish a subscription.
     */
    FullHttpResponse establishFilteredSubscription(final String filter) {
        final var input = String.format("""
             <establish-subscription xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <stream>NETCONF</stream>
               <encoding>encode-json</encoding>
               <stream-subtree-filter>%s</stream-subtree-filter>
             </establish-subscription>
             """, filter);

        return invokeRequestKeepClient(HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, MediaTypes.APPLICATION_YANG_DATA_JSON, input);
    }

    /**
     * Utility method to extract subscription ID from response.
     */
    static long extractSubscriptionId(final FullHttpResponse response) {
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8),
            jsonParserConfiguration());
        return jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");
    }

    @Override
    protected List<RpcImplementation> rpcImplementations(final DOMDataBroker domDataBroker,
            final MdsalDatabindProvider dataBindProvider) {
        return List.of(
            // register subscribed notifications RPCs to be tested
            new EstablishSubscriptionRpc(streamRegistry()),
            new ModifySubscriptionRpc(streamRegistry()),
            new DeleteSubscriptionRpc(streamRegistry()),
            new KillSubscriptionRpc(streamRegistry()));
    }
}
