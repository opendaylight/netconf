/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.ChangedByBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.changed.by.server.or.user.ServerBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on capabilities changes in data store and publishes them to base NETCONF notification stream listener.
 */
@Component(service = { })
public final class CapabilityChangeNotificationProducer implements DataTreeChangeListener<Capabilities>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CapabilityChangeNotificationProducer.class);

    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    private final ListenerRegistration<?> capabilityChangeListenerRegistration;

    @Activate
    public CapabilityChangeNotificationProducer(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationCollector notifManager,
            @Reference final DataBroker dataBroker) {
        baseNotificationPublisherRegistration = notifManager.registerBaseNotificationPublisher();
        capabilityChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
                    InstanceIdentifier.builder(NetconfState.class).child(Capabilities.class).build()), this);
    }

    @Deactivate
    @Override
    public void close() {
        if (baseNotificationPublisherRegistration != null) {
            baseNotificationPublisherRegistration.close();
        }
        if (capabilityChangeListenerRegistration != null) {
            capabilityChangeListenerRegistration.close();
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Capabilities>> changes) {
        for (DataTreeModification<Capabilities> change : changes) {
            final DataObjectModification<Capabilities> rootNode = change.getRootNode();
            final DataObjectModification.ModificationType modificationType = rootNode.getModificationType();
            switch (modificationType) {
                case WRITE: {
                    final Capabilities dataAfter = rootNode.getDataAfter();
                    final Capabilities dataBefore = rootNode.getDataBefore();
                    final Set<Uri> before = dataBefore != null ? ImmutableSet.copyOf(dataBefore.getCapability())
                        : Set.of();
                    final Set<Uri> after = dataAfter != null ? ImmutableSet.copyOf(dataAfter.getCapability())
                        : Set.of();
                    final Set<Uri> added = Sets.difference(after, before);
                    final Set<Uri> removed = Sets.difference(before, after);
                    publishNotification(added, removed);
                    break;
                }
                case DELETE: {
                    final Capabilities dataBeforeDelete = rootNode.getDataBefore();
                    if (dataBeforeDelete != null) {
                        final Set<Uri> removed = ImmutableSet.copyOf(dataBeforeDelete.getCapability());
                        publishNotification(Set.of(), removed);
                    }
                    break;
                }
                default:
                    LOG.debug("Received intentionally unhandled type: {}.", modificationType);
            }
        }
    }

    private void publishNotification(final Set<Uri> added, final Set<Uri> removed) {
        baseNotificationPublisherRegistration.onCapabilityChanged(new NetconfCapabilityChangeBuilder()
            .setChangedBy(new ChangedByBuilder()
                .setServerOrUser(new ServerBuilder().setServer(Empty.value()).build())
                .build())
            .setAddedCapability(Set.copyOf(added))
            .setDeletedCapability(Set.copyOf(removed))
            // TODO modified should be computed ... but why ?
            .setModifiedCapability(Set.of())
            .build());
    }
}
