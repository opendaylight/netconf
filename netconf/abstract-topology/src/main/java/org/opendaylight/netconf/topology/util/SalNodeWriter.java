/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SalNodeWriter implements NodeWriter {

    private static final Logger LOG = LoggerFactory.getLogger(SalNodeWriter.class);

    private final String topologyId;
    private final BindingTransactionChain transactionChain;

    public SalNodeWriter(final DataBroker dataBroker, final String topologyId) {
        this.topologyId = topologyId;
        this.transactionChain = Preconditions.checkNotNull(dataBroker).createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(TransactionChain<?, ?> transactionChain, AsyncTransaction<?, ?> transaction, Throwable cause) {
                LOG.error("{}: TransactionChain({}) {} FAILED!", transactionChain,
                        transaction.getIdentifier(), cause);
                throw new IllegalStateException("Abstract topology writer TransactionChain(" + transactionChain + ") not committed correctly", cause);
            }

            @Override
            public void onTransactionChainSuccessful(TransactionChain<?, ?> transactionChain) {
                LOG.trace("Abstract topology writer TransactionChain({}) SUCCESSFUL", transactionChain);
            }
        });
    }

    @Override public void init(@Nonnull final NodeId id, @Nonnull final Node operationalDataNode) {
        // put into Datastore
        final WriteTransaction wTx = transactionChain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, TopologyUtil.createTopologyNodeListPath(new NodeKey(id), topologyId), operationalDataNode);
        commitTransaction(wTx, id, "init");
    }

    @Override public void update(@Nonnull final NodeId id, @Nonnull final Node operationalDataNode) {
        // merge
        final WriteTransaction wTx = transactionChain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, TopologyUtil.createTopologyNodeListPath(new NodeKey(id), topologyId), operationalDataNode);
        commitTransaction(wTx, id, "update");
    }

    @Override public void delete(@Nonnull final NodeId id) {
        // delete
        final WriteTransaction wTx = transactionChain.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, TopologyUtil.createTopologyNodeListPath(new NodeKey(id), topologyId));
        commitTransaction(wTx, id, "delete");
    }

    // FIXME duplicated code. Also present in:
    // netconf/netconf/netconf-topology/src/main/java/org/opendaylight/netconf/topology/impl/TopologyNodeWriter.java
    // netconf/netconf/sal-netconf-connector/src/main/java/org/opendaylight/netconf/sal/connect/netconf/sal/NetconfDeviceTopologyAdapter.java
    private void commitTransaction(final WriteTransaction transaction, final NodeId id, final String txType) {
        LOG.debug("{}: Committing Transaction {}:{}", id.getValue(), txType,
                transaction.getIdentifier());

        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("{}: Transaction({}) {} SUCCESSFUL", id.getValue(), txType,
                        transaction.getIdentifier());
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("{}: Transaction({}) {} FAILED!", id.getValue(), txType,
                        transaction.getIdentifier(), t);
                throw new IllegalStateException(id + "  Transaction(" + txType + ") not committed correctly", t);
            }
        });
    }
}
