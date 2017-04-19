/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Abstract base class for subclasses, which want to listen for changes on specified subtree in operational datastore.
 *
 * @param <T> data object class
 */
abstract class OperationalDatastoreListener<T extends DataObject> implements DataTreeChangeListener<T> {

    private final InstanceIdentifier<T> instanceIdentifier;

    /**
     * Constructor.
     *
     * @param instanceIdentifier instance identifier of subtree, on which this instance should listen on changes.
     */
    OperationalDatastoreListener(InstanceIdentifier<T> instanceIdentifier) {
        this.instanceIdentifier = instanceIdentifier;
    }

    /**
     * Registers this instance as OPERATIONAL datastore listener via provided dataBroker.
     *
     * @param dataBroker data broker
     * @return listener registration
     */
    ListenerRegistration<OperationalDatastoreListener<T>> registerOnChanges(DataBroker dataBroker) {
        DataTreeIdentifier<T> id = new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, instanceIdentifier);
        return dataBroker.registerDataTreeChangeListener(id, this);
    }

}
