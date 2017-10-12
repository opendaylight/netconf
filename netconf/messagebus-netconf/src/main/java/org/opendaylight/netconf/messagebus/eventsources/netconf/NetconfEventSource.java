/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.messagebus.app.util.TopicDOMNotification;
import org.opendaylight.controller.messagebus.app.util.Util;
import org.opendaylight.controller.messagebus.spi.EventSource;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.NotificationPattern;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicNotification;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.DisJoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * NetconfEventSource serves as proxy between nodes and messagebus. Subscribers can join topic stream from this source.
 * Then they will receive notifications from device that matches pattern specified by topic.
 */
public class NetconfEventSource implements EventSource, DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfEventSource.class);

    private static final NodeIdentifier TOPIC_NOTIFICATION_ARG = NodeIdentifier.create(TopicNotification.QNAME);
    private static final NodeIdentifier EVENT_SOURCE_ARG = NodeIdentifier.create(
            QName.create(TopicNotification.QNAME, "node-id"));
    private static final NodeIdentifier TOPIC_ID_ARG = NodeIdentifier.create(
            QName.create(TopicNotification.QNAME, "topic-id"));
    private static final NodeIdentifier PAYLOAD_ARG = NodeIdentifier.create(
            QName.create(TopicNotification.QNAME, "payload"));
    private static final String CONNECTION_NOTIFICATION_SOURCE_NAME = "ConnectionNotificationSource";

    private final DOMNotificationPublishService domPublish;

    private final Map<String, String> urnPrefixToStreamMap; // key = urnPrefix, value = StreamName

    /**
     * Map notification uri -> registrations.
     */
    private final Multimap<String, NotificationTopicRegistration>
            notificationTopicRegistrations = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    private final NetconfEventSourceMount mount;

    /**
     * Creates new NetconfEventSource for node. Topic notifications will be published via provided
     * {@link DOMNotificationPublishService}
     *
     * @param streamMap      netconf streams from device
     * @param publishService publish service
     */
    public NetconfEventSource(final Map<String, String> streamMap,
                              final NetconfEventSourceMount mount,
                              final DOMNotificationPublishService publishService) {
        this.mount = mount;
        this.urnPrefixToStreamMap = Preconditions.checkNotNull(streamMap);
        this.domPublish = Preconditions.checkNotNull(publishService);
        this.initializeNotificationTopicRegistrationList();

        LOG.info("NetconfEventSource [{}] created.", mount.getNodeId());
    }

    /**
     * Creates {@link ConnectionNotificationTopicRegistration} for connection. Also creates
     * {@link StreamNotificationTopicRegistration} for every prefix and available stream as defined in config file.
     */
    private void initializeNotificationTopicRegistrationList() {
        final ConnectionNotificationTopicRegistration cntr =
                new ConnectionNotificationTopicRegistration(CONNECTION_NOTIFICATION_SOURCE_NAME, this);
        notificationTopicRegistrations
                .put(cntr.getNotificationUrnPrefix(), cntr);
        Map<String, Stream> availableStreams = getAvailableStreams();
        LOG.debug("Stream configuration compare...");
        for (String urnPrefix : this.urnPrefixToStreamMap.keySet()) {
            final String streamName = this.urnPrefixToStreamMap.get(urnPrefix);
            LOG.debug("urnPrefix: {} streamName: {}", urnPrefix, streamName);
            if (availableStreams.containsKey(streamName)) {
                LOG.debug("Stream containig on device");
                notificationTopicRegistrations
                        .put(urnPrefix, new StreamNotificationTopicRegistration(availableStreams.get(streamName),
                                urnPrefix, this));
            }
        }
    }

    private Map<String, Stream> getAvailableStreams() {
        Map<String, Stream> streamMap = new HashMap<>();
        final List<Stream> availableStreams;
        try {
            availableStreams = mount.getAvailableStreams();
            streamMap = Maps.uniqueIndex(availableStreams, input -> input.getName().getValue());
        } catch (ReadFailedException e) {
            LOG.warn("Can not read streams for node {}", mount.getNodeId());
        }
        return streamMap;
    }

    @Override
    public Future<RpcResult<JoinTopicOutput>> joinTopic(final JoinTopicInput input) {
        LOG.debug("Join topic {} on {}", input.getTopicId().getValue(), mount.getNodeId());
        final NotificationPattern notificationPattern = input.getNotificationPattern();
        final List<SchemaPath> matchingNotifications = getMatchingNotifications(notificationPattern);
        return registerTopic(input.getTopicId(), matchingNotifications);

    }

    @Override
    public Future<RpcResult<Void>> disJoinTopic(final DisJoinTopicInput input) {
        for (NotificationTopicRegistration reg : notificationTopicRegistrations.values()) {
            reg.unRegisterNotificationTopic(input.getTopicId());
        }
        return Util.resultRpcSuccessFor((Void) null);
    }

    private synchronized Future<RpcResult<JoinTopicOutput>> registerTopic(
            final TopicId topicId,
            final List<SchemaPath> notificationsToSubscribe) {
        Preconditions.checkNotNull(notificationsToSubscribe);
        LOG.debug("Join topic {} - register", topicId);
        JoinTopicStatus joinTopicStatus = JoinTopicStatus.Down;

        LOG.debug("Notifications to subscribe has found - count {}", notificationsToSubscribe.size());
        int registeredNotificationCount = 0;
        for (SchemaPath schemaPath : notificationsToSubscribe) {
            final Collection<NotificationTopicRegistration> topicRegistrations =
                    notificationTopicRegistrations.get(schemaPath.getLastComponent().getNamespace().toString());
            for (NotificationTopicRegistration reg : topicRegistrations) {
                LOG.info("Source of notification {} is activating, TopicId {}", reg.getSourceName(),
                        topicId.getValue());
                boolean regSuccess = reg.registerNotificationTopic(schemaPath, topicId);
                if (regSuccess) {
                    registeredNotificationCount = registeredNotificationCount + 1;
                }
            }
        }
        if (registeredNotificationCount > 0) {
            joinTopicStatus = JoinTopicStatus.Up;
        }
        final JoinTopicOutput output = new JoinTopicOutputBuilder().setStatus(joinTopicStatus).build();
        return immediateFuture(RpcResultBuilder.success(output).build());

    }

    public void reActivateStreams() {
        for (NotificationTopicRegistration reg : notificationTopicRegistrations.values()) {
            LOG.info("Source of notification {} is reactivating on node {}", reg.getSourceName(), mount.getNodeId());
            reg.reActivateNotificationSource();
        }
    }

    public void deActivateStreams() {
        for (NotificationTopicRegistration reg : notificationTopicRegistrations.values()) {
            LOG.info("Source of notification {} is deactivating on node {}", reg.getSourceName(), mount.getNodeId());
            reg.deActivateNotificationSource();
        }
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        SchemaPath notificationPath = notification.getType();
        Date notificationEventTime = null;
        if (notification instanceof DOMEvent) {
            notificationEventTime = ((DOMEvent) notification).getEventTime();
        }
        final String namespace = notification.getType().getLastComponent().getNamespace().toString();
        for (NotificationTopicRegistration notifReg : notificationTopicRegistrations.get(namespace)) {
            notifReg.setLastEventTime(notificationEventTime);
            Set<TopicId> topicIdsForNotification = notifReg.getTopicsForNotification(notificationPath);
            for (TopicId topicId : topicIdsForNotification) {
                publishNotification(notification, topicId);
                LOG.debug("Notification {} has been published for TopicId {}", notification.getType(),
                        topicId.getValue());
            }
        }
    }

    private void publishNotification(final DOMNotification notification, final TopicId topicId) {
        final ContainerNode topicNotification = Builders.containerBuilder().withNodeIdentifier(TOPIC_NOTIFICATION_ARG)
                .withChild(ImmutableNodes.leafNode(TOPIC_ID_ARG, topicId))
                .withChild(ImmutableNodes.leafNode(EVENT_SOURCE_ARG, mount.getNodeId()))
                .withChild(encapsulate(notification))
                .build();
        try {
            domPublish.putNotification(new TopicDOMNotification(topicNotification));
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private AnyXmlNode encapsulate(final DOMNotification body) {
        // FIXME: Introduce something like YangModeledAnyXmlNode in Yangtools
        final Document doc = XmlUtil.newDocument();
        final Optional<String> namespace = Optional.of(PAYLOAD_ARG.getNodeType().getNamespace().toString());
        final Element element = XmlUtil.createElement(doc, "payload", namespace);

        final DOMResult result = new DOMResult(element);

        final SchemaContext context = mount.getSchemaContext();
        final SchemaPath schemaPath = body.getType();
        try {
            NetconfUtil.writeNormalizedNode(body.getBody(), result, schemaPath, context);
            return Builders.anyXmlBuilder().withNodeIdentifier(PAYLOAD_ARG).withValue(new DOMSource(element)).build();
        } catch (IOException | XMLStreamException e) {
            LOG.error("Unable to encapsulate notification.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns all available notification paths that matches given pattern.
     *
     * @param notificationPattern pattern
     * @return notification paths
     */
    private List<SchemaPath> getMatchingNotifications(final NotificationPattern notificationPattern) {
        final String regex = notificationPattern.getValue();

        final Pattern pattern = Pattern.compile(regex);
        List<SchemaPath> availableNotifications = getAvailableNotifications();
        return Util.expandQname(availableNotifications, pattern);
    }

    @Override
    public void close() throws Exception {
        for (NotificationTopicRegistration streamReg : notificationTopicRegistrations.values()) {
            streamReg.close();
        }
    }

    @Override
    public NodeKey getSourceNodeKey() {
        return mount.getNode().getKey();
    }

    @Override
    public List<SchemaPath> getAvailableNotifications() {

        final List<SchemaPath> availNotifList = new ArrayList<>();
        // add Event Source Connection status notification
        availNotifList.add(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);

        final Set<NotificationDefinition> availableNotifications = mount.getSchemaContext()
                .getNotifications();
        // add all known notifications from netconf device
        for (final NotificationDefinition nd : availableNotifications) {
            availNotifList.add(nd.getPath());
        }
        return availNotifList;
    }

    NetconfEventSourceMount getMount() {
        return mount;
    }

}
