/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 *
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class RestconfImplNotificationSubscribingTest {

    private final String identifier = "data-change-event-subscription/datastore=OPERATIONAL/scope=ONE";

    @Mock
    private BrokerFacade broker;

    @Mock
    private DOMDataBroker domDataBroker;

    @Mock
    private UriInfo uriInfo;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.broker.setDomDataBroker(this.domDataBroker);
        RestconfImpl.getInstance().setBroker(this.broker);
        ControllerContext.getInstance().setGlobalSchema(TestRestconfUtils.loadSchemaContext("/notifications"));

        final YangInstanceIdentifier path = Mockito.mock(YangInstanceIdentifier.class);
        Notificator.createListener(path, this.identifier, NotificationOutputType.XML);
    }

    @Test
    public void startTimeTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test
    public void milisecsTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00.12345Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test
    public void zonesPlusTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00+01:00");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test
    public void zonesMinusTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00-01:00");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test
    public void startAndStopTimeTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00Z");
        Mockito.when(entry.getValue()).thenReturn(time);

        final Entry<String, List<String>> entry2 = Mockito.mock(Entry.class);
        Mockito.when(entry2.getKey()).thenReturn("stop-time");
        final List<String> time2 = new ArrayList<>();
        time2.add("2014-10-25T12:31:00Z");
        Mockito.when(entry2.getValue()).thenReturn(time2);

        list.add(entry);
        list.add(entry2);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void stopTimeTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("stop-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T12:31:00Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void badParamTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T12:31:00Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badValueTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("badvalue");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badZonesTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00Z+1:00");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badMilisecsTest() {
        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00:0026Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);
        Notificator.removeAllListeners();
    }

    @Test
    public void onNotifiTest() throws Exception {
        final YangInstanceIdentifier path = Mockito.mock(YangInstanceIdentifier.class);
        final ListenerAdapter listener = Notificator.createListener(path, this.identifier, NotificationOutputType.XML);

        final List<Entry<String, List<String>>> list = new ArrayList<>();
        final Entry<String, List<String>> entry = Mockito.mock(Entry.class);
        Mockito.when(entry.getKey()).thenReturn("start-time");
        final List<String> time = new ArrayList<>();
        time.add("2014-10-25T10:02:00Z");
        Mockito.when(entry.getValue()).thenReturn(time);
        list.add(entry);

        subscribe(list);

        final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change =
                Mockito.mock(AsyncDataChangeEvent.class);
        Field start = listener.getClass().getDeclaredField("start");
        start.setAccessible(true);
        Date startOrig = (Date) start.get(listener);
        Assert.assertNotNull(startOrig);
        listener.onDataChanged(change);

        start = listener.getClass().getDeclaredField("start");
        start.setAccessible(true);
        startOrig = (Date) start.get(listener);
        Assert.assertNull(startOrig);
    }

    private void subscribe(final List<Entry<String, List<String>>> entries) {
        final MultivaluedMap<String, String> map = Mockito.mock(MultivaluedMap.class);
        Mockito.when(this.uriInfo.getQueryParameters()).thenReturn(map);
        final UriBuilder uriBuilder = UriBuilder.fromPath("http://localhost:8181/" + this.identifier);
        Mockito.when(this.uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);
        final Set<Entry<String, List<String>>> set = new HashSet<>();
        for(final Entry<String, List<String>> entry : entries){
            set.add(entry);
        }
        Mockito.when(map.entrySet()).thenReturn(set);
        RestconfImpl.getInstance().subscribeToStream(this.identifier, this.uriInfo);
    }

}
