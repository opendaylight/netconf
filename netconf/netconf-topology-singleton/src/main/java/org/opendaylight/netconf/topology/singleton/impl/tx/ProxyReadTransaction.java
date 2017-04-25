/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * ProxyReadTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActor}.
 */
public class ProxyReadTransaction implements DOMDataReadOnlyTransaction {

    private final ProxyReadAdapter delegate;

    /**
     * Constructor for {@code ProxyReadTransaction}.
     *
     * @param masterTxActor {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActor} ref
     * @param id            device id
     * @param actorSystem   system
     * @param askTimeout    timeout
     */
    public ProxyReadTransaction(final ActorRef masterTxActor, final RemoteDeviceId id, final ActorSystem actorSystem,
                                final Timeout askTimeout) {
        delegate = new ProxyReadAdapter(masterTxActor, id, actorSystem, askTimeout);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType store,
                                                                                   final YangInstanceIdentifier path) {
        return delegate.read(store, path);
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
                                                              final YangInstanceIdentifier path) {
        return delegate.exists(store, path);
    }


    @Override
    public Object getIdentifier() {
        return this;
    }
}
