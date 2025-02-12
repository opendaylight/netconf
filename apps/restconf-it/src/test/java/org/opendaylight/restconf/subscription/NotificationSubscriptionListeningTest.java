/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.server.netty.TestEventStreamListener;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;


public class NotificationSubscriptionListeningTest extends AbstractNotificationSubscriptionTest {

    private static final String JSON_ENCODING = "json";
    private static final String NETCONF_STREAM = "NETCONF";
    private static final String BASE_URI = "/restconf/operations/ietf-subscribed-notifications:";
    private static final String MODIFY_SUBSCRIPTION_URI = BASE_URI + "modify-subscription";



    private static HTTPClient streamClient;
    private static TestEventStreamListener eventListener;
    private static String subscriptionId;

    @BeforeEach
    public void establishSubscription() throws Exception {
        super.beforeEach();
        // Establish subscription
        final var response = establishSubscription(NETCONF_STREAM, JSON_ENCODING);
        assertEquals(HttpResponseStatus.OK, response.status());

        // Get subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        subscriptionId = jsonContent.getJSONObject("output").getString("subscription-id");
        assertNotNull(subscriptionId, "Subscription ID is undefined");

        //Start listening on notifications
        streamClient = startStreamClient();
        eventListener = startSubscriptionStream(subscriptionId);
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        streamClient.shutdown().get(2, TimeUnit.SECONDS);
        super.afterEach();
    }

    @Test
    void test() throws Exception {

        // Modify the subscription
        final var modifyInput = String.format("""
            {
              "input": {
                "subscription-id": "%s",
                "encoding": "json"
              }
            }""", subscriptionId);

        invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, modifyInput);

        JSONAssert.assertEquals("""
                {
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

    }
/*
    final QName BASE_QNAME = QName.create("http://netconfcentral.org/ns/toaster",
        "toaster").intern();

    final var notificationBody = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME,
            "toasterRestocked")))
        .withChild(ImmutableNodes.leafNode(QName.create(BASE_QNAME, "amountOfBread"), 12))
        .build();
*/

}
