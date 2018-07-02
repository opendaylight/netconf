/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.Date;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class StreamNotificationTopicRegistrationTest {

    private static final String STREAM_NAME = "stream-1";
    private static final String PREFIX = ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH
            .getLastComponent().getNamespace().toString();

    @Mock
    private NetconfEventSource source;
    @Mock
    private NetconfEventSourceMount mount;
    @Mock
    private DOMNotificationService reference;
    @Mock
    private ListenerRegistration<DOMNotificationListener> listenerRegistration;

    private StreamNotificationTopicRegistration registration;
    private Stream stream;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Node node = new NodeBuilder().setNodeId(NodeId.getDefaultInstance("node-id")).build();
        when(mount.getNode()).thenReturn(node);
        when(mount.registerNotificationListener(source, ConnectionNotificationTopicRegistration
                .EVENT_SOURCE_STATUS_PATH))
                .thenReturn(listenerRegistration);
        when(mount.invokeCreateSubscription(any(), any())).thenReturn(Futures.immediateCheckedFuture(null));
        when(mount.invokeCreateSubscription(any())).thenReturn(Futures.immediateCheckedFuture(null));

        when(source.getMount()).thenReturn(mount);
        stream = new StreamBuilder().setName(StreamNameType.getDefaultInstance(STREAM_NAME)).setReplaySupport(true)
                .build();

        registration = new StreamNotificationTopicRegistration(stream, PREFIX, source);
    }

    @Test
    public void testActivateNotificationSource() throws Exception {
        registration.activateNotificationSource();
        Assert.assertTrue(registration.isActive());
        verify(mount).invokeCreateSubscription(stream);

    }

    @Test
    public void testReActivateNotificationSource() throws Exception {
        registration.setActive(true);
        registration.reActivateNotificationSource();

        Assert.assertTrue(registration.isActive());
        verify(mount).invokeCreateSubscription(stream, Optional.absent());
    }

    @Test
    public void testReActivateNotificationSourceWithReplay() throws Exception {
        final Date lastEventTime = new Date();
        registration.setActive(true);
        registration.setLastEventTime(lastEventTime);
        registration.reActivateNotificationSource();

        Assert.assertTrue(registration.isActive());
        verify(mount).invokeCreateSubscription(stream, Optional.of(lastEventTime));
    }

    @Test
    public void testClose() throws Exception {
        registration.setActive(true);
        registration.close();
        Assert.assertFalse(registration.isActive());
    }

    @Test
    public void testRegisterAndUnregisterNotificationTopic() throws Exception {
        final TopicId topic1 = registerTopic("topic1");
        final TopicId topic2 = registerTopic("topic2");
        final TopicId topic3 = registerTopic("topic3");
        final Set<TopicId> notificationTopicIds =
                registration.getTopicsForNotification(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(notificationTopicIds);
        Assert.assertThat(notificationTopicIds, hasItems(topic1, topic2, topic3));

        registration.unRegisterNotificationTopic(topic3);
        final Set<TopicId> afterUnregister =
                registration.getTopicsForNotification(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(afterUnregister);
        Assert.assertThat(afterUnregister, hasItems(topic1, topic2));
        Assert.assertFalse(afterUnregister.contains(topic3));
    }

    private TopicId registerTopic(final String value) {
        final TopicId topic = TopicId.getDefaultInstance(value);
        registration.registerNotificationTopic(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH, topic);
        return topic;
    }


}