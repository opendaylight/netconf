/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.mdsal.notification.impl.NetconfNotificationManager;
import org.opendaylight.test.notifications.TestNotificationsListener;
import org.opendaylight.test.utils.TestUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MountedDeviceListener implements DOMMountPointListener {

    private static final Logger LOG = LoggerFactory.getLogger(MountedDeviceListener.class);
    private static final String TEST_NODE_PREFIX = "qa-";
    private static final String TEST_NODE_WITH_NOTIFICATIONS_SUFFIX = "notif";
    private static final String STREAM_DEFAULT_NAME = "STREAM-DEFAULT";
    private static final QName CREATE_SUBSCRIPTION_QNAME =
        QName.create(CreateSubscriptionInput.QNAME, "create-subscription");
    private final DOMMountPointService mountPointService;
    private final NetconfNotificationManager notificationManager;
    private final BindingNormalizedNodeSerializer serializer;
    private final ConcurrentHashMap<YangInstanceIdentifier, TestNotificationsListener> notificationListeners =
        new ConcurrentHashMap<>();

    @Inject
    public MountedDeviceListener(final @Reference DOMMountPointService mountPointService,
                                 final @Reference BindingNormalizedNodeSerializer serializer,
                                 final @Reference NetconfNotificationManager notificationManager) {
        this.mountPointService = mountPointService;
        this.notificationManager = notificationManager;
        this.serializer = serializer;
        mountPointService.registerProvisionListener(this);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        final Optional<String> optNodeId = TestUtils.getNodeId(path);
        if (optNodeId.isPresent() && optNodeId.get().startsWith(TEST_NODE_PREFIX)) {
            LOG.error("Test node mounted: {}", optNodeId.get());
            if (optNodeId.get().contains(TEST_NODE_WITH_NOTIFICATIONS_SUFFIX)) {
                monitorNotifications(path);
            }
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        // TBD
    }

    private void monitorNotifications(final YangInstanceIdentifier path) {
        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path).get();
        final DOMRpcService rpcService = mountPoint.getService(DOMRpcService.class).get();

        final String nodeId = TestUtils.getNodeId(path).get();
        final String streamName = STREAM_DEFAULT_NAME + nodeId;
        final StreamNameType streamNameType = new StreamNameType(streamName);
        final CreateSubscriptionInputBuilder subscriptionInputBuilder = new CreateSubscriptionInputBuilder();
        subscriptionInputBuilder.setStream(streamNameType);
        final CreateSubscriptionInput input = subscriptionInputBuilder.build();
        final ContainerNode inputNode = serializer.toNormalizedNodeRpcData(input);

        LOG.info("Create and bind notification listener for stream: {}", streamName);
        final TestNotificationsListener notificationsListener = new TestNotificationsListener(nodeId);
        notificationManager.registerNotificationListener(streamNameType, notificationsListener);

        LOG.info("Triggering subscription to notification stream: {}", streamName);
        rpcService.invokeRpc(CREATE_SUBSCRIPTION_QNAME, inputNode);
    }

}
