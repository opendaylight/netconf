/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class WebSocketFactoryTest {

    private static final String REGISTERED_STREAM_NAME = "data-change-event-subscription/"
            + "toaster:toaster/datastore=CONFIGURATION/scope=SUBTREE";
    private static final YangInstanceIdentifier TOASTER_YIID = YangInstanceIdentifier.builder()
            .node(QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toaster"))
            .build();

    private final WebSocketFactory webSocketFactory = new WebSocketFactory(Mockito.mock(ScheduledExecutorService.class),
            5000, 2000);

    @BeforeClass
    public static void prepareListenersBroker() {
        ListenersBroker.getInstance().registerDataChangeListener(TOASTER_YIID, REGISTERED_STREAM_NAME,
                NotificationOutputTypeGrouping.NotificationOutputType.JSON);
    }

    @Test
    public void createWebSocketSuccessfully() {
        final ServletUpgradeRequest upgradeRequest = Mockito.mock(ServletUpgradeRequest.class);
        final ServletUpgradeResponse upgradeResponse = Mockito.mock(ServletUpgradeResponse.class);
        Mockito.when(upgradeRequest.getRequestURI()).thenReturn(URI.create('/' + REGISTERED_STREAM_NAME + '/'));

        final Object webSocket = webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse);
        Assert.assertTrue(webSocket instanceof WebSocketSessionHandler);
        Mockito.verify(upgradeResponse).setSuccess(true);
        Mockito.verify(upgradeResponse).setStatusCode(101);
    }

    @Test
    public void createWebSocketUnsuccessfully() {
        final ServletUpgradeRequest upgradeRequest = Mockito.mock(ServletUpgradeRequest.class);
        final ServletUpgradeResponse upgradeResponse = Mockito.mock(ServletUpgradeResponse.class);
        Mockito.when(upgradeRequest.getRequestURI()).thenReturn(URI.create('/' + REGISTERED_STREAM_NAME + '/'
                + "toasterStatus"));

        final Object webSocket = webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse);
        Assert.assertNull(webSocket);
        Mockito.verify(upgradeResponse).setSuccess(false);
        Mockito.verify(upgradeResponse).setStatusCode(404);
    }
}