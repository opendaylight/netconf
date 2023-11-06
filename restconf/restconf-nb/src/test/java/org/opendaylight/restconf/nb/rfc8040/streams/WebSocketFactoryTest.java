/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableClassToInstanceMap;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
class WebSocketFactoryTest extends AbstractNotificationListenerTest {
    private static final QName TOASTER = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toaster");

    @Mock
    private ScheduledExecutorService execService;
    @Mock
    private ServletUpgradeRequest upgradeRequest;
    @Mock
    private ServletUpgradeResponse upgradeResponse;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeChangeService changeService;
    @Mock
    private DOMDataTreeWriteTransaction tx;
    @Mock
    private DatabindProvider databindProvider;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMNotificationService notificationService;

    private ListenersBroker listenersBroker;
    private WebSocketFactory webSocketFactory;
    private String streamName;

    @BeforeEach
    void prepareListenersBroker() {
        doReturn(ImmutableClassToInstanceMap.of(DOMDataTreeChangeService.class, changeService)).when(dataBroker)
            .getExtensions();
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        listenersBroker = new ListenersBroker.ServerSentEvents(dataBroker, notificationService, mountPointService);
        webSocketFactory = new WebSocketFactory(execService, listenersBroker, 5000, 2000);

        streamName = listenersBroker.createStream("description", "streams",
            new DataTreeChangeSource(databindProvider, dataBroker, LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.of(TOASTER)))
            .getOrThrow()
            .name();
    }

    @Test
    void createWebSocketSuccessfully() {
        doReturn(URI.create("https://localhost:8181/rests/streams/xml/" + streamName))
            .when(upgradeRequest).getRequestURI();

        assertInstanceOf(WebSocketSessionHandler.class,
            webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse));
        verify(upgradeResponse).setSuccess(true);
        verify(upgradeResponse).setStatusCode(101);
    }

    @Test
    void createWebSocketUnsuccessfully() {
        doReturn(URI.create("https://localhost:8181/rests/streams/xml/" + streamName + "/toasterStatus"))
            .when(upgradeRequest).getRequestURI();

        assertNull(webSocketFactory.createWebSocket(upgradeRequest, upgradeResponse));
        verify(upgradeResponse).setSuccess(false);
        verify(upgradeResponse).setStatusCode(404);
    }
}