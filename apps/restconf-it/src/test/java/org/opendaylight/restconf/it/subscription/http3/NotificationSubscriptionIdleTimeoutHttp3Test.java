/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription.http3;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev261103.http3.server.grouping.quic.under.http.QuicServerParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev261103.http3.server.grouping.quic.under.http.QuicServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.quic.common.rev260901.Varint;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Verifies that the SSE heartbeat keeps a quiet HTTP/3 subscription open past the QUIC max idle timeout.
 */
class NotificationSubscriptionIdleTimeoutHttp3Test extends AbstractNotificationSubscriptionHttp3Test {
    // below the test client's own limit, so this one applies, and above the 1 second heartbeat interval
    private static final long MAX_IDLE_TIMEOUT_MILLIS = 3000;

    @Override
    protected QuicServerParameters quicServerParameters() {
        return new QuicServerParametersBuilder(super.quicServerParameters())
            .setMaxIdleTimeout(new Varint(Uint64.valueOf(MAX_IDLE_TIMEOUT_MILLIS)))
            .build();
    }

    @Test
    void quietSubscriptionOutlivesIdleTimeout() throws Exception {
        final var eventListener = startSubscriptionStream(startSubscription());

        // only the heartbeat is sent for well over the idle timeout
        Thread.sleep(3 * MAX_IDLE_TIMEOUT_MILLIS);
        assertFalse(eventListener.ended(), "Quiet subscription was closed");

        // the connection is still usable
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build(), Instant.now()));
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
