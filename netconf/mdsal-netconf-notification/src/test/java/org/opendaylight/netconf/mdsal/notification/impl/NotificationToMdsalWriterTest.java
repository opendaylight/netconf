/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.NotificationRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NotificationToMdsalWriterTest {

    @Mock
    private DataBroker dataBroker;

    private NotificationToMdsalWriter writer;

    @Mock
    private NotificationRegistration notificationRegistration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final NetconfNotificationCollector notificationCollector = mock(NetconfNotificationCollector.class);

        doReturn(notificationRegistration).when(notificationCollector).registerStreamListener(any(
                NetconfNotificationCollector.NetconfNotificationStreamListener.class));

        WriteTransaction tx = mock(WriteTransaction.class);
        doNothing().when(tx).merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class),
                any(DataObject.class), anyBoolean());
        doNothing().when(tx).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        doReturn(emptyFluentFuture()).when(tx).commit();
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();

        writer = new NotificationToMdsalWriter(notificationCollector, dataBroker);
        writer.start();
    }

    @Test
    public void testStreamRegisteration() {
        final StreamNameType testStreamName = new StreamNameType("TESTSTREAM");
        final Stream testStream = new StreamBuilder().setName(testStreamName).build();
        final InstanceIdentifier<Stream> streamIdentifier = InstanceIdentifier.create(Netconf.class)
                .child(Streams.class).child(Stream.class, testStream.key());

        writer.onStreamRegistered(testStream);

        verify(dataBroker.newWriteOnlyTransaction()).merge(LogicalDatastoreType.OPERATIONAL, streamIdentifier,
                testStream, true);

        writer.onStreamUnregistered(testStreamName);

        verify(dataBroker.newWriteOnlyTransaction()).delete(LogicalDatastoreType.OPERATIONAL, streamIdentifier);
    }

    @Test
    public void testClose() {
        doNothing().when(notificationRegistration).close();

        final InstanceIdentifier<Netconf> streamIdentifier = InstanceIdentifier.create(Netconf.class);

        writer.close();

        verify(dataBroker.newWriteOnlyTransaction()).delete(LogicalDatastoreType.OPERATIONAL, streamIdentifier);
        verify(notificationRegistration).close();
    }

}
