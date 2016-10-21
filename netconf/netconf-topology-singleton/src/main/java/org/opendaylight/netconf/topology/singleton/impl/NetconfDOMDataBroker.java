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
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfReadOnlyTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfWriteOnlyTransaction;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class NetconfDOMDataBroker implements DOMDataBroker {

    private final RemoteDeviceId id;
    private final NetconfDOMTransaction masterDataBroker;
    private final ActorSystem actorSystem;

    public NetconfDOMDataBroker(final ActorSystem actorSystem, final RemoteDeviceId id,
                         final NetconfDOMTransaction masterDataBroker) {
        this.id = id;
        this.masterDataBroker = masterDataBroker;
        this.actorSystem = actorSystem;
    }

    @Override
    public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
        return new NetconfReadOnlyTransaction(actorSystem, masterDataBroker);
    }

    @Override
    public DOMDataReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(new NetconfReadOnlyTransaction(actorSystem, masterDataBroker),
                new NetconfWriteOnlyTransaction(actorSystem, masterDataBroker));
    }

    @Override
    public DOMDataWriteTransaction newWriteOnlyTransaction() {
        return new NetconfWriteOnlyTransaction(actorSystem, masterDataBroker);
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(
            LogicalDatastoreType store, YangInstanceIdentifier path, DOMDataChangeListener listener,
            DataChangeScope triggeringScope) {
        throw new UnsupportedOperationException(id + ": Data change listeners not supported for netconf mount point");
    }

    @Override
    public DOMTransactionChain createTransactionChain(TransactionChainListener listener) {
        throw new UnsupportedOperationException(id + ": Transaction chains not supported for netconf mount point");
    }

    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return Collections.emptyMap();
    }
}
