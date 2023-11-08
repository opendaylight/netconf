/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.devnotif;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.Sink;
import org.opendaylight.restconf.server.mdsal.streams.notif.AbstractNotificationSource;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RestconfStream} reporting YANG notifications coming from a mounted device.
 */
final class DeviceNotificationSource extends AbstractNotificationSource implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationSource.class);

    private final AtomicReference<Runnable> onRemoved = new AtomicReference<>();
    private final DOMMountPointService mountPointService;
    private final YangInstanceIdentifier devicePath;

    DeviceNotificationSource(final DOMMountPointService mountPointService, final YangInstanceIdentifier devicePath) {
        this.mountPointService = requireNonNull(mountPointService);
        this.devicePath = requireNonNull(devicePath);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        // No-op
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        if (devicePath.equals(path)) {
            // The mount point went away, run cleanup
            cleanup();
        }
    }

    @Override
    protected Registration start(final Sink<DOMNotification> sink) {
        final var optMount = mountPointService.getMountPoint(devicePath);
        if (optMount.isEmpty()) {
            LOG.info("Mount point {} not present, terminating", devicePath);
            return endOfStream(sink);
        }

        final var mount = optMount.orElseThrow();
        final var optSchema = mount.getService(DOMSchemaService.class);
        if (optSchema.isEmpty()) {
            LOG.info("Mount point {} does not have a DOMSchemaService, terminating", devicePath);
            return endOfStream(sink);
        }

        final var optNotification = mount.getService(DOMNotificationService.class);
        if (optNotification.isEmpty()) {
            LOG.info("Mount point {} does not have a DOMNotificationService, terminating", devicePath);
            return endOfStream(sink);
        }

        // Find all notifications
        final var modelContext = optSchema.orElseThrow().getGlobalContext();
        final var paths = modelContext.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> Absolute.of(notification.argument()))
            .collect(ImmutableSet.toImmutableSet());
        if (paths.isEmpty()) {
            LOG.info("Mount point {} does not advertize any YANG notifications, terminating", devicePath);
            return endOfStream(sink);
        }

        final var notifReg = optNotification.orElseThrow()
            .registerNotificationListener(new Listener(sink, () -> modelContext), paths);

        // Notifications are running now.
        // If we get removed we need to close those. But since we are running lockless and we need to set up
        // the listener, which will own its cleanup.
        final Runnable closeNotif = () -> {
            notifReg.close();
            sink.endOfStream();
        };
        onRemoved.set(closeNotif);

        // onMountPointRemoved() may be invoked asynchronously before this method returns.
        // Therefore we perform a CAS replacement routine of the close routine:
        // - if it succeeds onRemoved's Runnable covers all required cleanup
        // - if it does not, it means state has already been cleaned up by onMountPointRemoved()
        final var mountReg = mountPointService.registerProvisionListener(this);
        final Runnable closeMount = () -> {
            notifReg.close();
            sink.endOfStream();
            mountReg.close();
        };
        if (onRemoved.compareAndSet(closeNotif, closeMount)) {
            // All set, cleanup() will handle the rest
            return this::cleanup;
        }

        // Already removed, bail out, but do not signal endOfStream()
        mountReg.close();
        return () -> {
            // No-op
        };
    }

    private static @NonNull Registration endOfStream(final Sink<DOMNotification> sink) {
        // Something went wrong: signal end of stream and return a no-op registration
        sink.endOfStream();
        return () -> {
            // No-op
        };
    }

    private void cleanup() {
        final var runnable = onRemoved.getAndSet(null);
        if (runnable != null) {
            runnable.run();
        }
    }
}
