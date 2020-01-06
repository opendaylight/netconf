/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.mdsal.dom.spi.PingPongMergingDOMDataBroker;
import org.opendaylight.netconf.api.tx.NetconfXPathDOMTransactionChain;
import org.opendaylight.netconf.api.tx.NetconfXPathDataBrokerExtension;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadTransaction;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadWriteTransaction;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadOnlyTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.TxChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.XPathAwareReadOnlyTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.XPathAwareReadWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.XPathAwareTxChain;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class NetconfDeviceDataBroker implements PingPongMergingDOMDataBroker {

    private final RemoteDeviceId id;
    private final NetconfBaseOps netconfOps;
    private final boolean rollbackSupport;
    private final boolean candidateSupported;
    private final boolean runningWritable;
    private final ImmutableClassToInstanceMap<DOMDataBrokerExtension> netconfDataBrokerExtensions;

    private boolean isLockAllowed = true;

    public NetconfDeviceDataBroker(final RemoteDeviceId id, final SchemaContext schemaContext,
                                   final DOMRpcService rpc, final NetconfSessionPreferences netconfSessionPreferences) {
        this.id = id;
        this.netconfOps = new NetconfBaseOps(rpc, schemaContext);
        // get specific attributes from netconf preferences and get rid of it
        // no need to keep the entire preferences object, its quite big with all the capability QNames
        candidateSupported = netconfSessionPreferences.isCandidateSupported();
        runningWritable = netconfSessionPreferences.isRunningWritable();
        rollbackSupport = netconfSessionPreferences.isRollbackSupported();
        Preconditions.checkArgument(candidateSupported || runningWritable,
            "Device %s has advertised neither :writable-running nor :candidate capability."
                    + "At least one of these should be advertised. Failed to establish a session.", id.getName());
        netconfDataBrokerExtensions = prepareExtensions(netconfSessionPreferences.isXPathSupported());
    }

    private ImmutableClassToInstanceMap<DOMDataBrokerExtension> prepareExtensions(final boolean isXPathSupported) {
        if (isXPathSupported) {
            return ImmutableClassToInstanceMap.of(NetconfXPathDataBrokerExtension.class,
                    new NetconfXPathDataBrokerExtImpl());
        } else {
            return ImmutableClassToInstanceMap.of();
        }
    }

    @Override
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        return new ReadOnlyTx(netconfOps, id);
    }

    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx(newReadOnlyTransaction(), newWriteOnlyTransaction());
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        if (candidateSupported) {
            if (runningWritable) {
                return new WriteCandidateRunningTx(id, netconfOps, rollbackSupport, isLockAllowed);
            } else {
                return new WriteCandidateTx(id, netconfOps, rollbackSupport, isLockAllowed);
            }
        } else {
            return new WriteRunningTx(id, netconfOps, rollbackSupport, isLockAllowed);
        }
    }

    @Override
    public DOMTransactionChain createTransactionChain(final DOMTransactionChainListener listener) {
        return new TxChain(this, listener);
    }

    @Override
    public ClassToInstanceMap<DOMDataBrokerExtension> getExtensions() {
        return netconfDataBrokerExtensions;
    }

    void setLockAllowed(boolean isLockAllowedOrig) {
        this.isLockAllowed = isLockAllowedOrig;
    }

    /**
     * Implementation of XPath-supporting extension operations to {@link DOMDataBroker} service - reading of data
     * from NETCONF device data-store using filters constructed from XPath expressions.
     */
    private final class NetconfXPathDataBrokerExtImpl implements NetconfXPathDataBrokerExtension {

        @Override
        public XPathAwareDOMReadTransaction newXPathReadOnlyTransaction() {
            return new XPathAwareReadOnlyTx(netconfOps, id);
        }

        @Override
        public XPathAwareDOMReadWriteTransaction newXPathReadWriteTransaction() {
            return new XPathAwareReadWriteTx(newXPathReadOnlyTransaction(), newWriteOnlyTransaction());
        }

        @Override
        public NetconfXPathDOMTransactionChain createXpathTransactionChain(
                final DOMTransactionChainListener listener) {
            return new XPathAwareTxChain(NetconfDeviceDataBroker.this, this, listener);
        }
    }
}
