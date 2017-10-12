/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.messagebus.app.util.TopicDOMNotification;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.NotificationPattern;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicNotification;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.DisJoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.DisJoinTopicInputBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NetconfEventSourceTest {


    private static final SchemaPath NOTIFICATION_1_PATH = SchemaPath.create(true, QName.create("ns1", "1970-01-15",
            "not1"));
    private static final SchemaPath NOTIFICATION_2_PATH = SchemaPath.create(true, QName.create("ns2", "1980-02-18",
            "not2"));

    NetconfEventSource netconfEventSource;

    @Mock
    DOMNotificationPublishService domNotificationPublishServiceMock;
    @Mock
    DOMNotification matchnigNotification;
    @Mock
    DOMNotification nonMachtingNotification;
    @Mock
    NetconfEventSourceMount mount;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        //init notification mocks
        doReturn(NOTIFICATION_1_PATH).when(matchnigNotification).getType();
        doReturn(NOTIFICATION_2_PATH).when(nonMachtingNotification).getType();
        DataContainerNodeAttrBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> body = Builders
                .containerBuilder().withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("ns1",
                        "1970-01-15", "not1data")));
        doReturn(body.build()).when(matchnigNotification).getBody();
        //init schema context mock
        Set<NotificationDefinition> notifications = new HashSet<>();
        notifications.add(getNotificationDefinitionMock(NOTIFICATION_1_PATH.getLastComponent()));
        notifications.add(getNotificationDefinitionMock(NOTIFICATION_2_PATH.getLastComponent()));
        SchemaContext schemaContext = mock(SchemaContext.class);
        doReturn(notifications).when(schemaContext).getNotifications();
        //init mount point mock
        List<Stream> streams = new ArrayList<>();
        streams.add(createStream("stream-1"));
        streams.add(createStream("stream-2"));
        doReturn(streams).when(mount).getAvailableStreams();
        doReturn(schemaContext).when(mount).getSchemaContext();
        doReturn(Futures.immediateCheckedFuture(null)).when(mount).invokeCreateSubscription(any(), any());
        doReturn(Futures.immediateCheckedFuture(null)).when(mount).invokeCreateSubscription(any());
        doReturn(mock(ListenerRegistration.class)).when(mount).registerNotificationListener(any(), any());
        final Node nodeId1 = NetconfTestUtils.getNetconfNode("NodeId1", "node.test.local", ConnectionStatus
                .Connected, NetconfTestUtils.NOTIFICATION_CAPABILITY_PREFIX);
        doReturn(nodeId1).when(mount).getNode();
        doReturn(nodeId1.getNodeId().getValue()).when(mount).getNodeId();

        Map<String, String> streamMap = new HashMap<>();
        streamMap.put(NOTIFICATION_1_PATH.getLastComponent().getNamespace().toString(), "stream-1");
        netconfEventSource = new NetconfEventSource(
                streamMap,
                mount,
                domNotificationPublishServiceMock);

    }

    @Test
    public void testJoinTopicOnNotification() throws Exception {
        final JoinTopicInput topic1 = new JoinTopicInputBuilder()
                .setTopicId(TopicId.getDefaultInstance("topic1"))
                .setNotificationPattern(NotificationPattern.getDefaultInstance(".*ns1"))
                .build();
        netconfEventSource.joinTopic(topic1);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        //handle notification matching topic namespace
        netconfEventSource.onNotification(matchnigNotification);
        //handle notification that does not match topic namespace
        netconfEventSource.onNotification(nonMachtingNotification);
        //only matching notification should be published
        verify(domNotificationPublishServiceMock).putNotification(captor.capture());
        final TopicDOMNotification value = (TopicDOMNotification) captor.getValue();
        final QName qname = TopicNotification.QNAME;
        final YangInstanceIdentifier.NodeIdentifier topicIdNode =
                new YangInstanceIdentifier.NodeIdentifier(QName.create(qname, "topic-id"));
        final Object actualTopicId = value.getBody().getChild(topicIdNode).get().getValue();
        Assert.assertEquals(topic1.getTopicId(), actualTopicId);
    }

    @Test
    public void testDisjoinTopicOnNotification() throws Exception {
        final TopicId topicId = TopicId.getDefaultInstance("topic1");
        final JoinTopicInput topic1 = new JoinTopicInputBuilder()
                .setTopicId(topicId)
                .setNotificationPattern(NotificationPattern.getDefaultInstance(".*ns1"))
                .build();
        netconfEventSource.joinTopic(topic1);

        //handle notification matching topic namespace
        netconfEventSource.onNotification(matchnigNotification);
        //disjoin topic
        DisJoinTopicInput disjoinTopic = new DisJoinTopicInputBuilder().setTopicId(topicId).build();
        netconfEventSource.disJoinTopic(disjoinTopic);
        netconfEventSource.onNotification(matchnigNotification);
        //topic notification published only once before disjoin
        verify(domNotificationPublishServiceMock, only()).putNotification(any());
    }

    private static Stream createStream(final String name) {
        return new StreamBuilder()
                .setName(new StreamNameType(name))
                .setReplaySupport(true)
                .build();
    }

    private static NotificationDefinition getNotificationDefinitionMock(final QName qualifiedName) {
        NotificationDefinition notification = mock(NotificationDefinition.class);
        doReturn(qualifiedName).when(notification).getQName();
        doReturn(SchemaPath.create(true, qualifiedName)).when(notification).getPath();
        return notification;
    }

}