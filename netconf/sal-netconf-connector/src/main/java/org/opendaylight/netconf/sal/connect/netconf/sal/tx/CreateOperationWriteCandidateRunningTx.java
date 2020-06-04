/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOperationWriteCandidateRunningTx extends WriteCandidateRunningTx {
    private static final Logger LOG = LoggerFactory.getLogger(CreateOperationWriteCandidateRunningTx.class);

    public CreateOperationWriteCandidateRunningTx(RemoteDeviceId id, NetconfBaseOps netOps, boolean rollbackSupport) {
        super(id, netOps, rollbackSupport);
    }

    public CreateOperationWriteCandidateRunningTx(RemoteDeviceId id, NetconfBaseOps netconfOps,
                                                  boolean rollbackSupport,boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    public synchronized void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                 final NormalizedNode<?, ?> data) {
        checkEditable(store);

        // Trying to write only mixin nodes (not visible when serialized).
        // Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring put for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.CREATE), path);
        editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), "put");
    }
}
