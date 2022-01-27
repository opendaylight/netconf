/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfImplNotificationSubscribingTest {

    private final String identifier = "data-change-event-subscription/datastore=OPERATIONAL/scope=ONE";

    private static EffectiveModelContext schemaContext;

    @Mock
    private BrokerFacade broker;

    @Mock
    private UriInfo uriInfo;

    private ControllerContext controllerContext;
    private RestconfImpl restconfImpl;

    @BeforeClass
    public static void init() throws FileNotFoundException {
        schemaContext = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/notifications"));
    }

    @AfterClass
    public static void cleanUp() {
        WebSocketServer.destroyInstance(); // NETCONF-604
    }

    @Before
    public void setup() {
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
        restconfImpl = RestconfImpl.newInstance(broker, controllerContext);

        final YangInstanceIdentifier path = mock(YangInstanceIdentifier.class);
        Notificator.createListener(path, identifier, NotificationOutputType.XML, controllerContext);
    }

    @Test
    public void startTimeTest() {
        subscribe(Set.of(Map.entry("start-time",  List.of("2014-10-25T10:02:00Z"))));
        Notificator.removeAllListeners();
    }

    @Test
    public void milisecsTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00.12345Z"))));
        Notificator.removeAllListeners();
    }

    @Test
    public void zonesPlusTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00+01:00"))));
        Notificator.removeAllListeners();
    }

    @Test
    public void zonesMinusTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00-01:00"))));
        Notificator.removeAllListeners();
    }

    @Test
    public void startAndStopTimeTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00Z")),
            Map.entry("stop-time", List.of("2014-10-25T12:31:00Z"))));
        Notificator.removeAllListeners();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void stopTimeTest() {
        subscribe(Set.of(Map.entry("stop-time", List.of("2014-10-25T12:31:00Z"))));
        Notificator.removeAllListeners();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void badParamTest() {
        subscribe(Set.of(Map.entry("time", List.of("2014-10-25T12:31:00Z"))));
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badValueTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("badvalue"))));
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badZonesTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00Z+1:00"))));
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badMilisecsTest() {
        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00:0026Z"))));
        Notificator.removeAllListeners();
    }

    @Test
    public void onNotifiTest() throws Exception {
        final YangInstanceIdentifier path = mock(YangInstanceIdentifier.class);
        final PathArgument pathValue = NodeIdentifier.create(QName.create("module", "2016-12-14", "localName"));
        final ListenerAdapter listener = Notificator.createListener(path, identifier, NotificationOutputType.XML,
                controllerContext);

        subscribe(Set.of(Map.entry("start-time", List.of("2014-10-25T10:02:00Z"))));

        Instant startOrig = listener.getStart();
        assertNotNull(startOrig);
        listener.onDataTreeChanged(List.of());

        startOrig = listener.getStart();
        assertNull(startOrig);
    }

    private void subscribe(final Set<Entry<String, List<String>>> entries) {
        final MultivaluedMap<String, String> map = mock(MultivaluedMap.class);
        when(uriInfo.getQueryParameters()).thenReturn(map);
        final UriBuilder uriBuilder = UriBuilder.fromPath("http://localhost:8181/" + identifier);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);
        when(map.entrySet()).thenReturn(entries);
        restconfImpl.subscribeToStream(identifier, uriInfo);
    }
}
