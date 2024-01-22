/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.junit.BeforeClass;
import org.junit.Test;
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

    private final WebSocketFactory webSocketFactory = new WebSocketFactory(mock(ScheduledExecutorService.class),
            5000, 2000);

    @BeforeClass
    public static void prepareListenersBroker() {
        ListenersBroker.getInstance().registerDataChangeListener(TOASTER_YIID, REGISTERED_STREAM_NAME,
                NotificationOutputTypeGrouping.NotificationOutputType.JSON);
    }

    @Test
    public void createWebSocketSuccessfully() {
        final ServletUpgradeRequest upgradeRequest = mock(ServletUpgradeRequest.class);
        final ServletUpgradeResponse upgradeResponse = mock(ServletUpgradeResponse.class);
        doReturn(URI.create('/' + REGISTERED_STREAM_NAME + '/')).when(upgradeRequest).getRequestURI();

        final Object webSocket = webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse);
        assertThat(webSocket, instanceOf(WebSocketSessionHandler.class));
        verify(upgradeResponse).setSuccess(true);
        verify(upgradeResponse).setStatusCode(101);
    }

    @Test
    public void createWebSocketUnsuccessfully() {
        final ServletUpgradeRequest upgradeRequest = mock(ServletUpgradeRequest.class);
        final ServletUpgradeResponse upgradeResponse = mock(ServletUpgradeResponse.class);
        doReturn(URI.create('/' + REGISTERED_STREAM_NAME + '/' + "toasterStatus"))
            .when(upgradeRequest).getRequestURI();

        final Object webSocket = webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse);
        assertNull(webSocket);
        verify(upgradeResponse).setSuccess(false);
        verify(upgradeResponse).setStatusCode(404);
    }
}