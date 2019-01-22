/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.NotificationRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on changes in netconf notification stream availability and writes
 * changes to the data store.
 */
public final class NotificationToMdsalWriter implements AutoCloseable, NetconfNotificationCollector
        .NetconfNotificationStreamListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationToMdsalWriter.class);
    private static final InstanceIdentifier<Streams> STREAMS = InstanceIdentifier.builder(Netconf.class)
            .child(Streams.class).build();

    private final NetconfNotificationCollector netconfNotificationCollector;
    private final DataBroker dataBroker;
    private NotificationRegistration notificationRegistration;

    public NotificationToMdsalWriter(final NetconfNotificationCollector netconfNotificationCollector,
                                     final DataBroker dataBroker) {
        this.netconfNotificationCollector = netconfNotificationCollector;
        this.dataBroker = dataBroker;
    }

    @Override
    public void close() {
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(Netconf.class));

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo info) {
                LOG.debug("Streams cleared successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to clear streams", throwable);
            }
        }, MoreExecutors.directExecutor());

        notificationRegistration.close();
    }

    /**
     * Invoked by blueprint.
     */
    public void start() {
        notificationRegistration = netconfNotificationCollector.registerStreamListener(this);
    }

    @Override
    public void onStreamRegistered(final Stream stream) {
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

        final InstanceIdentifier<Stream> streamIdentifier = STREAMS.child(Stream.class, stream.key());
        tx.merge(LogicalDatastoreType.OPERATIONAL, streamIdentifier, stream, true);

        try {
            tx.commit().get();
            LOG.debug("Stream {} registered successfully.", stream.getName());
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Unable to register stream {}.", stream, e);
        }
    }

    @Override
    public void onStreamUnregistered(final StreamNameType stream) {
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

        final InstanceIdentifier<Stream> streamIdentifier = STREAMS.child(Stream.class, new StreamKey(stream));

        tx.delete(LogicalDatastoreType.OPERATIONAL, streamIdentifier);

        try {
            tx.commit().get();
            LOG.debug("Stream {} unregistered successfully.", stream);
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Unable to unregister stream {}", stream, e);
        }
    }
}
