/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.test.notifications.NotificationsCounter;
import org.opendaylight.test.utils.TestUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

@Singleton
public class MountedDeviceListener implements DOMMountPointListener {

    private static final Logger LOG = LoggerFactory.getLogger(MountedDeviceListener.class);
    private static final String TEST_NODE_PREFIX = "perf-";
    private static final String STREAM_DEFAULT_NAME = "STREAM-PERF-DEFAULT";
    private static final QName CREATE_SUBSCRIPTION_QNAME = QName.create(CreateSubscriptionInput.QNAME,
        "create-subscription");
    private static final QName NOTIFICATION_QNAME = QName.create(
        "org:opendaylight:coretutorials:ncmount:example:notifications", "2015-06-11", "vrf-route-notification");
    private final DOMMountPointService mountPointService;
    private final BindingNormalizedNodeSerializer serializer;
    private final ConcurrentHashMap<YangInstanceIdentifier, ListenerRegistration<NotificationsCounter>> listeners =
        new ConcurrentHashMap<>();

    @Inject
    public MountedDeviceListener(final @Reference DOMMountPointService mountPointService,
                                 final @Reference BindingNormalizedNodeSerializer serializer) {
        this.mountPointService = mountPointService;
        this.serializer = serializer;
        mountPointService.registerProvisionListener(this);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        final Optional<String> optNodeId = TestUtils.getNodeId(path);
        if (optNodeId.isPresent() && optNodeId.get().startsWith(TEST_NODE_PREFIX)) {
            LOG.trace("Test node mounted: {}", optNodeId.get());
            trackNotificationsPerformance(path);
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        if (listeners.contains(path)) {
            listeners.get(path).close();
            listeners.remove(path);
        }
    }

    private void trackNotificationsPerformance(final YangInstanceIdentifier path) {
        // 1. get nodeId from the path
        final String nodeId = TestUtils.getNodeId(path).get();

        // 2. extract needed services from the mount point
        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RuntimeException("Unable to get mountpoint"));
        final DOMRpcService rpcService = mountPoint.getService(DOMRpcService.class)
            .orElseThrow(() -> new RuntimeException("Unable to get RPC Service from the mountpoint"));
        final DOMNotificationService notificationService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RuntimeException("Unable to get NotificationService from the mountpoint"));

        // 3. create a listener for the notifications
        final NotificationsCounter notificationsListener = new NotificationsCounter(nodeId, serializer);
        final ListenerRegistration<NotificationsCounter> registeredListener = notificationService
            .registerNotificationListener(notificationsListener, SchemaNodeIdentifier.Absolute.of(NOTIFICATION_QNAME));
        listeners.put(path, registeredListener);

        // 4. send 'create-subscription' request to the device
        final StreamNameType streamNameType = new StreamNameType(STREAM_DEFAULT_NAME);
        final CreateSubscriptionInputBuilder subscriptionInputBuilder = new CreateSubscriptionInputBuilder();
        subscriptionInputBuilder.setStream(streamNameType);
        final CreateSubscriptionInput input = subscriptionInputBuilder.build();
        final ContainerNode inputNode = serializer.toNormalizedNodeRpcData(input);
        final ListenableFuture<? extends DOMRpcResult> resultFuture = rpcService.invokeRpc(CREATE_SUBSCRIPTION_QNAME,
            inputNode);
        Futures.addCallback(resultFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(@Nullable final DOMRpcResult rpcResult) {
                LOG.info("Notification stream subscription succesfully completed");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Notification stream subscription failed");
            }
        }, MoreExecutors.directExecutor());
    }
}
