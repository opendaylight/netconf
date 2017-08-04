/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.ChangedByBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.changed.by.server.or.user.ServerBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listens on capabilities changes in data store and publishes them to base
 * netconf notification stream listener.
 */
public final class CapabilityChangeNotificationProducer extends OperationalDatastoreListener<Capabilities> {

    private static final InstanceIdentifier<Capabilities> CAPABILITIES_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
    private static final Logger LOG = LoggerFactory.getLogger(CapabilityChangeNotificationProducer.class);

    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    private final ListenerRegistration capabilityChangeListenerRegistration;

    public CapabilityChangeNotificationProducer(final NetconfNotificationCollector netconfNotificationCollector,
                                                final DataBroker dataBroker) {
        super(CAPABILITIES_INSTANCE_IDENTIFIER);
        this.baseNotificationPublisherRegistration = netconfNotificationCollector.registerBaseNotificationPublisher();
        this.capabilityChangeListenerRegistration = registerOnChanges(dataBroker);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Capabilities>> changes) {
        for (DataTreeModification<Capabilities> change : changes) {
            final DataObjectModification<Capabilities> rootNode = change.getRootNode();
            final DataObjectModification.ModificationType modificationType = rootNode.getModificationType();
            switch (modificationType) {
                case WRITE: {
                    final Capabilities dataAfter = rootNode.getDataAfter();
                    final Capabilities dataBefore = rootNode.getDataBefore();
                    final Set<Uri> before = dataBefore != null ? ImmutableSet.copyOf(dataBefore.getCapability()) :
                            Collections.emptySet();
                    final Set<Uri> after = dataAfter != null ? ImmutableSet.copyOf(dataAfter.getCapability()) :
                            Collections.emptySet();
                    final Set<Uri> added = Sets.difference(after, before);
                    final Set<Uri> removed = Sets.difference(before, after);
                    publishNotification(added, removed);
                    break;
                }
                case DELETE: {
                    final Capabilities dataBeforeDelete = rootNode.getDataBefore();
                    if (dataBeforeDelete != null) {
                        final Set<Uri> removed = ImmutableSet.copyOf(dataBeforeDelete.getCapability());
                        publishNotification(Collections.emptySet(), removed);
                    }
                    break;
                }
                default:
                    LOG.debug("Received intentionally unhandled type: {}.", modificationType);
            }
        }

    }

    private void publishNotification(Set<Uri> added, Set<Uri> removed) {
        final NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();
        netconfCapabilityChangeBuilder.setChangedBy(new ChangedByBuilder().setServerOrUser(new ServerBuilder()
                .setServer(true).build()).build());
        netconfCapabilityChangeBuilder.setAddedCapability(ImmutableList.copyOf(added));
        netconfCapabilityChangeBuilder.setDeletedCapability(ImmutableList.copyOf(removed));
        // TODO modified should be computed ... but why ?
        netconfCapabilityChangeBuilder.setModifiedCapability(Collections.emptyList());
        baseNotificationPublisherRegistration.onCapabilityChanged(netconfCapabilityChangeBuilder.build());
    }

    /**
     * Invoked by blueprint.
     */
    public void close() {
        if (baseNotificationPublisherRegistration != null) {
            baseNotificationPublisherRegistration.close();
        }
        if (capabilityChangeListenerRegistration != null) {
            capabilityChangeListenerRegistration.close();
        }
    }
}
