/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.dom.codec.impl.di.DefaultBindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.generator.impl.DefaultBindingRuntimeGenerator;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.class)
public class NetconfNotificationManagerTest {
    public static final String RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    @Test
    public void testEventTime() throws Exception {
        //Testing values with SimpleDateFormat
        final var iterator = List.of(
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
                "1985-04-12T23:59:59.520+00:00").iterator();

        // Testing correct values
        for (final String time : List.of(
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
            final var apply = NotificationMessage.RFC3339_DATE_PARSER.apply(time);
            final var parse = new SimpleDateFormat(RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT).parse(iterator.next());
            assertEquals(parse, Date.from(apply));
            // Testing that we're consistent from formatting to parsing.
            final String dateString = NotificationMessage.RFC3339_DATE_FORMATTER.apply(apply);
            final var date1 = NotificationMessage.RFC3339_DATE_PARSER.apply(dateString);
            final String dateString1 = NotificationMessage.RFC3339_DATE_FORMATTER.apply(date1);
            assertEquals(apply, date1);
            assertEquals(dateString, dateString1);
        }

        // Testing that we're consistent from formatting to parsing.
        final var date0 = Instant.ofEpochMilli(0);
        final String dateString0 = NotificationMessage.RFC3339_DATE_FORMATTER.apply(date0);
        final var date1 = NotificationMessage.RFC3339_DATE_PARSER.apply(dateString0);
        final String dateString1 = NotificationMessage.RFC3339_DATE_FORMATTER.apply(date1);
        assertEquals(date0, date1);
        assertEquals(dateString0, dateString1);

        // Testing wrong values
        for (final String time : List.of(
                "0",
                "205-10-23T09:42:27.67175+00:00",
                "1970-01-01T17:60:22.229568+00:00",
                "1937-01-01T32:00:27.87+00:20",
                "2060-13-31T15:59:90-08:00",
                "1990-12-31T23:58:60Z")) {
            assertThrows(DateTimeParseException.class, () -> NotificationMessage.RFC3339_DATE_PARSER.apply(time));
        }
    }

    @Test
    public void testNotificationListeners() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = createManager();
        final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration =
                netconfNotificationManager.registerBaseNotificationPublisher();

        final NetconfCapabilityChangeBuilder capabilityChangedBuilder = new NetconfCapabilityChangeBuilder();

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);
        doNothing().when(listener).onNotification(any(StreamNameType.class), any(NotificationMessage.class));

        final NetconfCapabilityChange notification = capabilityChangedBuilder.build();

        try (var reg = netconfNotificationManager.registerNotificationListener(
                NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listener)) {
            baseNotificationPublisherRegistration.onCapabilityChanged(notification);

            verify(listener).onNotification(any(StreamNameType.class), any(NotificationMessage.class));
        }

        baseNotificationPublisherRegistration.onCapabilityChanged(notification);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testCustomNotificationListeners() throws Exception {
        final NetconfNotificationManager netconfNotificationManager = createManager();

        final StreamNameType testStreamName = new StreamNameType("TEST_STREAM");
        final Stream testStream = new StreamBuilder().setName(testStreamName).build();

        final NetconfNotificationListener listenerBase = mock(NetconfNotificationListener.class);
        netconfNotificationManager.registerNotificationListener(
            NetconfNotificationManager.BASE_NETCONF_STREAM.getName(), listenerBase);

        final NetconfNotificationListener listener = mock(NetconfNotificationListener.class);
        netconfNotificationManager.registerNotificationListener(testStream.getName(), listener);

        doNothing().when(listener).onNotification(eq(testStreamName), any(NotificationMessage.class));

        final NotificationMessage notification = new NotificationMessage(
            XmlUtil.readXmlToDocument("<notification/>"));
        netconfNotificationManager.onNotification(testStream.getName(), notification);

        verify(listener).onNotification(eq(testStream.getName()), eq(notification));

        netconfNotificationManager.close();
        netconfNotificationManager.onNotification(testStream.getName(), notification);

        verifyNoMoreInteractions(listener);
        verify(listenerBase, never()).onNotification(eq(testStream.getName()), eq(notification));
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

        final var change = new NetconfCapabilityChangeBuilder().build();
        assertThrows(IllegalStateException.class,
            () -> baseNotificationPublisherRegistration.onCapabilityChanged(change));
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
        return new NetconfNotificationManager(new DefaultYangParserFactory(),
            new DefaultBindingRuntimeGenerator(), new DefaultBindingDOMCodecFactory());
    }
}