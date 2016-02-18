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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class StreamNotificationTopicRegistrationTest {

    private static final String NS = CreateSubscriptionInput.QNAME.getNamespace().toString();
    private static final String REV = CreateSubscriptionInput.QNAME.getFormattedRevision();
    private static final YangInstanceIdentifier.NodeIdentifier STREAM = new YangInstanceIdentifier.NodeIdentifier(QName.create(NS, REV, "stream"));
    private static final YangInstanceIdentifier.NodeIdentifier START_TIME = new YangInstanceIdentifier.NodeIdentifier(QName.create(NS, REV, "startTime"));
    private static final String STREAM_NAME = "stream-1";
    private static final SchemaPath createSubscription = SchemaPath.create(true, QName.create(CreateSubscriptionInput.QNAME, "create-subscription"));
    private static final String PREFIX = ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH.getLastComponent().getNamespace().toString();

    @Mock
    private NetconfEventSource source;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMRpcService service;
    @Mock
    private DOMNotificationService reference;
    @Mock
    private ListenerRegistration<DOMNotificationListener> listenerRegistration;

    private StreamNotificationTopicRegistration registration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Node node = new NodeBuilder().setNodeId(NodeId.getDefaultInstance("node-id")).build();
        when(source.getNode()).thenReturn(node);
        when(source.getDOMMountPoint()).thenReturn(mountPoint);

        when(mountPoint.getService(DOMRpcService.class)).thenReturn(Optional.of(service));
        when(mountPoint.getService(DOMNotificationService.class)).thenReturn(Optional.of(reference));
        when(reference.registerNotificationListener(any(), eq(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH)))
                .thenReturn(listenerRegistration);
        when(service.invokeRpc(eq(createSubscription), any())).thenReturn(Futures.immediateCheckedFuture(null));

        Stream stream = new StreamBuilder().setName(StreamNameType.getDefaultInstance(STREAM_NAME)).setReplaySupport(true).build();

        registration = new StreamNotificationTopicRegistration(stream, PREFIX, source);
    }

    @Test
    public void testActivateNotificationSource() throws Exception {
        registration.activateNotificationSource();

        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        Assert.assertTrue(registration.isActive());
        verify(service).invokeRpc(eq(createSubscription), captor.capture());
        checkStreamName(captor.getValue());
    }

    @Test
    public void testReActivateNotificationSource() throws Exception {
        registration.setActive(true);
        registration.reActivateNotificationSource();

        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        Assert.assertTrue(registration.isActive());
        verify(service).invokeRpc(eq(createSubscription), captor.capture());
        checkStreamName(captor.getValue());
        checkDate(captor.getValue(), Optional.absent());
    }

    @Test
    public void testReActivateNotificationSourceWithReplay() throws Exception {
        final Date lastEventTime = new Date();
        registration.setActive(true);
        registration.setLastEventTime(lastEventTime);
        registration.reActivateNotificationSource();

        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        Assert.assertTrue(registration.isActive());
        verify(service).invokeRpc(eq(createSubscription), captor.capture());
        checkStreamName(captor.getValue());
        checkDate(captor.getValue(), Optional.of(lastEventTime));
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
        final ArrayList<TopicId> notificationTopicIds = registration.getNotificationTopicIds(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(notificationTopicIds);
        Assert.assertThat(notificationTopicIds, hasItems(topic1, topic2, topic3));

        registration.unRegisterNotificationTopic(topic3);
        final ArrayList<TopicId> afterUnregister = registration.getNotificationTopicIds(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(afterUnregister);
        Assert.assertThat(afterUnregister, hasItems(topic1, topic2));
        Assert.assertFalse(afterUnregister.contains(topic3));
    }

    private TopicId registerTopic(String value) {
        final TopicId topic = TopicId.getDefaultInstance(value);
        registration.registerNotificationTopic(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH, topic);
        return topic;
    }

    private void checkStreamName(ContainerNode value) {
        final String streamName = (String) value.getChild(STREAM).get().getValue();
        Assert.assertEquals(STREAM_NAME, streamName);
    }

    private void checkDate(ContainerNode value, Optional<Date> lastEventTime) {
        final Optional<Date> startTime = (Optional<Date>) value.getChild(START_TIME).get().getValue();
        Assert.assertEquals(lastEventTime, startTime);
    }

}