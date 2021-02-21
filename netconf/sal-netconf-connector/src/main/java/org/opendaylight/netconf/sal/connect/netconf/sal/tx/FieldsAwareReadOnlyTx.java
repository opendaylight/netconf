/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FluentFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldsAwareReadOnlyTx extends AbstractReadOnlyTx implements NetconfDOMFieldsReadTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(FieldsAwareReadOnlyTx.class);

    public FieldsAwareReadOnlyTx(final NetconfBaseOps netconfOps, final RemoteDeviceId id) {
        super(netconfOps, id);
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        switch (store) {
            case CONFIGURATION:
                return readConfigurationData(path, fields);
            case OPERATIONAL:
                return readOperationalData(path, fields);
            default:
                LOG.warn("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                        "%s, Cannot read data %s with fields %s for %s datastore, unknown datastore type",
                        id, path, fields, store));
        }
    }

    private @NonNull FluentFuture<Optional<NormalizedNode<?, ?>>> readConfigurationData(
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return remapException(netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path), fields));
    }

    private @NonNull FluentFuture<Optional<NormalizedNode<?, ?>>> readOperationalData(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        return remapException(netconfOps.getData(
                new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path), fields));
    }
}