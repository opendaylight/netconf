/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications.impl;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Lists;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.NetconfNotificationListener;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.notifications.NotificationListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;

public class NetconfNotificationManagerTest {

    @Mock
    private NetconfNotificationRegistry notificationRegistry;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEventTime() throws Exception {
        // Testing correct values
        for (String time : Lists.newArrayList(
                "2001-07-04T12:08:56.235-07:00",
                "2015-10-23T09:42:27.67175+00:00",
                "1970-01-01T17:17:22.229568+00:00",
                "1937-01-01T12:00:27.87+00:20",
                "1990-12-31T15:59:60-08:00",
                "2015-06-30T23:59:60Z",
                "1996-12-19T16:39:57-08:00",
                "2015-10-23T09:42:27Z",
                "2015-10-23T09:42:27.200001Z",
                "1985-04-12T23:20:50.52Z",
                // Values with leap second
                "2001-07-04T12:08:60.235-07:00",
                "2015-10-23T09:42:60.67175+00:00",
                "1970-01-01T17:17:60.229568+00:00",
                "1937-01-01T12:00:60.87+00:20",
                "2015-10-23T09:42:60Z",
                "2015-10-23T09:42:60.200001Z",
                "1985-04-12T23:20:60.52Z"
        )) {
            try {
                NetconfNotification.RFC3339_DATE_PARSER.apply(time);
            } catch (DateTimeParseException e) {
                fail("Failed to parse time value = " + time + " " + e);
                throw e;
            }
        }

        // Testing that we're consistent from formatting to parsing.
        Date date0 = Date.from(Instant.ofEpochMilli(0));
        String dateString0 = NetconfNotification.RFC3339_DATE_FORMATTER.apply(date0);
        Date date1 = NetconfNotification.RFC3339_DATE_PARSER.apply(dateString0);
        String dateString1 = NetconfNotification.RFC3339_DATE_FORMATTER.apply(date1);
        Assert.assertEquals(date0, date1);
        Assert.assertEquals(dateString0, dateString1);

        // Testing wrong values
        for (String time : Lists.newArrayList(
                "0",
                "205-10-23T09:42:27.67175+00:00",
                "1970-01-01T17:60:22.229568+00:00",
                "1937-01-01T32:00:27.87+00:20",
                "2060-13-31T15:59:90-08:00"
        )) {
            try {
                NetconfNotification.RFC3339_DATE_PARSER.apply(time);
            } catch (DateTimeParseException e) {
                continue;
            }
            fail("Should have thrown an exception; value= " + time);
        }
    }

    @Test
    public void testNotificationListeners() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = new NetconfNotificationManager();
        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        final NetconfCapabilityChangeBuilder capabilityChangedBuilder = new NetconfCapabilityChangeBuilder();

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);
        doNothing().when(listener).onNotification(any(StreamNameType.class), any(NetconfNotification.class));
        final NotificationListenerRegistration notificationListenerRegistration = netconfNotificationManager.registerNotificationListener(NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listener);
        final NetconfCapabilityChange notification = capabilityChangedBuilder.build();
        baseNotificationPublisherRegistration.onCapabilityChanged(notification);

        verify(listener).onNotification(any(StreamNameType.class), any(NetconfNotification.class));

        notificationListenerRegistration.close();

        baseNotificationPublisherRegistration.onCapabilityChanged(notification);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testClose() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = new NetconfNotificationManager();

        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration = netconfNotificationManager.registerBaseNotificationPublisher();

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);
        doNothing().when(listener).onNotification(any(StreamNameType.class), any(NetconfNotification.class));

        netconfNotificationManager.registerNotificationListener(NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listener);

        final NetconfNotificationCollector.NetconfNotificationStreamListener streamListener =
                mock(NetconfNotificationCollector.NetconfNotificationStreamListener.class);
        doNothing().when(streamListener).onStreamUnregistered(any(StreamNameType.class));
        doNothing().when(streamListener).onStreamRegistered(any(Stream.class));
        netconfNotificationManager.registerStreamListener(streamListener);

        verify(streamListener).onStreamRegistered(NetconfNotificationManager.BASE_NETCONF_STREAM);

        netconfNotificationManager.close();

        verify(streamListener).onStreamUnregistered(NetconfNotificationManager.BASE_NETCONF_STREAM.getName());

        try {
            baseNotificationPublisherRegistration.onCapabilityChanged(new NetconfCapabilityChangeBuilder().build());
        } catch (final IllegalStateException e) {
            // Exception should be thrown after manager is closed
            return;
        }

        fail("Publishing into a closed manager should fail");
    }

    @Test
    public void testStreamListeners() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = new NetconfNotificationManager();

        final NetconfNotificationCollector.NetconfNotificationStreamListener streamListener = mock(NetconfNotificationCollector.NetconfNotificationStreamListener.class);
        doNothing().when(streamListener).onStreamRegistered(any(Stream.class));
        doNothing().when(streamListener).onStreamUnregistered(any(StreamNameType.class));

        netconfNotificationManager.registerStreamListener(streamListener);

        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        verify(streamListener).onStreamRegistered(NetconfNotificationManager.BASE_NETCONF_STREAM);


        baseNotificationPublisherRegistration.close();

        verify(streamListener).onStreamUnregistered(NetconfNotificationManager.BASE_STREAM_NAME);
    }
}