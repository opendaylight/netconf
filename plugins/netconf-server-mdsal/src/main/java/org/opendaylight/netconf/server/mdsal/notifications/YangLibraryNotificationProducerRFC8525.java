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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdateBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Listens on the modules, submodules, datastores, and datastore schemas changes in data store and publishes to base
 * NETCONF notification stream listener a server-specific identifier representing the current set of modules,
 * submodules, datastores, and datastore schemas.
 */
@Component(service = { })
public final class YangLibraryNotificationProducerRFC8525
        implements DataTreeChangeListener<YangLibrary>, AutoCloseable {
    private final ListenerRegistration<?> yangLibraryChangeListenerRegistration;
    private final YangLibraryPublisherRegistration yangLibraryPublisherRegistration;

    @Activate
    public YangLibraryNotificationProducerRFC8525(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationCollector notifManager,
            @Reference final DataBroker dataBroker) {
        yangLibraryPublisherRegistration = notifManager.registerYangLibraryPublisher();
        yangLibraryChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(YangLibrary.class)),
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
    public void onDataTreeChanged(final Collection<DataTreeModification<YangLibrary>> changes) {
        for (DataTreeModification<YangLibrary> change : changes) {
            final YangLibrary dataAfter = change.getRootNode().getDataAfter();
            if (dataAfter != null) {
                yangLibraryPublisherRegistration.onYangLibraryUpdate(new YangLibraryUpdateBuilder()
                    .setContentId(dataAfter.getContentId())
                    .build());
            }
        }
    }
}
