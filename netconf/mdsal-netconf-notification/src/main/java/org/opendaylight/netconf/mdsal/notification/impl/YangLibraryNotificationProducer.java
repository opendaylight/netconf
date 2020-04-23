/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.notification.impl;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.YangLibraryPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.YangLibraryChangeBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listens on the set of modules and submodules changes in data store and publishes
 * to base netconf notification stream listener a server-specific identifier representing
 * the current set of modules and submodules.
 */
public final class YangLibraryNotificationProducer extends OperationalDatastoreListener<ModulesState>
    implements AutoCloseable {

    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(ModulesState.class);

    private final ListenerRegistration<?> yangLibraryChangeListenerRegistration;
    private final YangLibraryPublisherRegistration yangLibraryPublisherRegistration;

    public YangLibraryNotificationProducer(final NetconfNotificationCollector netconfNotificationCollector,
                                           final DataBroker dataBroker) {
        super(MODULES_STATE_INSTANCE_IDENTIFIER);
        this.yangLibraryPublisherRegistration = netconfNotificationCollector.registerYangLibraryPublisher();
        this.yangLibraryChangeListenerRegistration = registerOnChanges(dataBroker);
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<ModulesState>> changes) {
        for (DataTreeModification<ModulesState> change : changes) {
            final DataObjectModification<ModulesState> rootNode = change.getRootNode();
            final ModulesState dataAfter = rootNode.getDataAfter();
            if (dataAfter != null) {
                final YangLibraryChange yangLibraryChange = new YangLibraryChangeBuilder()
                        .setModuleSetId(dataAfter.getModuleSetId())
                        .build();
                yangLibraryPublisherRegistration.onYangLibraryChange(yangLibraryChange);
            }
        }
    }

    /**
     * Invoked by blueprint.
     */
    @Override
    public void close() {
        if (yangLibraryPublisherRegistration != null) {
            yangLibraryPublisherRegistration.close();
        }
        if (yangLibraryChangeListenerRegistration != null) {
            yangLibraryChangeListenerRegistration.close();
        }
    }
}
