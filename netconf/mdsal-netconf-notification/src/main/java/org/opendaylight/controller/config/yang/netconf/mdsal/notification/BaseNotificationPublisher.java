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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Abstract base class for subclasses, which want to listen for changes in {@link NetconfState} subtree.
 * @param <T> subtree of {@link NetconfState}
 */
abstract class BaseNotificationPublisher<T extends ChildOf<NetconfState>> implements DataChangeListener, AutoCloseable {

    private final Class<T> type;

    /**
     * @param type subtree of {@link NetconfState}
     */
    BaseNotificationPublisher(Class<T> type) {
        this.type = type;
    }

    /**
     * Registers this as OPERATIONAL datastore listener via provided dataBroker with scope SUBTREE
     * @param dataBroker data broker
     * @return listener registration
     */
    ListenerRegistration registerOnChanges(DataBroker dataBroker) {
        return dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, getInstanceIdentifier(), this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    /**
     * @return instance identifier of subtree, on which should this instance listen
     */
    InstanceIdentifier<T> getInstanceIdentifier() {
        return InstanceIdentifier.create(NetconfState.class).child(type);
    }
}
