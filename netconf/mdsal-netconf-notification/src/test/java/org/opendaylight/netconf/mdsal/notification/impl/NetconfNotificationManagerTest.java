/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Lists;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.dom.codec.impl.DefaultBindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.generator.impl.DefaultBindingRuntimeGenerator;
import org.opendaylight.netconf.mdsal.notification.impl.ops.NotificationsTransformUtil;
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
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.YangParserFactoryImpl;

@RunWith(MockitoJUnitRunner.class)
public class NetconfNotificationManagerTest {

    public static final String RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    @Mock
    private NetconfNotificationRegistry notificationRegistry;

    @Test
    public void testEventTime() throws Exception {
        //Testing values with SimpleDateFormat
        final ArrayList<String> checkWith = Lists.newArrayList(
                "2001-07-04T12:08:56.235-07:00",
                "2015-10-23T09:42:27.671+00:00",
                "1970-01-01T17:17:22.229+00:00",
                "1937-01-01T12:00:27.870+00:20",
                "2015-06-30T23:59:59.000+00:00",
                "1996-12-19T16:39:57.000-08:00",
                "2015-10-23T09:42:27.000+00:00",
                "2015-10-23T09:42:27.200+00:00",
                "1985-04-12T23:20:50.520+00:00",
                // Values with leap second
                "2001-07-04T23:59:59.235-07:00",
                "1990-12-31T23:59:59.000-08:00",
                "2015-10-23T23:59:59.671+00:00",
                "1970-01-01T23:59:59.229+00:00",
                "1937-01-01T23:59:59.870+00:20",
                "1990-12-31T23:59:59.000+00:00",
                "2015-10-23T23:59:59.200+00:00",
                "1985-04-12T23:59:59.520+00:00");
        final Iterator<String> iterator = checkWith.iterator();

        // Testing correct values
        for (final String time : Lists.newArrayList(
                "2001-07-04T12:08:56.235-07:00",
                "2015-10-23T09:42:27.67175+00:00",
                "1970-01-01T17:17:22.229568+00:00",
                "1937-01-01T12:00:27.87+00:20",
                "2015-06-30T23:59:59Z",
                "1996-12-19T16:39:57-08:00",
                "2015-10-23T09:42:27Z",
                "2015-10-23T09:42:27.200001Z",
                "1985-04-12T23:20:50.52Z",
                // Values with leap second
                "2001-07-04T23:59:60.235-07:00",
                "1990-12-31T23:59:60-08:00",
                "2015-10-23T23:59:60.67175+00:00",
                "1970-01-01T23:59:60.229568+00:00",
                "1937-01-01T23:59:60.87+00:20",
                "1990-12-31T23:59:60Z",
                "2015-10-23T23:59:60.20001Z",
                "1985-04-12T23:59:60.52Z"
        )) {
            try {
                final Date apply = NetconfNotification.RFC3339_DATE_PARSER.apply(time);
                final Date parse =
                        new SimpleDateFormat(RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT).parse(iterator.next());
                assertEquals(parse.getTime(), apply.getTime());
                // Testing that we're consistent from formatting to parsing.
                final String dateString = NetconfNotification.RFC3339_DATE_FORMATTER.apply(apply);
                final Date date1 = NetconfNotification.RFC3339_DATE_PARSER.apply(dateString);
                final String dateString1 = NetconfNotification.RFC3339_DATE_FORMATTER.apply(date1);
                Assert.assertEquals(apply, date1);
                Assert.assertEquals(dateString, dateString1);
            } catch (final DateTimeParseException e) {
                fail("Failed to parse time value = " + time + " " + e);
                throw e;
            }
        }

        // Testing that we're consistent from formatting to parsing.
        final Date date0 = Date.from(Instant.ofEpochMilli(0));
        final String dateString0 = NetconfNotification.RFC3339_DATE_FORMATTER.apply(date0);
        final Date date1 = NetconfNotification.RFC3339_DATE_PARSER.apply(dateString0);
        final String dateString1 = NetconfNotification.RFC3339_DATE_FORMATTER.apply(date1);
        Assert.assertEquals(date0, date1);
        Assert.assertEquals(dateString0, dateString1);

        // Testing wrong values
        for (final String time : Lists.newArrayList(
                "0",
                "205-10-23T09:42:27.67175+00:00",
                "1970-01-01T17:60:22.229568+00:00",
                "1937-01-01T32:00:27.87+00:20",
                "2060-13-31T15:59:90-08:00",
                "1990-12-31T23:58:60Z"
        )) {
            try {
                NetconfNotification.RFC3339_DATE_PARSER.apply(time);
            } catch (final DateTimeParseException e) {
                continue;
            }
            fail("Should have thrown an exception; value= " + time);
        }
    }

    @Test
    public void testNotificationListeners() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = createManager();
        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        final NetconfCapabilityChangeBuilder capabilityChangedBuilder = new NetconfCapabilityChangeBuilder();

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);
        doNothing().when(listener).onNotification(any(StreamNameType.class), any(NetconfNotification.class));
        final NotificationListenerRegistration notificationListenerRegistration = netconfNotificationManager
                .registerNotificationListener(NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listener);
        final NetconfCapabilityChange notification = capabilityChangedBuilder.build();
        baseNotificationPublisherRegistration.onCapabilityChanged(notification);

        verify(listener).onNotification(any(StreamNameType.class), any(NetconfNotification.class));

        notificationListenerRegistration.close();

        baseNotificationPublisherRegistration.onCapabilityChanged(notification);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testClose() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = createManager();

        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);

        netconfNotificationManager
                .registerNotificationListener(NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listener);

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
        final NetconfNotificationManager netconfNotificationManager = createManager();

        final NetconfNotificationCollector.NetconfNotificationStreamListener streamListener =
                mock(NetconfNotificationCollector.NetconfNotificationStreamListener.class);
        doNothing().when(streamListener).onStreamRegistered(any(Stream.class));
        doNothing().when(streamListener).onStreamUnregistered(any(StreamNameType.class));

        netconfNotificationManager.registerStreamListener(streamListener);

        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        verify(streamListener).onStreamRegistered(NetconfNotificationManager.BASE_NETCONF_STREAM);


        baseNotificationPublisherRegistration.close();

        verify(streamListener).onStreamUnregistered(NetconfNotificationManager.BASE_STREAM_NAME);
    }

    private static NetconfNotificationManager createManager() throws YangParserException {
        return new NetconfNotificationManager(new NotificationsTransformUtil(new YangParserFactoryImpl(),
            new DefaultBindingRuntimeGenerator(), new DefaultBindingDOMCodecFactory()));
    }
}