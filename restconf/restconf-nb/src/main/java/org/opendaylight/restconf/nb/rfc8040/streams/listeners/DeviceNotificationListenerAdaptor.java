/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DeviceNotificationListenerAdaptor} is responsible to track events on notifications.
 */
public final class DeviceNotificationListenerAdaptor extends AbstractNotificationListenerAdaptor
        implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationListenerAdaptor.class);

    private final @NonNull EffectiveModelContext effectiveModel;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull YangInstanceIdentifier instanceIdentifier;
    private final @NonNull DOMDataBroker domDataBroker;

    private ListenerRegistration<DOMMountPointListener> reg;

    public DeviceNotificationListenerAdaptor(final String streamName, final NotificationOutputType outputType,
            final EffectiveModelContext effectiveModel, final DOMMountPointService mountPointService,
            final YangInstanceIdentifier path, final DOMDataBroker domDataBroker) {
        // FIXME: a dummy QName due to contracts
        super(QName.create("dummy", "dummy"), streamName, outputType);
        this.domDataBroker = domDataBroker;
        this.effectiveModel = requireNonNull(effectiveModel);
        this.mountPointService = requireNonNull(mountPointService);
        instanceIdentifier = requireNonNull(path);
    }

    public synchronized void listen(final DOMNotificationService notificationService, final Set<Absolute> paths) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, paths));
            reg = mountPointService.registerProvisionListener(this);
        }
    }

    private synchronized void resetListenerRegistration() {
        if (reg != null) {
            reg.close();
            reg = null;
        }
    }

    @Override
    EffectiveModelContext effectiveModel() {
        return effectiveModel;
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        if (instanceIdentifier.equals(path)) {
            try {
                reRegisterDeviceNotification(path);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        if (instanceIdentifier.equals(path)) {
            getSubscribers().forEach(subscriber -> {
                if (subscriber.isConnected()) {
                    subscriber.sendDataMessage("Device disconnected");
                }
            });
        }
    }

    private void reRegisterDeviceNotification(YangInstanceIdentifier path)
            throws ExecutionException, InterruptedException {
        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
                .orElseThrow(() -> new RestconfDocumentedException("Mount point not available", ErrorType.APPLICATION,
                        ErrorTag.OPERATION_FAILED));
        Collection<? extends NotificationDefinition> notificationDefinitions = mountPoint.getService(
                        DOMSchemaService.class).get().getGlobalContext()
                .getNotifications();
        if (notificationDefinitions == null || notificationDefinitions.isEmpty()) {
            throw new RestconfDocumentedException("Device does not support notification", ErrorType.APPLICATION,
                    ErrorTag.OPERATION_FAILED);
        }
        final Optional<NormalizedNode> normalizedNodeOptional =
                this.domDataBroker.newReadOnlyTransaction().read(LogicalDatastoreType.CONFIGURATION, path).get();
        final Optional<NormalizedNode> normalizedNodeOptionalOperational =
                this.domDataBroker.newReadOnlyTransaction().read(LogicalDatastoreType.OPERATIONAL, path).get();
        LOG.info("data {} {}", normalizedNodeOptional.toString(), normalizedNodeOptionalOperational.toString());
        final Set<Absolute> absolutes = notificationDefinitions.stream()
                .map(notificationDefinition -> Absolute.of(notificationDefinition.getQName()))
                .collect(Collectors.toUnmodifiableSet());
        resetListenerRegistration();
        resetRegistration();
        listen(this.mountPointService.getMountPoint(path).get().getService(DOMNotificationService.class).get(),
                absolutes);
        getSubscribers().forEach(subscriber -> {
            if (subscriber.isConnected()) {
                subscriber.sendDataMessage("Device connected");
            }
        });
    }
}
