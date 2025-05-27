/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.ReceiversBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.ReceiverBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.ReceiverKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter64;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

public class SubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static HTTPClient streamClient;

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

    /**
     * Tests the toOperational() method output.
     *
     * <p>Establishes a subscription, sends a notification, and verifies that the output reflects the change.
     */
    @Test
    void testToOperationalOutput() throws Exception {
        final var stopTime = Instant.now().plus(Duration.ofDays(2));
        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON, String.format("""
            {
              "input": {
                "stream": "NETCONF",
                "encoding": "encode-json",
                "stop-time": "%s"
              }
            }""", stopTime), MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var subscriptionId = Uint32.valueOf(extractSubscriptionId(response));

        // Add 3 receivers. Only the last one will be used to wait for the notification.
        startSubscriptionStream(String.valueOf(subscriptionId));
        startSubscriptionStream(String.valueOf(subscriptionId));
        final var listener = startSubscriptionStream(String.valueOf(subscriptionId));

        final var subscription = getStreamRegistry().lookupSubscription(subscriptionId);
        Assertions.assertNotNull(subscription);

        validateToOperationalOutput(subscription, stopTime, 0);

        //send notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(notificationNode, Instant.now()));

        //wait for notification
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(listener::readNext, Objects::nonNull);

        validateToOperationalOutput(subscription, stopTime, 1);
    }

    /**
     * Validates that the subscription's operational state matches the expected values.
     *
     * @param subscription actual subscription instance
     * @param stopTime expected stop time
     * @param sentRecords number of expected sent notifications
     */
    private static void validateToOperationalOutput(final RestconfStream.Subscription subscription,
            final Instant stopTime, final int sentRecords) {
        final var receivers = subscription.receiversNames();
        assertEquals(3, receivers.size());

        final var receiverMap = receivers.stream()
            .map(name -> new ReceiverBuilder()
                .setName(name)
                .setState(Receiver.State.Active)
                .setSentEventRecords(new ZeroBasedCounter64(Uint64.valueOf(sentRecords)))
                .setExcludedEventRecords(new ZeroBasedCounter64(Uint64.valueOf(0)))
                .build())
            .collect(BindingMap.<ReceiverKey, Receiver>toMap());

        final var expected = new SubscriptionBuilder()
            .setId(new SubscriptionId(subscription.id()))
            .setEncoding(EncodeJson$I.VALUE)
            .setStopTime(new DateAndTime(stopTime.toString()))
            .setReceivers(new ReceiversBuilder().setReceiver(receiverMap).build())
            .build();

        assertEquals(expected, subscription.toOperational());
    }
}
