/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Abstract base class for subclasses, which want to listen for changes in datastore.
 * @param <T> data object class
 */
abstract class BaseNotificationPublisher<T extends DataObject> implements DataChangeListener, AutoCloseable {

    private final InstanceIdentifier<T> instanceIdentifier;
    private final AsyncDataBroker.DataChangeScope scope;

    /**
     * @param instanceIdentifier instance identifier
     * @param scope change scope
     */
    BaseNotificationPublisher(InstanceIdentifier<T> instanceIdentifier, AsyncDataBroker.DataChangeScope scope) {
        this.instanceIdentifier = instanceIdentifier;
        this.scope = scope;
    }

    /**
     * Registers this as OPERATIONAL datastore listener via provided dataBroker with scope SUBTREE
     * @param dataBroker data broker
     * @return listener registration
     */
    ListenerRegistration registerOnChanges(DataBroker dataBroker) {
        return dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, instanceIdentifier, this, scope);
    }

}
