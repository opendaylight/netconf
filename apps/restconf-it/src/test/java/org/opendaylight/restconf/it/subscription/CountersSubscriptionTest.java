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

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterOutOfBread;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class CountersSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final NodeIdentifier BREAD_NODEID =
        NodeIdentifier.create(QName.create(ToasterRestocked.QNAME, "amountOfBread").intern());

    private static HTTPClient streamClient;

    @Override
    @BeforeEach
    void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient();
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
        super.afterEach();
    }

    @Test
    void counterNotificationTest() throws Exception {
        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);

        startSubscriptionStream(String.valueOf(id));

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, Instant.now()));

        final var toasterOutOfBreadNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterOutOfBread.QNAME))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterOutOfBreadNotification,
            Instant.now()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var receiversResponse =  invokeRequest(HttpMethod.GET,
                "/restconf/data/ietf-subscribed-notifications:subscriptions/subscription=" + id + "/receivers",
                MediaTypes.APPLICATION_YANG_DATA_JSON, null, MediaTypes.APPLICATION_YANG_DATA_JSON);
            assertEquals(HttpResponseStatus.OK, receiversResponse.status());
            // verify 2 notification were sent ToasterRestocked and ToasterOutOfBread
            assertCounter(receiversResponse, "2", "0");
        });
    }

    @Test
    void counterNotificationWithFilterTest() throws Exception {
        final var response = establishFilteredSubscription("""
            <toasterRestocked xmlns="http://netconfcentral.org/ns/toaster">
              <amountOfBread/>
            </toasterRestocked>
            """, streamClient);

        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);

        startSubscriptionStream(String.valueOf(id));

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, Instant.now()));

        // modify
        final var modifyInput = String.format("""
             <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
             </input>""", id);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var receiversResponse = invokeRequest(HttpMethod.GET,
                "/restconf/data/ietf-subscribed-notifications:subscriptions/" + "subscription=" + id + "/receivers",
                MediaTypes.APPLICATION_YANG_DATA_JSON);
            assertEquals(HttpResponseStatus.OK, receiversResponse.status());
            // verify 2 notification were sent subscription-modified and ToasterRestocked
            assertCounter(receiversResponse, "2", "0");
        });
    }

    @Test
    void excludedCounterNotificationTest() throws Exception {
        final var response = establishFilteredSubscription(
            "<toasterOutOfBread xmlns=\"http://netconfcentral.org/ns/toaster\"/>", streamClient);

        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);

        startSubscriptionStream(String.valueOf(id));

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, Instant.now()));

        final var toasterOutOfBreadNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterOutOfBread.QNAME))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterOutOfBreadNotification,
            Instant.now()));

        // modify
        final var modifyInput = String.format("""
             <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
             </input>""", id);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var receiversResponse = invokeRequest(HttpMethod.GET,
                "/restconf/data/ietf-subscribed-notifications:subscriptions/subscription=" + id + "/receivers",
                MediaTypes.APPLICATION_YANG_DATA_JSON, null, MediaTypes.APPLICATION_YANG_DATA_JSON);

            assertEquals(HttpResponseStatus.OK, receiversResponse.status());
            // verify 2 notification were sent subscription-modified and ToasterOutOfBread and 1 excluded
            // ToasterRestocked
            assertCounter(receiversResponse, "2", "1");
        });
    }

    private static void assertCounter(final FullHttpResponse response, final String sent, final String excluded) {
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8))
            .getJSONObject("ietf-subscribed-notifications:receivers")
            //there is only one receiver
            .getJSONArray("receiver").getJSONObject(0);
        final var sentEvents = json.getString("sent-event-records");
        final var excludedEvents = json.getString("excluded-event-records");
        assertEquals(sent, sentEvents, "Unexpected sent-event records");
        assertEquals(excluded, excludedEvents, "Unexpected excluded-event records");
    }
}
