/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONParserConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.restconf.it.subscription.AbstractNotificationSubscriptionTest;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class NotificationSubscriptionListeningHttp2Test extends AbstractNotificationSubscriptionTest {
    static final JSONParserConfiguration JSON_PARSER_CONFIGURATION = new JSONParserConfiguration().withStrictMode();
    private static HTTPClient streamClient;
    private static TestEventStreamListener eventListener;

    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient(true);
        final String uri =
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription";
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, uri,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8),
            JSON_PARSER_CONFIGURATION);
        final var subscriptionId = jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");

        // Start listening on notifications
        eventListener = startSubscriptionStream(String.valueOf(subscriptionId), true);
    }

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
        super.afterEach();
    }

    /**
     * Tests sending and receiving custom notification.
     */
    @Test
    void testPutNotification() throws Exception {
        //create notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        //send notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(notificationNode, Instant.now()));

        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification" : {
                "toaster:toasterRestocked" : {
                  "amountOfBread" : 10
                }
              }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }


}
