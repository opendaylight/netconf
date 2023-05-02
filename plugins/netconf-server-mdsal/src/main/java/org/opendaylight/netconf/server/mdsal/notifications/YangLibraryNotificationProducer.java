/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.server.api.notifications.YangLibraryPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChangeBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Listens on the set of modules and submodules changes in data store and publishes to base NETCONF notification stream
 * listener a server-specific identifier representing the current set of modules and submodules.
 *
 * @deprecated ietf-yang-library:yang-library-change was deprecated in the new RFC8525.
 *             Use {@link YangLibraryNotificationProducerRFC8525}.
 */
@Component(service = { })
@Deprecated(forRemoval = true)
public final class YangLibraryNotificationProducer implements DataTreeChangeListener<ModulesState>, AutoCloseable {
    private final ListenerRegistration<?> yangLibraryChangeListenerRegistration;
    private final YangLibraryPublisherRegistration yangLibraryPublisherRegistration;

    @Activate
    public YangLibraryNotificationProducer(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationCollector notifManager,
            @Reference final DataBroker dataBroker) {
        yangLibraryPublisherRegistration = notifManager.registerYangLibraryPublisher();
        yangLibraryChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ModulesState.class)),
            this);
    }

    @Deactivate
    @Override
    public void close() {
        if (yangLibraryPublisherRegistration != null) {
            yangLibraryPublisherRegistration.close();
        }
        if (yangLibraryChangeListenerRegistration != null) {
            yangLibraryChangeListenerRegistration.close();
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<ModulesState>> changes) {
        for (DataTreeModification<ModulesState> change : changes) {
            final ModulesState dataAfter = change.getRootNode().getDataAfter();
            if (dataAfter != null) {
                yangLibraryPublisherRegistration.onYangLibraryChange(new YangLibraryChangeBuilder()
                    .setModuleSetId(dataAfter.getModuleSetId())
                    .build());
            }
        }
    }
}
