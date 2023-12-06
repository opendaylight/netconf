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

import java.net.URI;
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
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.mdsal.streams.dtcl.DataTreeChangeSource;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
@Deprecated(since = "7.0.0", forRemoval = true)
class WebSocketFactoryTest extends AbstractNotificationListenerTest {
    private static final QName TOASTER = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toaster");

    @Mock
    private PingExecutor pingExecutor;
    @Mock
    private ServletUpgradeRequest upgradeRequest;
    @Mock
    private ServletUpgradeResponse upgradeResponse;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeWriteTransaction tx;
    @Mock
    private DOMDataTreeChangeService changeService;
    @Mock
    private DatabindProvider databindProvider;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMNotificationService notificationService;

    private WebSocketFactory webSocketFactory;
    private String streamName;

    @BeforeEach
    void prepareListenersBroker() {
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        final var streamRegistry = new MdsalRestconfStreamRegistry(dataBroker);
        webSocketFactory = new WebSocketFactory(streamRegistry, pingExecutor, 5000, 2000);

        streamName = streamRegistry.createStream(URI.create("https://localhost:8181/rests"),
            new DataTreeChangeSource(databindProvider, changeService, LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.of(TOASTER)),
            "description")
            .getOrThrow()
            .name();
    }

    @Test
    void createWebSocketSuccessfully() {
        doReturn(URI.create("https://localhost:8181/rests/streams/xml/" + streamName))
            .when(upgradeRequest).getRequestURI();

        assertInstanceOf(WebSocketSender.class,
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