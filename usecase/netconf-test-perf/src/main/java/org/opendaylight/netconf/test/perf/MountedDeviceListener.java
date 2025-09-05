/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.perf;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.test.perf.notifications.NotificationsCounter;
import org.opendaylight.netconf.test.perf.utils.TestUtils;
import org.opendaylight.yang.gen.v1.org.opendaylight.coretutorials.ncmount.example.notifications.rev150611.VrfRouteNotification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true)
public final class MountedDeviceListener implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(MountedDeviceListener.class);
    private static final String TEST_NODE_PREFIX = "perf-";
    private static final String STREAM_DEFAULT_NAME = "STREAM-PERF-DEFAULT";
    private static final QName CREATE_SUBSCRIPTION_QNAME = QName.create(CreateSubscriptionInput.QNAME,
        "create-subscription");

    private final ConcurrentMap<YangInstanceIdentifier, Registration> listeners = new ConcurrentHashMap<>();
    private final DOMMountPointService mountPointService;
    private final BindingNormalizedNodeSerializer serializer;
    private final Registration reg;

    @Inject
    @Activate
    public MountedDeviceListener(final @Reference DOMMountPointService mountPointService,
                                 final @Reference BindingNormalizedNodeSerializer serializer) {
        this.mountPointService = requireNonNull(mountPointService);
        this.serializer = requireNonNull(serializer);
        reg = mountPointService.registerProvisionListener(this);
    }

    @PreDestroy
    @Deactivate
    public void stop() {
        reg.close();
        final var it = listeners.values().iterator();
        while (it.hasNext()) {
            it.next().close();
            it.remove();
        }
    }

    @Override
    public void onMountPointCreated(final DOMMountPoint mountPoint) {
        final var path = mountPoint.getIdentifier();
        TestUtils.getNodeId(path).ifPresent(nodeId -> {
            if (nodeId.startsWith(TEST_NODE_PREFIX)) {
                LOG.info("Test node mounted: {}", nodeId);
                trackNotificationsPerformance(path);
            }
        });
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        final var listener = listeners.remove(path);
        if (listener != null) {
            listener.close();
        }
    }

    private void trackNotificationsPerformance(final YangInstanceIdentifier path) {
        // 1. get nodeId from the path
        final String nodeId = TestUtils.getNodeId(path).orElseThrow();

        // 2. extract needed services from the mount point
        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RuntimeException("Unable to get mountpoint"));
        final DOMRpcService rpcService = mountPoint.getService(DOMRpcService.class)
            .orElseThrow(() -> new RuntimeException("Unable to get RPC Service from the mountpoint"));
        final DOMNotificationService notificationService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RuntimeException("Unable to get NotificationService from the mountpoint"));

        // 3. create a listener for the notifications
        listeners.put(path, notificationService.registerNotificationListener(
            new NotificationsCounter(nodeId, serializer), Absolute.of(VrfRouteNotification.QNAME)));

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
            public void onSuccess(final DOMRpcResult rpcResult) {
                LOG.info("Notification stream subscription succesfully completed");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Notification stream subscription failed");
            }
        }, MoreExecutors.directExecutor());
    }
}
