/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.tx.XPathAwareDOMReadTransaction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only transaction that allows reading of data from DS using both {@link YangInstanceIdentifier}
 * and {@link YangExpr} that represents XPath expression.
 */
public final class XPathAwareReadOnlyTx extends ReadOnlyTx implements XPathAwareDOMReadTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(XPathAwareReadOnlyTx.class);

    public XPathAwareReadOnlyTx(final NetconfBaseOps netconfOps, final RemoteDeviceId id) {
        super(netconfOps, id);
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store, final YangExpr xpath) {
        switch (store) {
            case CONFIGURATION:
                return readConfigurationDataUsingXPath(xpath);
            case OPERATIONAL:
                return readOperationalDataUsingXPath(xpath);
            default:
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                        "%s, Cannot read data %s for %s datastore, unknown datastore type", id, xpath, store));
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangExpr xpath) {
        return read(store, xpath).transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                MoreExecutors.directExecutor());
    }

    private FluentFuture<Optional<NormalizedNode<?, ?>>> readConfigurationDataUsingXPath(final YangExpr xpath) {
        return remapException(netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("XPath data read", id), xpath));
    }

    private FluentFuture<Optional<NormalizedNode<?, ?>>> readOperationalDataUsingXPath(final YangExpr xpath) {
        return remapException(netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("XPath data read", id), xpath));
    }
}