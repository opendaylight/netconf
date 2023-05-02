/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NotificationToMdsalWriterTest {
    @Mock
    private DataBroker dataBroker;
    @Mock
    private Registration registration;

    private NotificationToMdsalWriter writer;

    @Before
    public void setUp() {
        final NetconfNotificationCollector notificationCollector = mock(NetconfNotificationCollector.class);

        doReturn(registration).when(notificationCollector).registerStreamListener(any());

        WriteTransaction tx = mock(WriteTransaction.class);
        doNothing().when(tx).merge(any(), any(), any());
        doNothing().when(tx).delete(any(), any());
        doReturn(emptyFluentFuture()).when(tx).commit();
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();

        writer = new NotificationToMdsalWriter(notificationCollector, dataBroker);
    }

    @Test
    public void testStreamRegisteration() {
        final var testStreamName = new StreamNameType("TESTSTREAM");
        final var testStream = new StreamBuilder().setName(testStreamName).build();
        final var streamIdentifier = InstanceIdentifier.create(Netconf.class)
                .child(Streams.class).child(Stream.class, testStream.key());

        writer.onStreamRegistered(testStream);

        verify(dataBroker.newWriteOnlyTransaction()).merge(LogicalDatastoreType.OPERATIONAL, streamIdentifier,
                testStream);

        writer.onStreamUnregistered(testStreamName);

        verify(dataBroker.newWriteOnlyTransaction()).delete(LogicalDatastoreType.OPERATIONAL, streamIdentifier);
    }

    @Test
    public void testClose() {
        doNothing().when(registration).close();

        final var streamIdentifier = InstanceIdentifier.create(Netconf.class);

        writer.close();

        verify(dataBroker.newWriteOnlyTransaction()).delete(LogicalDatastoreType.OPERATIONAL, streamIdentifier);
        verify(registration).close();
    }
}
