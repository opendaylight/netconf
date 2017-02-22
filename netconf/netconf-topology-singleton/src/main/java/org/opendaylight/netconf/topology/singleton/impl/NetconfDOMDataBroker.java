/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorSystem;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMReadTransaction;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMWriteTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfReadOnlyTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfWriteOnlyTransaction;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class NetconfDOMDataBroker implements DOMDataBroker {

    private final RemoteDeviceId id;
    private final NetconfDOMReadTransaction masterReadTx;
    private final NetconfDOMWriteTransaction masterWriteTx;
    private final ActorSystem actorSystem;

    public NetconfDOMDataBroker(final ActorSystem actorSystem, final RemoteDeviceId id,
                                final NetconfDOMReadTransaction masterReadTx,
                                final NetconfDOMWriteTransaction masterWriteTx) {
        this.id = id;
        this.masterReadTx = masterReadTx;
        this.masterWriteTx = masterWriteTx;
        this.actorSystem = actorSystem;
    }

    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        return new NetconfReadOnlyTransaction(id, actorSystem, masterReadTx);
    }

    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(new NetconfReadOnlyTransaction(id, actorSystem, masterReadTx),
                new NetconfWriteOnlyTransaction(id, actorSystem, masterWriteTx));
    }

    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        return new NetconfWriteOnlyTransaction(id, actorSystem, masterWriteTx);
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(
            final LogicalDatastoreType store, final YangInstanceIdentifier path, final DOMDataChangeListener listener,
            final DataChangeScope triggeringScope) {
        throw new UnsupportedOperationException(id + ": Data change listeners not supported for netconf mount point");
    }

    @Override
    public DOMTransactionChain createTransactionChain(final TransactionChainListener listener) {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }

    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return Collections.emptyMap();
    }
}
